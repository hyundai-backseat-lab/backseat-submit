package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.CardIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameFactoryTest {
    @Test
    fun `observe와 giltam을 만든다`() {
        assertTrue(createGame("observe") { emptyList() } is ObserveGame)
        assertTrue(createGame("giltam") { emptyList() } is GiltamGame)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `모르는 게임 이름이면 예외`() {
        createGame("없음") { emptyList() }
    }

    @Test
    fun `미션 카드 ID 규칙`() {
        assertEquals("mission-normal-0", CardIds.mission("normal-0"))
    }
}
