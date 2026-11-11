package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.GameMessage
import io.backseatlab.playground.game.api.GameNext
import io.backseatlab.playground.game.api.GamePlan
import io.backseatlab.playground.game.api.GameSession

private const val MISSIONS_PER_CHAIN = 3

/** 복합 게임(gameId "giltam"): 관찰 미션 3개마다 말잇기 1판을 끼우고 다시 관찰 미션으로 돌아온다. */
class GiltamGame(
    private val observe: ObserveGame,
    private val newChain: () -> WordChain,
) : GameSession {
    private var chain: WordChain? = null
    private var turn = 0

    override suspend fun step(input: GameInput): GamePlan {
        val playing = chain
        return when {
            input == GameInput.Arrived -> observe.step(input)
            playing == null -> fromObserve(input)
            input is GameInput.Answer -> reply(playing.answer(input.candidates))
            input is GameInput.NoAnswer -> reply(playing.silent())
            else -> await(playing.reopen())
        }
    }

    override fun status() = observe.status().let { if (chain == null) it else it.copy(missionTitle = "말잇기") }

    override suspend fun close() = observe.close()

    private suspend fun fromObserve(input: GameInput): GamePlan {
        val before = observe.status().played
        val plan = observe.step(input)
        val played = observe.status().played
        val next = plan.next
        if (played == before || played % MISSIONS_PER_CHAIN != 0 || next !is GameNext.AwaitAnswer) return plan
        // 관찰 미션이 이미 다음 미션을 골라 안내했다. 안내 앞부분(칭찬)만 남기고 말잇기를 연다. 미션은 말잇기 뒤 Start로 다시 안내한다.
        val praise =
            plan.messages
                .lastOrNull()
                ?.text
                .orEmpty()
                .removeSuffix(next.prompt)
                .trim()
        val opened = newChain().also { chain = it }
        return await(listOf(praise, opened.open()).filter { it.isNotEmpty() }.joinToString(" "))
    }

    private suspend fun reply(reply: ChainReply): GamePlan {
        if (!reply.done) return await(reply.line)
        chain = null
        val back = observe.step(GameInput.Start)
        return GamePlan(listOf(GameMessage(reply.line)) + back.messages, back.next)
    }

    private fun await(text: String) = GamePlan(listOf(GameMessage(text)), GameNext.AwaitAnswer("w${++turn}", text))
}
