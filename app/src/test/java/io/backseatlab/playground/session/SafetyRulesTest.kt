package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.Driving
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import io.backseatlab.playground.session.SafetyAction.CONTINUE
import io.backseatlab.playground.session.SafetyAction.END
import io.backseatlab.playground.session.SafetyAction.PAUSE
import org.junit.Assert.assertEquals
import org.junit.Test

class SafetyRulesTest {
    /** features.md 결정표 그대로. 열 순서: 주차, 정차, 저속, 주행. 저속은 정차와 같은 판정(D-027). */
    private val table =
        mapOf(
            SafetySignal.TURN to listOf(CONTINUE, PAUSE, PAUSE, PAUSE),
            SafetySignal.BRAKE to listOf(CONTINUE, CONTINUE, CONTINUE, PAUSE),
            SafetySignal.HAZARD to listOf(PAUSE, PAUSE, PAUSE, PAUSE),
            SafetySignal.OTHER_VOICE to listOf(PAUSE, PAUSE, PAUSE, PAUSE),
            SafetySignal.WARNING to listOf(END, END, END, END),
        )
    private val columns = listOf(Driving.PARKED, Driving.STOPPED, Driving.STOPPED, Driving.DRIVING)

    @Test
    fun `결정표의 모든 칸대로 판정한다`() {
        table.forEach { (signal, expected) ->
            columns.zip(expected).forEach { (driving, action) ->
                assertEquals("$signal × $driving", action, SafetyRules.decide(driving, signal))
            }
        }
    }

    @Test
    fun `좌우 동시 점등은 방향지시등이 아니라 비상등이고 모르는 값은 켜진 것으로 보지 않는다`() {
        val hazard = VehicleState(hazard = Reading.Value(true), turn = Reading.Value(Turn.LEFT))
        assertEquals(setOf(SafetySignal.HAZARD), SafetyRules.activeSignals(hazard, focusLost = false))
        assertEquals(
            setOf(SafetySignal.TURN, SafetySignal.OTHER_VOICE),
            SafetyRules.activeSignals(VehicleState(turn = Reading.Value(Turn.RIGHT)), focusLost = true),
        )
        assertEquals(emptySet<SafetySignal>(), SafetyRules.activeSignals(VehicleState(), focusLost = false))
    }
}
