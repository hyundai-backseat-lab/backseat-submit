package io.backseatlab.playground.ui.cards

import io.backseatlab.playground.pleos.RecordResult
import io.backseatlab.playground.pleos.fake.FakePleosClient
import io.backseatlab.playground.session.CardSlot
import io.backseatlab.playground.session.CardStudio
import io.backseatlab.playground.session.CardTab
import io.backseatlab.playground.session.TakeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class CardsViewModelTest {
    @get:Rule val temp = TemporaryFolder()

    private val pleos = FakePleosClient()
    private val parked = MutableStateFlow(true)
    private val slots =
        listOf(
            CardSlot("mission-easy-1", "버스", CardTab.EASY),
            CardSlot("vehicle-wiper", "와이퍼 출동", CardTab.VEHICLE),
        )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `주차 중에만 열리고 녹음 중 출발하면 녹음을 멈추고 닫힌다`() =
        runTest(UnconfinedTestDispatcher()) {
            val studio = CardStudio(temp.newFolder(), pleos, consentAt = 1L, saveConsent = {}, clock = { 0L }, log = {})
            val viewModel = CardsViewModel(studio, parked, slots)

            parked.value = false
            viewModel.onOpenClick()
            assertFalse(viewModel.uiState.value.open)

            parked.value = true
            viewModel.onOpenClick()
            assertTrue(viewModel.uiState.value.open)
            assertEquals(
                listOf("버스"),
                viewModel.uiState.value.slots
                    .map { it.label },
            )
            viewModel.onTabClick(CardTab.VEHICLE)
            assertFalse(viewModel.onRecordClick("vehicle-wiper", micGranted = true))
            assertEquals("vehicle-wiper", viewModel.uiState.value.recording)

            parked.value = false
            assertEquals(1, pleos.stopRecordingCount)
            assertFalse(viewModel.uiState.value.open)
            assertNull(viewModel.uiState.value.recording)
        }

    @Test
    fun `다시 누르면 녹음을 멈추고 작은 소리면 한 번 더 크게를 보여 준다`() =
        runTest(UnconfinedTestDispatcher()) {
            val studio = CardStudio(temp.newFolder(), pleos, consentAt = 1L, saveConsent = {}, clock = { 0L }, log = {})
            val viewModel = CardsViewModel(studio, parked, slots)
            viewModel.onOpenClick()

            viewModel.onRecordClick("mission-easy-1", micGranted = true)
            viewModel.onRecordClick("mission-easy-1", micGranted = true)
            assertTrue(
                viewModel.uiState.value.slots
                    .single()
                    .recorded,
            )
            assertNull(viewModel.uiState.value.recording)

            pleos.recordResults += RecordResult.Done(durationMs = 2_000, maxAmplitude = 10)
            viewModel.onRecordClick("mission-easy-1", micGranted = true)
            assertEquals(TakeResult.TRY_LOUDER, viewModel.uiState.value.lastTake)
            assertTrue(viewModel.onRecordClick("mission-easy-1", micGranted = false))
        }
}
