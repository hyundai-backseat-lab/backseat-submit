package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameMessage
import io.backseatlab.playground.game.api.GameNext
import io.backseatlab.playground.game.api.GamePlan
import io.backseatlab.playground.game.api.GameSession
import io.backseatlab.playground.game.api.NoAnswerReason
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.Driving
import io.backseatlab.playground.pleos.ListenResult
import io.backseatlab.playground.pleos.Parking
import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.SpeakResult
import io.backseatlab.playground.pleos.driving
import io.backseatlab.playground.pleos.parking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 이용 하나의 흐름(시작·안내·응답·멈춤·휴식·종료)을 한곳에서 처리한다(설계서 2절·6절).
 * 모든 함수는 [scope]의 한 스레드에서 불린다고 가정한다(앱: Main). 게임은 [newGame]으로만 만든다.
 *
 * 진행 위치([Position])를 저장해 두고, 진행 작업(runner)이 그 위치부터 실행한다. 멈춤·휴식은 runner만 취소하므로
 * 재개하면 끊긴 문장부터, 듣던 중이면 듣기부터 다시 한다. 게임 계산(step)은 runner 밖에서 돌아 휴식 중에도 결과를 보관한다.
 * 앱이 말하는 동안·멈춤·휴식 중에는 제어 요청만 듣는다(ear).
 */
