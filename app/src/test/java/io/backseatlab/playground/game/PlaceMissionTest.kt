package io.backseatlab.playground.game

import io.backseatlab.playground.game.api.GameInput
import io.backseatlab.playground.game.api.NoAnswerReason
import io.backseatlab.playground.game.api.Place
import io.backseatlab.playground.game.api.PlaceSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaceMissionTest {
    private val logs = mutableListOf<String>()

    private suspend fun start(places: PlaceSource) =
        ObserveGame(OBSERVE_MISSIONS, Random(1), places) { logs += it }.also {
            it.step(GameInput.Start)
            it.step(GameInput.Answer("t", listOf("중급")))
        }

    private val cafe = Place("별다방", "카페", 300)

    @Test
    fun `앞 150~500m 장소가 있으면 장소 미션을 내고 카드는 붙이지 않는다`() =
        runTest {
            val g = ObserveGame(OBSERVE_MISSIONS, Random(1), { listOf(Place("멀다방", "카페", 900), cafe) }) { logs += it }
            g.step(GameInput.Start)
            val plan = g.step(GameInput.Answer("t", listOf("중급")))
            assertEquals("중급으로 게임을 시작할게요! 곧 '별다방' 같은 가게가 보일지도 몰라요. 찾아볼까요?", plan.messages.single().text)
            assertNull(plan.messages.single().cardId)
            assertTrue(logs.contains("MISSION source=PLACE category=카페"))
            assertEquals("'별다방'", g.status().missionTitle)
        }

    @Test
    fun `장소 이름을 말하면 정답이다`() =
        runTest {
            val g = start { listOf(cafe) }
            g.step(GameInput.Answer("t", listOf("저기 별다방이다")))
            assertEquals(1, g.status().found)
        }

    @Test
    fun `범위 밖이거나 비었거나 실패하면 데이터 없는 미션으로 바꾼다`() =
        runTest {
            listOf<PlaceSource>(
                PlaceSource { listOf(Place("가까운집", "식당", 100)) },
                PlaceSource { emptyList() },
                PlaceSource { error("서버 없음") },
            ).forEach { source ->
                val plan =
                    ObserveGame(OBSERVE_MISSIONS, Random(1), source, { logs += it }).let {
                        it.step(GameInput.Start)
                        it.step(GameInput.Answer("t", listOf("중급")))
                    }
                assertTrue(
                    plan.messages
                        .single()
                        .cardId!!
                        .startsWith("mission-normal-"),
                )
            }
            assertEquals(3, logs.count { it.startsWith("MISSION source=BASIC") })
        }

    @Test
    fun `같은 장소를 연달아 내지 않는다`() =
        runTest {
            val g = start { listOf(cafe) }
            g.step(GameInput.Answer("t", listOf("별다방")))
            assertTrue(g.status().missionTitle != "'별다방'")
        }

    @Test
    fun `장소가 다시 멀어지면 지나갔다고 알리고 다음 미션으로 간다`() =
        runTest {
            var dist = 300
            val g = start { listOf(Place("별다방", "카페", dist)) }
            dist = 200
            assertTrue(g.step(GameInput.NoAnswer("t", NoAnswerReason.SILENT)).messages.isEmpty())
            dist = 240
            val plan = g.step(GameInput.Answer("t", listOf("바나나")))
            assertTrue(
                plan.messages
                    .single()
                    .text
                    .startsWith("방금 지나간 게 별다방이었어요! "),
            )
            assertEquals(1, g.status().played)
            assertEquals(0, g.status().found)
            assertTrue(logs.contains("MISSION source=PLACE result=PASSED"))
        }
}
