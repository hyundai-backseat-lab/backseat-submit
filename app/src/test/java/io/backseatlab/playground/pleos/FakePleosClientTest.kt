package io.backseatlab.playground.pleos

import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakePleosClientTest {
    @Test
    fun `Fake는 말한 문장을 기록하고 정한 결과를 돌려준다`() =
        runTest {
            val pleos = FakePleosClient(speakResult = SpeakResult.Failed("UNKNOWN"))
            assertEquals(SpeakResult.Failed("UNKNOWN"), pleos.speak("안녕"))
            assertEquals(listOf("안녕"), pleos.spoken)
        }

    @Test
    fun `Fake는 미리 넣은 듣기 결과를 순서대로 돌려준다`() =
        runTest {
            val pleos = FakePleosClient()
            pleos.heard += listOf(ListenResult.Heard("빨간 사과"), ListenResult.Failed("TIMEOUT"))
            assertEquals(ListenResult.Heard("빨간 사과"), pleos.listen())
            assertEquals(ListenResult.Failed("TIMEOUT"), pleos.listen())
        }

    @Test
    fun `Fake에서 차량 값을 바꾸면 vehicle로 흘러 나온다`() {
        val pleos = FakePleosClient()
        pleos.updateVehicle { copy(brake = Reading.Value(true)) }
        assertEquals(Reading.Value(true), pleos.vehicle.value.brake)
    }
}