class SessionController(
    private val pleos: PleosClient,
    private val newGame: () -> GameSession,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val log: (String) -> Unit,
    private val cards: VoiceCardStore = VoiceCardStore { null },
) {
    val carStatus: StateFlow<CarStatus> =
        pleos.vehicle
            .map(CarStatus::from)
            .stateIn(scope, SharingStarted.Eagerly, CarStatus.from(pleos.vehicle.value))

    /** 주차 확인. 확인 불가는 주차가 아니다(F-03-06). */
    val parked: StateFlow<Boolean> =
        pleos.vehicle
            .map { it.parking() == Parking.PARKED }
            .stateIn(scope, SharingStarted.Eagerly, pleos.vehicle.value.parking() == Parking.PARKED)

    private val mutableStatus = MutableStateFlow(SessionStatus())
    val status: StateFlow<SessionStatus> = mutableStatus

    private val turns = TurnListener(pleos, clock, log)
    private val pauses = mutableSetOf<PauseReason>()
    private var sessionId = 0
    private var attempt = 0
    private var game: GameSession? = null
    private var position: Position? = null
    private var resting = false
    private var quietTurn = false
    private var pendingStep: Job? = null
    private var arrived = false
    private var arrivalCardPlayed = false
    private var screenVisible = false
    private var lastDriving = Driving.DRIVING
    private val reactions = CarReactions(clock)
    private var cardJob: Job? = null

    /** 도착 뒤 마무리로 넘어가기 전까지 runner가 더 진행하지 않게 막는다. */
    private var holdForArrival = false
    private var speakingNow = false
    private var runner: Job? = null
    private var ear: Job? = null
    private var announcer: Job? = null
    private var restTimer: Job? = null
    private var resumeTimer: Job? = null
    private val echo = EchoFilter(clock)

    init {
        pleos.vehicle
            .map { it.parking() }
            .distinctUntilChanged()
            .onEach { log("PARK state=$it") }
            .launchIn(scope)
        SafetyRules
            .watch(pleos)
            .onEach { (driving, signals) -> applySafety(driving, signals) }
            .launchIn(scope)
        pleos.arrivals.onEach { onArrived() }.launchIn(scope)
        combine(pleos.vehicle, pleos.vehicle.driving()) { state, driving -> driving to state }
            .onEach { (driving, state) ->
                lastDriving = driving
                reactions.onVehicle(driving, state)?.let { react(it) }
            }.launchIn(scope)
    }

    /** 결정표대로 멈춤 사유를 켜고 끈다. 경고는 추가 발화 없이 무음 종료(F-03-05). */
    private fun applySafety(
        driving: Driving,
        signals: Set<SafetySignal>,
    ) {
        // 경고를 먼저 본다: 멈춤 처리(확인 질문 중 멈춤 = 종료)가 종료 사유를 USER로 덮지 않게
        if (SafetySignal.WARNING in signals && game != null) {
            log("SAFETY state=$driving signal=${SafetySignal.WARNING} action=END")
            return end(EndReason.WARNING)
        }
        for (signal in SafetySignal.entries) {
            val action = if (signal in signals) SafetyRules.decide(driving, signal) else SafetyAction.CONTINUE
            val reason = signal.pauseReason
            when {
                reason == null -> Unit
                (action == SafetyAction.PAUSE) != (reason in pauses) -> {
                    val shown = if (action == SafetyAction.PAUSE) "PAUSE" else "RESUME"
                    if (signal == SafetySignal.OTHER_VOICE) {
                        log("SAFETY reason=FOCUS_LOSS action=$shown")
                    } else {
                        log("SAFETY state=$driving signal=$signal action=$shown")
                    }
                    setPause(reason, action == SafetyAction.PAUSE)
                }
            }
        }
    }

    /** 지금 시작할 수 없는 이유. null이면 시작할 수 있다. 이미 시작한 이용은 주행 중에도 계속되므로 시작에만 쓴다. */
    fun startBlock(micGranted: Boolean): StartBlock? =
        when {
            !parked.value -> StartBlock.NOT_PARKED
            (pleos.vehicle.value.warning as? Reading.Value)?.value == true -> StartBlock.WARNING
            !micGranted -> StartBlock.NO_MIC
            !pleos.voiceReady() -> StartBlock.VOICE_NOT_READY
            else -> null
        }

    /** 화면의 시작 요청. 조건이 맞으면 시작하고, 아니면 이유를 돌려준다. */
    fun requestStart(micGranted: Boolean): StartBlock? {
        val block = startBlock(micGranted)
        if (block == null) start() else log("SESSION start blocked=$block")
        return block
    }

    /** 실제 화면 이탈(다른 앱·홈으로 이동)은 무음 종료(F-04-06). */
    fun onScreenLeft() = end(EndReason.LEFT)

    /** 이용을 시작한다. 이미 진행 중이면 새 이용을 만들지 않는다. */
    fun start() {
        if (game != null) return
        game = newGame()
        sessionId++
        attempt = 0
        resting = false
        log("SESSION start id=$sessionId")
        pleos.holdAudioFocus()
        position = Position.Saying(SessionLines.intro.map { GameMessage(it) }, 0, Next.Step(GameInput.Start))
        advance()
    }

    /** 멈춤 사유를 켜고 끈다. 켜지면 바로 조용해지고, 모두 꺼지면 1초 뒤 이어간다. */
    fun setPause(
        reason: PauseReason,
        active: Boolean,
    ) {
        val changed = if (active) pauses.add(reason) else pauses.remove(reason)
        // 카드 재생 중 멈춤 신호가 오면 이용 전이라도 바로 멈춘다(F-08-02)
        if (changed && active && cardJob?.isActive == true) {
            cardJob?.cancel()
            pleos.stopSpeaking()
        }
        if (!changed || game == null) return
        if (active) {
            // 확인 질문 중에 멈추면 확인 없이 종료한다(멈춤 중 종료 요청 = 즉시 종료)
            if (position is Position.ConfirmQuit) return end(EndReason.USER)
            resumeTimer?.cancel()
            announcer?.cancel()
            pleos.stopSpeaking()
            runner?.cancel()
            listenForCommandsOnly()
        } else if (pauses.isEmpty()) {
            resumeTimer =
                scope.launch {
                    delay(RESUME_DELAY_MS)
                    // 듣던 중이었으면 신호 없이 듣기만 다시
                    quietTurn = position is Position.Waiting
                    if (!resting) log("TTS resumed")
                    advance()
                }
        }
        publish()
    }

    /** 무음 종료(그만하기 확인 후·경고·장애·화면 이탈·휴식 초과). 종료 음성은 없다. */
    fun end(reason: EndReason) {
        val ending = game ?: return
        // 결과(SC-07)는 close 전에 읽는다. 종료 이유와 상관없이 종료 후 화면에 보여 준다(공통 정책 "종료는 두 종류")
        val played = ending.status()
        game = null
        position = null
        log("SESSION end reason=$reason")
        listOf(runner, ear, announcer, restTimer, resumeTimer, cardJob).forEach { it?.cancel() }
        pleos.stopSpeaking()
        pleos.stopListening()
        pleos.releaseAudioFocus()
        val running = pendingStep
        // 실행 중인 step이 끝난 뒤 close. 논리적 종료는 기다리지 않는다
        scope.launch {
            running?.join()
            ending.close()
        }
        arrived = false
        arrivalCardPlayed = false
        holdForArrival = false
        mutableStatus.value =
            SessionStatus(Phase.ENDED, pauses.toSet(), reason, SessionResult(played.found, played.played, played.hints))
    }

    /** 지금 상태에 맞게 진행하거나(runner), 제어 요청만 듣는다(ear). */
    private fun advance() {
        runner?.cancel()
        if (game == null) return
        val confirming = position is Position.ConfirmQuit
        if (pauses.isNotEmpty() || (resting && !confirming)) {
            listenForCommandsOnly()
        } else {
            ear?.cancel()
            runner = scope.launch { run() }
        }
        publish()
    }

    private suspend fun run() {
        while (true) {
            // 휴식·확인 질문으로 바뀌면 runner가 스스로를 취소한다. 다음 일을 시작하기 전에 확인한다
            currentCoroutineContext().ensureActive()
            // 도착하면 지금 문장까지만 말하고 더 진행하지 않는다(마무리는 onArrived가 이어받는다)
            if (holdForArrival) return
            when (val at = position ?: return) {
                is Position.Saying -> if (at.index < at.lines.size) say(at) else follow(at.then)
                is Position.Waiting -> takeAnswer(at)
                is Position.Thinking -> {
                    val plan = awaitStep(at.result)
                    val messages = if (arrivalCardPlayed) withoutArrivalCheer(plan.messages) else plan.messages
                    position = Position.Saying(messages, 0, nextOf(plan))
                }
                is Position.ConfirmQuit -> confirmQuit(at)
            }
        }
    }

    private suspend fun say(at: Position.Saying) {
        publish(Phase.GUIDING)
        val message = at.lines[at.index]
        val audio = message.cardId?.let(cards::find)
        val result = withEar { speakWatched(message.text, audio) }
        // 취소된 발화는 다음 단계로 넘어가지 않는다. 멈춤이면 runner가 취소돼 여기서 끝나고, 아니면 같은 문장을 다시 말한다
        currentCoroutineContext().ensureActive()
        if (result != SpeakResult.Cancelled) position = at.copy(index = at.index + 1)
    }

    private fun follow(then: Next) {
        when (then) {
            is Next.Turn -> position = Position.Waiting(then.turnId, then.prompt)
            is Next.Step -> position = Position.Thinking(step(then.input))
            is Next.Back -> position = then.position
            Next.Rest -> {
                // 휴식에서 돌아오면 게임이 다음 미션을 다시 안내한다(D-022)
                position = Position.Saying(emptyList(), 0, Next.Step(GameInput.Start))
                rest("NO_RESPONSE")
            }
            Next.Finish -> end(EndReason.ARRIVED)
        }
    }

    private suspend fun takeAnswer(at: Position.Waiting) {
        publish(Phase.LISTENING)
        attempt++
        val heard = turns.listen(attempt, cue = !quietTurn, waitMs = NO_ANSWER_MS)
        quietTurn = false
        val input =
            when (heard) {
                is Heard.Text -> {
                    val command = VoiceCommand.parse(heard.text)
                    if (command != null) return onCommand(command, heard.text)
                    GameInput.Answer(at.turnId, listOf(heard.text))
                }
                Heard.Unheard -> GameInput.NoAnswer(at.turnId, NoAnswerReason.UNHEARD)
                Heard.Silent -> GameInput.NoAnswer(at.turnId, NoAnswerReason.SILENT)
                is Heard.Broken -> return broken(heard.code)
            }
        // 응답을 받았으면 이 시도는 닫힌다. 이후 휴식·재개해도 같은 답을 다시 보내지 않는다
        position = Position.Thinking(step(input))
    }

    private suspend fun confirmQuit(at: Position.ConfirmQuit) {
        publish(Phase.LISTENING)
        if (speakWatched(SessionLines.CONFIRM_QUIT) == SpeakResult.Cancelled) return // run()이 다시 묻는다
        val heard = turns.listen(++attempt, cue = false, waitMs = CONFIRM_WAIT_MS)
        if (heard is Heard.Text && "아니" in heard.text) {
            position = at.back
            if (resting) advance()
        } else {
            // 아이가 직접 그만하겠다고 한 경우만 마무리 인사를 한다(D-036). 경고·휴식 초과 같은 종료는 무음 그대로
            speakWatched(SessionLines.farewell(game?.status()?.found ?: 0))
            end(EndReason.USER)
        }
    }

    /** 같은 게임에서 step을 동시에 부르지 않는다(설계서 3절). 게임 예외는 scope 밖으로 번지지 않게 결과 값으로 받는다. */
    private fun step(input: GameInput): Deferred<Result<GamePlan>> {
        val playing = game ?: throw CancellationException("ended")
        val before = pendingStep
        return scope
            .async {
                before?.join()
                runCatching { playing.step(input) }
            }.also { pendingStep = it }
    }

    /**
     * 목적지 도착(F-05). 멈춤 중이면 말없이 종료하고, 아니면 하던 말을 최대 3초 안에 마무리한 뒤
     * 게임에 Arrived를 보내 마무리 문장을 말하고 끝낸다(휴식 중이어도 마무리한다).
     */
    private fun onArrived() {
        when {
            // 이용 전·후에는 도착 카드만(화면이 보이고 주차 브레이크와 함께일 때)
            game == null -> reactions.onArrived(pleos.vehicle.value)?.let { react(it) }
            arrived -> Unit
            pauses.isNotEmpty() -> {
                log("SESSION arrived paused=true")
                end(EndReason.ARRIVED)
            }
            else -> beginArrival()
        }
    }

    private fun beginArrival() {
        log("SESSION arrived paused=false")
        arrived = true
        holdForArrival = true
        ear?.cancel()
        announcer?.cancel()
        restTimer?.cancel()
        resting = false
        val arrivedIn = game
        val talking = runner
        scope.launch {
            // 말하던 중이면 그 문장이 끝날 때까지(최대 3초), 듣거나 계산 중이면 바로 넘어간다
            if (speakingNow) withTimeoutOrNull(ARRIVAL_FINISH_MS) { talking?.join() }
            if (game !== arrivedIn) return@launch
            runner?.cancel()
            pleos.stopSpeaking()
            // 마무리 전에 멈춤이 걸리면 말없이 끝낸다(F-05-02)
            if (pauses.isNotEmpty()) return@launch end(EndReason.ARRIVED)
            val nextStep = step(GameInput.Arrived)
            // 주차 브레이크와 함께 도착했고 "벌써 도착!" 카드가 있으면 마무리 첫 마디를 카드로(F-05-04, F-09-04)
            val card = cards.find(CardIds.ARRIVED)
            if (card != null && (pleos.vehicle.value.parkingBrake as? Reading.Value)?.value == true) {
                log("VOICE play card=ARRIVED trigger=nav-arrive state=$lastDriving")
                arrivalCardPlayed = speakWatched(SessionLines.cardText(CardIds.ARRIVED), card) == SpeakResult.Done
                if (pauses.isNotEmpty()) return@launch end(EndReason.ARRIVED)
            }
            position = Position.Thinking(nextStep)
            holdForArrival = false
            advance()
        }
    }

    private suspend fun awaitStep(result: Deferred<Result<GamePlan>>): GamePlan {
        publish(Phase.THINKING)
        return withEar { result.await() }.getOrElse {
            // 게임이 예외를 던지면 재시도하지 않고 종료한다(설계서 6절)
            log("SESSION step failed ${it::class.simpleName}")
            end(EndReason.FAILURE)
            throw CancellationException("game failed")
        }
    }

    private fun nextOf(plan: GamePlan): Next =
        when (val next = plan.next) {
            is GameNext.AwaitAnswer -> Next.Turn(next.turnId, next.prompt)
            GameNext.SuggestRest -> Next.Rest
            GameNext.Finished -> Next.Finish
        }

    private fun onCommand(
        command: VoiceCommand,
        heard: String,
    ) {
        val paused = pauses.isNotEmpty()
        log("SESSION command=$command heard=\"$heard\" paused=$paused resting=$resting")
        when (command) {
            VoiceCommand.QUIT ->
                if (paused) {
                    end(EndReason.USER)
                } else if (position !is Position.ConfirmQuit) {
                    position = Position.ConfirmQuit(back = requireNotNull(position))
                    advance()
                }
            VoiceCommand.REST -> if (!resting) rest("USER")
            VoiceCommand.RESUME -> if (resting && !paused) resume()
            VoiceCommand.REPEAT -> repeat(paused)
        }
    }

    /** 멈춤 중 재안내는 저장하지 않는다. 휴식 중이면 재개·종료 방법만 안내한다. */
    private fun repeat(paused: Boolean) {
        val at = position
        when {
            paused -> Unit
            resting -> announce(SessionLines.REST_HELP)
            at is Position.Saying -> {
                position = at.copy(index = 0)
                advance()
            }
            at is Position.Waiting -> {
                position = Position.Saying(listOf(GameMessage(at.prompt)), 0, Next.Turn(at.turnId, at.prompt))
                advance()
            }
        }
    }

    private fun rest(reason: String) {
        resting = true
        log("SESSION rest reason=$reason")
        pleos.stopSpeaking()
        restTimer =
            scope.launch {
                delay(REST_LIMIT_MS)
                end(EndReason.REST_TIMEOUT)
            }
        advance()
        // 멈춤 중이면 화면에만 표시하고 나중에 말하지 않는다
        if (pauses.isEmpty()) announce(SessionLines.REST)
    }

    private fun resume() {
        resting = false
        quietTurn = false
        restTimer?.cancel()
        announcer?.cancel()
        val back = requireNotNull(position)
        position = Position.Saying(listOf(GameMessage(SessionLines.RESUME)), 0, Next.Back(back))
        advance()
    }

    private fun announce(text: String) {
        announcer?.cancel()
        announcer = scope.launch { speakWatched(text) }
    }

    private fun listenForCommandsOnly() {
        if (ear?.isActive == true) return
        ear = scope.launch { hearCommands() }
    }

    private suspend fun <T> withEar(block: suspend () -> T): T =
        coroutineScope {
            val listening = launch { hearCommands() }
            try {
                block()
            } finally {
                listening.cancel()
            }
        }

    /** 제어 요청만 듣는다. 앱이 방금 말한 문장과 겹치는 인식(반향)은 버린다. */
    private suspend fun hearCommands() {
        while (true) {
            val result = pleos.listen()
            val text = (result as? ListenResult.Heard)?.text
            when {
                result is ListenResult.Failed && isVoiceBroken(result.code) -> return broken(result.code)
                // 오류가 이어질 때 쉬지 않고 다시 요청하지 않게 잠깐 쉰다
                text.isNullOrBlank() -> delay(RELISTEN_DELAY_MS)
                echo.isEcho(text) -> log("SESSION echo dropped")
                else -> VoiceCommand.parse(text)?.let { onCommand(it, text) }
            }
        }
    }

    /** 화면이 보이는지. 차량 반응 카드는 화면이 보일 때만(이용 전에도) 재생한다(F-09-03). */
    fun setScreenVisible(visible: Boolean) {
        screenVisible = visible
    }

    /**
     * 차량 반응 카드(F-09). 결정표상 멈춤 상태면 내지 않는다. 이용 중이면 하던 진행을 잠시 멈추고 카드를 재생한 뒤
     * 끊긴 문장부터, 듣던 중이었으면 새 입력 시도로 신호 없이 다시 듣는다. 이용 전에는 마이크를 켜지 않고 카드만 재생한다.
     */
    private fun react(reaction: Reaction) {
        // 이용 중 도착 카드는 도착 마무리(beginArrival)가 맡는다
        val handledByArrival = holdForArrival || arrived || (reaction.cardId == CardIds.ARRIVED && game != null)
        if (!screenVisible || pauses.isNotEmpty() || handledByArrival) return
        val name = reaction.cardId.removePrefix("vehicle-").uppercase()
        log("VOICE play card=$name trigger=${reaction.trigger} state=$lastDriving")
        val text = SessionLines.cardText(reaction.cardId)
        val audio = cards.find(reaction.cardId)
        cardJob?.cancel()
        if (game == null) {
            cardJob = scope.launch { speakWatched(text, audio) }
            return
        }
        runner?.cancel()
        ear?.cancel()
        announcer?.cancel()
        pleos.stopSpeaking()
        cardJob =
            scope.launch {
                speakWatched(text, audio)
                quietTurn = position is Position.Waiting
                advance()
            }
    }

    /** 카드로 "벌써 도착!"을 이미 말했으면 게임 마무리 문장의 같은 첫 마디를 빼서 두 번 말하지 않는다. */
    private fun withoutArrivalCheer(messages: List<GameMessage>): List<GameMessage> {
        val first = messages.firstOrNull() ?: return messages
        val rest = first.text.removePrefix(ARRIVAL_CHEER).trim()
        return if (rest.isEmpty()) messages.drop(1) else listOf(first.copy(text = rest)) + messages.drop(1)
    }

    /** [audio]가 있으면 목소리 카드를, 없으면 [text]를 TTS로 말한다. 반향 판별은 둘 다 [text] 기준. */
    private suspend fun speakWatched(
        text: String,
        audio: CardAudio? = null,
    ): SpeakResult {
        echo.speaking(text)
        speakingNow = true
        val result =
            try {
                if (audio != null) pleos.playCard(audio) else pleos.speak(text)
            } finally {
                speakingNow = false
                echo.done()
            }
        if (result is SpeakResult.Failed && isVoiceBroken(result.code)) broken(result.code)
        return result
    }

    /** 음성 기능 장애는 복구를 기다리지 않고 종료한다(F-04-07). */
    private fun broken(code: String) {
        log("SESSION voice broken code=$code")
        end(EndReason.FAILURE)
    }

    private fun publish(phase: Phase? = null) {
        val shown =
            when {
                game == null -> return
                pauses.isNotEmpty() -> Phase.PAUSED
                resting && position !is Position.ConfirmQuit -> Phase.RESTING
                else -> phase ?: mutableStatus.value.phase
            }
        // 게임 진행 값은 안내·듣기로 바뀔 때마다 읽는다(계산 중에는 직전 값). status()는 언제 불러도 된다는 계약이다
        val playing = game?.status()?.let(PlayStatus::from)
        mutableStatus.value = SessionStatus(shown, pauses.toSet(), play = playing, answerOpen = openTurn() != null)
    }

    /**
     * 주차 중 화면 버튼(난이도 등)을 일반 응답으로 보낸다(work-split: 버튼 글자를 후보 하나로). 열린 응답 차례일 때만,
     * 주차 중일 때만 받는다. 숨은 버튼을 강제로 눌러도 여기서 막힌다(F-06-02).
     */
    fun answerFromScreen(text: String): Boolean {
        val waiting = openTurn()
        if (!parked.value || waiting == null) {
            log("SESSION screen answer blocked")
            return false
        }
        runner?.cancel()
        position = Position.Thinking(step(GameInput.Answer(waiting.turnId, listOf(text))))
        advance()
        return true
    }

    /** 화면 답을 받을 수 있는 응답 차례. 확인 질문·멈춤·휴식·도착 마무리 중에는 없다. */
    private fun openTurn(): Position.Waiting? {
        val waiting = position as? Position.Waiting
        return waiting?.takeIf { pauses.isEmpty() && !resting && !arrived }
    }

    /** 주차 중 일시정지 버튼 = 휴식 요청(F-06-02). */
    fun restFromScreen(): Boolean {
        val allowed = parked.value && game != null && !resting
        if (allowed) rest("USER") else log("SESSION screen rest blocked")
        return allowed
    }

    /** 진행 위치. 재개는 여기서부터 이어간다. */
    private sealed interface Position {
        data class Saying(
            val lines: List<GameMessage>,
            val index: Int,
            val then: Next,
        ) : Position

        data class Waiting(
            val turnId: String,
            val prompt: String,
        ) : Position

        data class Thinking(
            val result: Deferred<Result<GamePlan>>,
        ) : Position

        data class ConfirmQuit(
            val back: Position,
        ) : Position
    }

    /** 말을 다 한 뒤 할 일. */
    private sealed interface Next {
        data class Turn(
            val turnId: String,
            val prompt: String,
        ) : Next

        data class Step(
            val input: GameInput,
        ) : Next

        data class Back(
            val position: Position,
        ) : Next

        data object Rest : Next

        data object Finish : Next
    }

    private companion object {
        const val NO_ANSWER_MS = 14_000L
        const val CONFIRM_WAIT_MS = 9_000L
        const val RESUME_DELAY_MS = 1_000L
        const val REST_LIMIT_MS = 120_000L
        const val RELISTEN_DELAY_MS = 300L
        const val ARRIVAL_FINISH_MS = 3_000L
        const val ARRIVAL_CHEER = "벌써 도착했어요!"
    }
}
