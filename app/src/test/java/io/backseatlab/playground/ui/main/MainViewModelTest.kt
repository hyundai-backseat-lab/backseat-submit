package io.backseatlab.playground.ui.main

import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.VehicleState
import io.backseatlab.playground.pleos.fake.FakePleosClient
import io.backseatlab.playground.session.FakeGame
import io.backseatlab.playground.session.LocationConsent
import io.backseatlab.playground.session.Phase
import io.backseatlab.playground.session.SessionController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private val pleos = FakePleosClient(initial = VehicleState(gear = Reading.Value(Gear.PARK)))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `권한을 묻는 사이 출발하면 시작 요청이 취소되고 다시 주차해도 새로 눌러야 한다`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = SessionController(pleos, { FakeGame(FakeGame.ask("문제", "t1")) }, backgroundScope, log = {})
            val viewModel = MainViewModel(session, LocationConsent(false) {})

            assertTrue(viewModel.onStartClick(micGranted = false))
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE)) }
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK)) }
            viewModel.onMicPermissionResult(granted = true)

            assertEquals(Phase.IDLE, session.status.value.phase)
            assertTrue(viewModel.uiState.value.showStart)
        }

    @Test
    fun `주행 중에는 시작 버튼이 없고 이유를 보여 준다`() =
        runTest(UnconfinedTestDispatcher()) {
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE)) }
            val session = SessionController(pleos, { FakeGame() }, backgroundScope, log = {})
            val viewModel = MainViewModel(session, LocationConsent(false) {})

            assertFalse(viewModel.uiState.value.showStart)
            assertFalse(viewModel.onStartClick(micGranted = true))
            assertEquals(Phase.IDLE, session.status.value.phase)
        }

    @Test
    fun `이용 중 주행이면 버튼 없이 요약만, 주차면 난이도와 쉬기 버튼을 보인다`() =
        runTest(UnconfinedTestDispatcher()) {
            val session = SessionController(pleos, { FakeGame(FakeGame.ask("문제", "t1")) }, backgroundScope, log = {})
            val viewModel = MainViewModel(session, LocationConsent(false) {})
            session.start()
            while (session.status.value.phase != Phase.LISTENING) testScheduler.advanceTimeBy(100)

            assertTrue(viewModel.uiState.value.showDifficulty)
            assertTrue(viewModel.uiState.value.showPause)
            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE)) }
            assertFalse(viewModel.uiState.value.showDifficulty)
            assertFalse(viewModel.uiState.value.showPause)
            assertFalse(viewModel.uiState.value.showStart)
        }

    @Test
    fun `위치 동의 버튼은 주차 중 동의 전에만 보이고 권한을 받아야 동의된다`() =
        runTest(UnconfinedTestDispatcher()) {
            val saved = mutableListOf<Boolean>()
            val consent = LocationConsent(false) { saved += it }
            val session = SessionController(pleos, { FakeGame() }, backgroundScope, log = {})
            val viewModel = MainViewModel(session, consent)

            assertTrue(viewModel.uiState.value.showLocationConsent)
            assertTrue(viewModel.onLocationConsentClick(locationGranted = false))
            viewModel.onLocationPermissionResult(granted = false)
            assertFalse(consent.granted.value)

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.DRIVE)) }
            assertFalse(viewModel.uiState.value.showLocationConsent)
            assertFalse(viewModel.onLocationConsentClick(locationGranted = true))
            assertFalse(consent.granted.value)

            pleos.updateVehicle { copy(gear = Reading.Value(Gear.PARK)) }
            assertFalse(viewModel.onLocationConsentClick(locationGranted = true))
            assertTrue(consent.granted.value)
            assertEquals(listOf(true), saved)
            assertFalse(viewModel.uiState.value.showLocationConsent)
        }
}
