package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.RecordResult
import io.backseatlab.playground.pleos.fake.FakePleosClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CardStudioTest {
    @get:Rule val temp = TemporaryFolder()

    private val pleos = FakePleosClient()
    private val savedConsent = mutableListOf<Long?>()

    private fun studio(dir: File = temp.newFolder("cards")) =
        CardStudio(dir, pleos, consentAt = null, saveConsent = { savedConsent += it }, clock = { 1_000L }, log = {})

    @Test
    fun `동의 전에는 녹음하지 않고 동의 시각을 저장하며 작거나 짧은 녹음은 다시 받는다`() =
        runTest {
            val studio = studio()

            assertEquals(TakeResult.NO_CONSENT, studio.record("vehicle-wiper"))
            assertEquals(0, pleos.recordCount)
            studio.agree()
            assertEquals(1_000L, studio.consentAt.value)
            assertEquals(listOf<Long?>(1_000L), savedConsent)

            pleos.recordResults += RecordResult.Done(durationMs = 300, maxAmplitude = 5_000)
            assertEquals(TakeResult.TRY_LOUDER, studio.record("vehicle-wiper"))
            pleos.recordResults += RecordResult.Done(durationMs = 2_000, maxAmplitude = 100)
            assertEquals(TakeResult.TRY_LOUDER, studio.record("vehicle-wiper"))
            pleos.recordResults += RecordResult.Failed("IO")
            assertEquals(TakeResult.FAILED, studio.record("vehicle-wiper"))
            assertTrue(studio.recorded.value.isEmpty())

            pleos.recordResults += RecordResult.Done(durationMs = 2_000, maxAmplitude = 5_000)
            assertEquals(TakeResult.SAVED, studio.record("vehicle-wiper"))
            assertEquals(setOf("vehicle-wiper"), studio.recorded.value)
            assertEquals(CardStudio.MAX_RECORD_MS, pleos.lastRecordMaxMs)
        }

    @Test
    fun `카드는 개별·전체 삭제되고 재시작해도 남으며 동의를 철회하면 모두 지운다`() =
        runTest {
            val dir = temp.newFolder("cards")
            val studio = studio(dir).apply { agree() }
            listOf("vehicle-wiper", "vehicle-bye", "mission-easy-1").forEach {
                pleos.recordResults += RecordResult.Done(2_000, 5_000)
                studio.record(it)
            }
            assertEquals(File(dir, "vehicle-wiper.m4a"), studio.recordedFile("vehicle-wiper"))
            assertNull(studio.recordedFile("vehicle-arrived"))
            studio.delete("vehicle-bye")
            assertEquals(setOf("vehicle-wiper", "mission-easy-1"), studio.recorded.value)
            assertTrue(File(dir, "vehicle-wiper.m4a").exists())

            File(dir, ".take.m4a").writeText("남은 임시 녹음")
            val restarted = CardStudio(dir, pleos, consentAt = 1_000L, saveConsent = {}, clock = { 0L }, log = {})
            assertEquals(setOf("vehicle-wiper", "mission-easy-1"), restarted.recorded.value)
            restarted.deleteAll()
            assertTrue(restarted.recorded.value.isEmpty())

            pleos.recordResults += RecordResult.Done(2_000, 5_000)
            restarted.record("vehicle-arrived")
            restarted.revoke()
            assertNull(restarted.consentAt.value)
            assertTrue(restarted.recorded.value.isEmpty())
            assertFalse(File(dir, "vehicle-arrived.m4a").exists())
            assertFalse(File(dir, ".take.m4a").exists())
        }

    @Test
    fun `녹음 화면은 주차 중 이용 전·종료 후에만 열 수 있다`() {
        assertTrue(cardsAvailable(parked = true, phase = Phase.IDLE))
        assertTrue(cardsAvailable(parked = true, phase = Phase.ENDED))
        assertFalse(cardsAvailable(parked = true, phase = Phase.LISTENING))
        assertFalse(cardsAvailable(parked = false, phase = Phase.IDLE))
    }
}
