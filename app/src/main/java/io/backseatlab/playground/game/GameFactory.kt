package io.backseatlab.playground.game

import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.game.api.GameSession
import io.backseatlab.playground.game.api.PlaceSource
import kotlin.random.Random

/** 게임 구현을 고르는 유일한 지점. 개발자 A는 호출만 한다. */
fun createGame(
    gameId: String,
    places: PlaceSource,
): GameSession =
    when (gameId) {
        "observe" -> ObserveGame(places = places, log = BackseatLog::i)
        "giltam" ->
            GiltamGame(ObserveGame(places = places, log = BackseatLog::i)) {
                WordChain(WORD_LINKS, Random, BackseatLog::i)
            }
        else -> throw IllegalArgumentException("알 수 없는 게임: $gameId")
    }
