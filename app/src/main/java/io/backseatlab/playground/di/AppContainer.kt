package io.backseatlab.playground.di

import android.content.Context
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.BuildConfig
import io.backseatlab.playground.R
import io.backseatlab.playground.game.OBSERVE_MISSIONS
import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.game.api.Difficulty
import io.backseatlab.playground.game.createGame
import io.backseatlab.playground.place.Fix
import io.backseatlab.playground.place.HttpPlaceSource
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.sdk.PleosSdkClient
import io.backseatlab.playground.session.CardSlot
import io.backseatlab.playground.session.CardStudio
import io.backseatlab.playground.session.CardTab
import io.backseatlab.playground.session.LocationConsent
import io.backseatlab.playground.session.SessionController
import io.backseatlab.playground.session.cardsAvailable
import io.backseatlab.playground.session.locate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import java.io.File

/** 앱에서 쓰는 객체를 만들어 서로 연결하는 곳. 라이브러리 없이 손으로 연결한다. */
class AppContainer(
    private val context: Context,
) {
    private val sdk = PleosSdkClient(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** debug 빌드에서는 Pleos 위에 `scripts/sim.sh` 덮어쓰기가 얹힌다. release는 Pleos 그대로. */
    val pleos: PleosClient = decorateForBuildType(sdk)

    private val prefs = context.getSharedPreferences("consent", Context.MODE_PRIVATE)
    val locationConsent =
        LocationConsent(prefs.getBoolean(LOCATION_CONSENT, false)) {
            prefs.edit().putBoolean(LOCATION_CONSENT, it).apply()
        }

    /** 장소 중계 API(F-16). 위치는 동의(F-11) 뒤에만 NaviHelper에서 받는다. 동의 전·위치 모름이면 빈 목록 → 데이터 없는 미션. */
    private val places =
        HttpPlaceSource(BuildConfig.PLACES_API_URL, locate = ::locate, log = BackseatLog::i)

    /** 프로세스와 수명이 같다. 화면이 다시 만들어져도 같은 컨트롤러에 연결된다. */
    val session =
        SessionController(
            pleos = pleos,
            newGame = { createGame("giltam", places) },
            scope = scope,
            log = BackseatLog::i,
            cards = ::findCard,
        )

    /** 아이 목소리 카드 녹음(F-08). [findCard]와 같은 폴더에 저장해 녹음 카드가 샘플보다 먼저 재생된다. */
    val cardStudio =
        CardStudio(
            dir = File(context.filesDir, "cards"),
            pleos = pleos,
            consentAt = prefs.getLong(CARD_CONSENT_AT, 0L).takeIf { it > 0L },
            saveConsent = { at -> prefs.edit().putLong(CARD_CONSENT_AT, at ?: 0L).apply() },
            clock = System::currentTimeMillis,
            log = BackseatLog::i,
        )

    /** 녹음 화면은 주차 중, 이용 전·종료 후에만 열린다. */
    val cardsAvailable: StateFlow<Boolean> =
        combine(session.parked, session.status) { parked, status -> cardsAvailable(parked, status.phase) }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** 녹음할 카드: 카드가 있는 관찰 미션(난이도별) + 차량 반응 카드 3개. */
    val cardSlots: List<CardSlot> =
        OBSERVE_MISSIONS.filter { it.hasCard }.map { CardSlot(CardIds.mission(it.id), it.title, tabOf(it.level)) } +
            listOf(
                CardSlot(CardIds.WIPER, context.getString(R.string.card_vehicle_wiper), CardTab.VEHICLE),
                CardSlot(CardIds.ARRIVED, context.getString(R.string.card_vehicle_arrived), CardTab.VEHICLE),
                CardSlot(CardIds.BYE, context.getString(R.string.card_vehicle_bye), CardTab.VEHICLE),
            )

    fun start() {
        sdk.start()
        // 동의한 뒤 바로 위치 구독을 켜 둔다. 첫 장소 미션 때 GPS 값이 아직 없어 다른 출처로 넘어가지 않게
        locationConsent.granted
            .filter { it }
            .onEach { locate() }
            .launchIn(scope)
    }

    private suspend fun locate(): Fix? {
        val here = locationConsent.locate(pleos) ?: return null
        return Fix(here.lat, here.lng, here.heading.toFloat())
    }

    /** 목소리 카드: 녹음 카드(F-08, 기기 저장) → 샘플 카드(debug assets) 순서. 둘 다 없으면 세션이 TTS로 말한다. */
    private fun findCard(cardId: String): CardAudio? {
        val recorded = cardStudio.recordedFile(cardId)
        val sample = "cards/$cardId.m4a"
        return when {
            recorded != null -> CardAudio.File(recorded.path)
            context.assets.list("cards")?.contains("$cardId.m4a") == true -> CardAudio.Asset(sample)
            else -> null
        }
    }

    private companion object {
        const val LOCATION_CONSENT = "location"
        const val CARD_CONSENT_AT = "cards_at"

        fun tabOf(level: Difficulty) =
            when (level) {
                Difficulty.EASY -> CardTab.EASY
                Difficulty.NORMAL -> CardTab.NORMAL
                Difficulty.HARD -> CardTab.HARD
            }
    }
}
