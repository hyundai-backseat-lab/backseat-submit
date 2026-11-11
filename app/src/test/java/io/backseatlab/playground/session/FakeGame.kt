package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameMessage
import io.backseatlab.playground.game.api.GameNext
import io.backseatlab.playground.game.api.GamePlan
import io.backseatlab.playground.game.api.GameSession
import io.backseatlab.playground.game.api.GameStatus
import kotlinx.coroutines.delay

/** 정해 둔 계획을 순서대로 돌려주는 가짜 게임. 받은 입력을 [inputs]에 남기고, 계산에 [stepMs]가 든다. 계획이 떨어지면 Finished. */
class FakeGame(
    vararg plans: GamePlan,
    private val stepMs: Long = 0,
    private val played: GameStatus = GameStatus(),
) : GameSession {
    private val queue = ArrayDeque(plans.toList())
    val inputs = mutableListOf<GameInput>()
    var closed = 0
        private set

    override suspend fun step(input: GameInput): GamePlan {
        inputs += input
        stepping++
        check(stepping == 1) { "step이 동시에 불렸다" }
        delay(stepMs)
        stepping--
        return queue.removeFirstOrNull() ?: GamePlan(emptyList(), GameNext.Finished)
    }

    var stepping = 0
        private set

    override fun status() = played

    override suspend fun close() {
        closed++
    }

    companion object {
        fun ask(
            text: String,
            turnId: String,
        ) = GamePlan(listOf(GameMessage(text)), GameNext.AwaitAnswer(turnId, text))

        fun finish(text: String) = GamePlan(listOf(GameMessage(text)), GameNext.Finished)

        fun suggestRest(text: String) = GamePlan(listOf(GameMessage(text)), GameNext.SuggestRest)
    }
}

/** step에서 항상 예외를 던지는 게임. */
class ThrowingGame : GameSession {
    override suspend fun step(input: GameInput): GamePlan = throw IllegalStateException("게임 오류")

    override fun status() = GameStatus()

    override suspend fun close() = Unit
}
