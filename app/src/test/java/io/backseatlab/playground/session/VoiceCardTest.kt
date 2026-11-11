package io.backseatlab.playground.session

import io.backseatlab.playground.game.api.CardIds
import io.backseatlab.playground.pleos.CardAudio
import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F-09: 아이 목소리 차량 반응 카드. */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCardTest {
    private val driving =
        VehicleState(
            gear = Reading.Value(Gear.DRIVE),
            parkingBrake = Reading.Value(false),
            brake = Reading.Value(false),
            wiper = Reading.Value(false),
            doorOpen = Reading.Value(false),
            turn = Reading.Value(Turn.OFF),
        )
    private val logs = mutableListOf<String>()
    private val stored = mapOf(CardIds.WIPER to CardAudio.Asset("cards/vehicle-wiper.m4a"))
    private lateinit var session: SessionController

    private fun TestScope.controller(
        pleos: FakePleosClient,
        game: FakeGame = FakeGame(FakeGame.ask("문제", "t1")),
        cards: VoiceCardStore = VoiceCardStore { stored[it] },
    ): SessionController {
        session =
            SessionController(pleos, { game }, backgroundScope, { testScheduler.currentTime }, { logs += it }, cards)
        session.setScreenVisible(true)
        runCurrent()
        return session
    }

    private fun TestScope.stop(pleos: FakePleosClient) {
        pleos.updateVehicle { copy(brake = Reading.Value(true)) }
        advanceTimeBy(2_100)
    }

    private fun FakePleosClient.wiper(on: Boolean) = updateVehicle { copy(wiper = Reading.Value(on)) }

    private fun FakePleosClient.cards() = events.filter { it.startsWith("card:") }

    @Test
    fun `정차 중 와이퍼가 켜지면 카드를 주행 1회당 한 번만 재생하고 주행 중에는 재생하지 않는다`() =
        runTest {
            val pleos = FakePleosClient(initial = driving)
            controller(pleos)
            pleos.wiper(true)
            runCurrent()
            assertEquals(0, pleos.cards().size)

            pleos.wiper(false)
            stop(pleos)
            pleos.wiper(true)
            runCurrent()
            assertEquals(1, pleos.cards().size)
            assertTrue("VOICE play card=WIPER trigger=wiper-on state=STOPPED" in logs)

            pleos.wiper(false)
            pleos.wiper(true)
            runCurrent()
            assertEquals(1, pleos.cards().size)

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK), wiper = Reading.Value(false)) }
            runCurrent()
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE)) }
            stop(pleos)
            pleos.wiper(true)
            runCurrent()
            assertEquals(2, pleos.cards().size)
        }

    @Test
    fun `이용 전에는 화면이 보일 때만 재생하고 마이크를 켜지 않는다`() =
        runTest {
            val pleos = FakePleosClient(initial = driving)
            controller(pleos).setScreenVisible(false)
            stop(pleos)
            pleos.wiper(true)
            runCurrent()
            assertEquals(0, pleos.cards().size)

            session.setScreenVisible(true)
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK)) }
            runCurrent()
            pleos.updateVehicle { copy(doorOpen = Reading.Value(true)) }
            runCurrent()
            assertEquals(listOf("다녀오세요!"), pleos.spoken)
            assertFalse("listen" in pleos.events)
        }

    @Test
    fun `결정표상 멈춤이면 카드를 내지 않고 재생 중 멈춤 신호가 오면 바로 멈춘다`() =
        runTest {
            val pleos = FakePleosClient(initial = driving, speakMs = 1_000)
            controller(pleos)
            stop(pleos)
            pleos.updateVehicle { copy(turn = Reading.Value(Turn.LEFT)) }
            pleos.wiper(true)
            runCurrent()
            assertEquals(0, pleos.cards().size)

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK), turn = Reading.Value(Turn.OFF)) }
            runCurrent()
            pleos.updateVehicle { copy(doorOpen = Reading.Value(true)) }
            runCurrent()
            assertEquals("다녀오세요!", pleos.spoken.lastOrNull())
            pleos.updateVehicle { copy(hazard = Reading.Value(true)) }
            runCurrent()
            assertEquals(1, pleos.stopSpeakingCount)
        }

    @Test
    fun `이용 중이면 듣기를 멈추고 카드를 재생한 뒤 신호 없이 새로 듣는다`() =
        runTest {
            val pleos = FakePleosClient(initial = driving)
            controller(pleos).start()
            stop(pleos)
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)
            runCurrent()
            val cuesBefore = pleos.events.count { it == "beep" }

            pleos.wiper(true)
            advanceTimeBy(500)

            assertEquals(1, pleos.cards().size)
            assertEquals(Phase.LISTENING, session.status.value.phase)
            assertEquals(cuesBefore, pleos.events.count { it == "beep" })
            val afterCard = pleos.events.drop(pleos.events.indexOfLast { it.startsWith("card:") } + 1)
            assertTrue("listen" in afterCard)
            assertFalse("beep" in afterCard)
        }

    @Test
    fun `주차 브레이크와 함께 도착하면 벌써 도착 카드를 먼저 재생하고 마무리 문장의 같은 첫 마디는 빼고 말한다`() =
        runTest {
            val arrived = mapOf(CardIds.ARRIVED to CardAudio.Asset("cards/vehicle-arrived.m4a"))
            val pleos = FakePleosClient(initial = driving)
            val game = FakeGame(FakeGame.ask("문제", "t1"), FakeGame.finish("벌써 도착했어요! 오늘 미션 2개 찾았어요."))
            controller(pleos, game) { arrived[it] }.start()
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)

            pleos.updateVehicle { copy(parkingBrake = Reading.Value(true)) }
            pleos.arrive()
            advanceTimeBy(1_000)

            assertEquals(1, pleos.cards().size)
            assertEquals("오늘 미션 2개 찾았어요.", pleos.spoken.last())
            assertEquals(EndReason.ARRIVED, session.status.value.endReason)

            // 이용이 끝난 뒤의 도착은 주차 브레이크가 10초 안에 걸리면 카드만
            pleos.updateVehicle { copy(parkingBrake = Reading.Value(false)) }
            pleos.arrive()
            advanceTimeBy(5_000)
            pleos.updateVehicle { copy(parkingBrake = Reading.Value(true)) }
            runCurrent()
            assertEquals(2, pleos.cards().size)
        }

    @Test
    fun `도착 카드 중 멈추면 마무리 없이 끝낸다`() =
        runTest {
            val arrived = mapOf(CardIds.ARRIVED to CardAudio.Asset("cards/vehicle-arrived.m4a"))
            val pleos = FakePleosClient(initial = driving, speakMs = 1_000)
            val game = FakeGame(FakeGame.ask("문제", "t1"), FakeGame.finish("벌써 도착했어요! 오늘 미션 1개 찾았어요."))
            controller(pleos, game) { arrived[it] }.start()
            while (session.status.value.phase != Phase.LISTENING) advanceTimeBy(100)

            pleos.updateVehicle { copy(parkingBrake = Reading.Value(true)) }
            pleos.arrive()
            advanceTimeBy(300)
            pleos.updateVehicle { copy(hazard = Reading.Value(true)) }
            advanceTimeBy(5_000)

            assertEquals(EndReason.ARRIVED, session.status.value.endReason)
            assertFalse(pleos.spoken.any { "오늘 미션" in it })
        }
}
