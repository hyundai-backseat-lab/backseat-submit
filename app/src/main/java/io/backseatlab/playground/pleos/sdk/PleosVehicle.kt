package io.backseatlab.playground.pleos.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.constant.telltale.TelltaleType
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.content.Context
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.Gear
import io.backseatlab.playground.pleos.Reading
import io.backseatlab.playground.pleos.Turn
import io.backseatlab.playground.pleos.VehicleState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Pleos Vehicle SDK(2.3.2)를 VehicleState로 바꾼다. 신호마다
 * Capability 확인 → 현재 값(Get) 한 번 → 변경 알림(Callback) 등록 순서로 연결한다.
 * 속도는 SDK에 없어서 항상 "미지원"이다(docs/sdk-notes/Vehicle.md).
 *
 * ponytail: 앱 프로세스와 수명이 같아 release/unregister를 하지 않는다. 여러 번 만들게 되면 stop()을 추가한다.
 */
class PleosVehicle(
    private val context: Context,
) {
    private val mutableState = MutableStateFlow(VehicleState(speedKmh = Reading.Unsupported))
    val state: StateFlow<VehicleState> = mutableState

    private val doorPositions = mutableMapOf<Int, Int?>()

    fun start() {
        val v =
            try {
                Vehicle(context).also { it.initialize() }
            } catch (e: Exception) {
                BackseatLog.w("CAR init failed", e)
                return
            }
        // 신호마다 따로 감싼다: 권한 거부 같은 예외가 한 신호에서 나도 나머지는 계속 등록된다
        listOf(::watchTurn, ::watchBrake, ::watchGear, ::watchWiper, ::watchDoors, ::watchTelltales).forEach { watch ->
            try {
                watch(v)
            } catch (e: Exception) {
                BackseatLog.w("CAR watch failed", e)
            }
        }
    }

    private fun change(transform: VehicleState.() -> VehicleState) {
        mutableState.update { it.transform() }
    }

    private fun failed(name: String): (Exception) -> Unit = { BackseatLog.w("CAR $name failed", it) }

    private fun unsupported(
        name: String,
        transform: VehicleState.() -> VehicleState,
    ) {
        BackseatLog.i("CAR unsupported $name")
        change(transform)
    }

    private fun CapabilityConfig.canRead() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    private fun watchTurn(v: Vehicle) {
        val turnSignal = v.getTurnSignal()
        val listener =
            object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                    change { copy(turn = value.toReading()) }
                }

                override fun onFailed(e: Exception) = failed("turn")(e)
            }
        turnSignal.checkTurnSignalStateCapabilities(
            { cap ->
                if (cap.canRead()) {
                    turnSignal.getCurrentTurnSignalState({ change { copy(turn = it.toReading()) } }, failed("turn"))
                    turnSignal.registerTurnSignalState(listener)
                } else {
                    unsupported("turn") { copy(turn = Reading.Unsupported) }
                }
            },
            failed("turn"),
        )
    }

    private fun watchBrake(v: Vehicle) {
        val brake = v.getBrake()
        val listener =
            object : BrakePedalStatusListener {
                override fun onBrakePedalStatusUpdated(value: Boolean?) {
                    change { copy(brake = value.toReading()) }
                }

                override fun onFailed(e: Exception) = failed("brake")(e)
            }
        brake.checkBrakePedalStatusCapabilities(
            { cap ->
                if (cap.canRead()) {
                    brake.getCurrentBrakePedalStatus({ change { copy(brake = it.toReading()) } }, failed("brake"))
                    brake.registerBrakePedalStatus(listener)
                } else {
                    unsupported("brake") { copy(brake = Reading.Unsupported) }
                }
            },
            failed("brake"),
        )
    }

    /** 기어는 Capability 확인 메서드가 없다. 조회가 실패하면 "모름"으로 두고 로그만 남긴다(일시 실패와 미지원을 구분할 수 없다). */
    private fun watchGear(v: Vehicle) {
        val gear = v.getGear()
        val listener =
            object : GearSelectionListener {
                override fun onSelected(value: VehicleGear?) {
                    change { copy(gear = value.toReading()) }
                }

                override fun onFailed(e: Exception) = failed("gear")(e)
            }
        gear.getCurrentGearSelectionState(
            { change { copy(gear = it.toReading()) } },
            failed("gear"),
        )
        gear.registerGearSelection(listener)
    }

    private fun watchWiper(v: Vehicle) {
        val wiper = v.getWiper()
        val area = WindowArea(WindowValue.FRONT_WINDSHIELD)
        val listener =
            object : WiperStateListener {
                override fun onWiperStateUpdated(value: WiperState?) {
                    change { copy(wiper = value.toReading()) }
                }

                override fun onFailed(e: Exception) = failed("wiper")(e)
            }
        wiper.checkWiperStateCapabilities(
            { areas ->
                if (area in areas.keys) {
                    wiper.getCurrentWiperState(
                        area,
                        { change { copy(wiper = it.toReading()) } },
                        failed("wiper"),
                    )
                    wiper.registerWiperState(area, listener)
                } else {
                    unsupported("wiper") { copy(wiper = Reading.Unsupported) }
                }
            },
            failed("wiper"),
        )
    }

    /** 지원하는 문마다 리스너를 하나씩 달고, 하나라도 열려 있으면 "문 열림"이다. */
    private fun watchDoors(v: Vehicle) {
        val door = v.getDoor()
        door.checkDoorPositionCapabilities(
            { areas ->
                if (areas.isEmpty()) {
                    unsupported("door") { copy(doorOpen = Reading.Unsupported) }
                }
                areas.keys.forEachIndexed { index, area ->
                    val listener =
                        object : DoorPositionListener {
                            override fun onPositionUpdated(value: Int?) = setDoor(index, value)

                            override fun onFailed(e: Exception) = failed("door")(e)
                        }
                    door.getCurrentDoorPosition(area, { setDoor(index, it) }, failed("door"))
                    door.registerDoorPosition(area, listener)
                }
            },
            failed("door"),
        )
    }

    /** 하나라도 열려 있으면 열림. 열린 문은 없지만 값을 못 받은 문이 있으면 "모름". */
    private fun setDoor(
        index: Int,
        position: Int?,
    ) {
        synchronized(doorPositions) {
            doorPositions[index] = position
            val reading =
                when {
                    doorPositions.values.any { (it ?: 0) > 0 } -> Reading.Value(true)
                    doorPositions.values.any { it == null } -> Reading.Unknown
                    else -> Reading.Value(false)
                }
            change { copy(doorOpen = reading) }
        }
    }

    /** 비상등·주차 브레이크·경고는 전용 API가 없고 계기판 경고등(Telltale)으로만 온다. */
    private fun watchTelltales(v: Vehicle) {
        val safety = v.getSafety()
        val listener =
            object : TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(value: Telltales?) = applyTelltales(value)

                override fun onFailed(e: Exception) = failed("telltale")(e)
            }
        safety.checkTelltalesCapabilities(
            { cap ->
                if (cap.canRead()) {
                    safety.getTelltalesInfo(::applyTelltales, failed("telltale"))
                    safety.registerTelltales(listener)
                } else {
                    unsupported("telltale") {
                        copy(
                            hazard = Reading.Unsupported,
                            parkingBrake = Reading.Unsupported,
                            warning = Reading.Unsupported,
                        )
                    }
                }
            },
            failed("telltale"),
        )
    }

    private fun applyTelltales(telltales: Telltales?) {
        if (telltales == null) return
        val lit =
            telltales.telltales
                .filter { it.state != TelltaleState.OFF }
                .map { it.type }
                .toSet()
        change {
            copy(
                hazard = Reading.Value(TelltaleType.TURN_SIGNAL_LEFT in lit && TelltaleType.TURN_SIGNAL_RIGHT in lit),
                parkingBrake =
                    Reading.Value(
                        TelltaleType.PARKING_BRAKE in lit || TelltaleType.ELECTRONIC_PARKING_BRAKE in lit,
                    ),
                warning = Reading.Value(TelltaleType.MASTER_WARNING in lit),
            )
        }
    }

    /** 값이 null이면 "모름"이다. 안전 판정에서는 위험한 쪽으로 본다. */
    private fun Boolean?.toReading(): Reading<Boolean> = if (this == null) Reading.Unknown else Reading.Value(this)

    private fun WiperState?.toReading(): Reading<Boolean> {
        if (this == null) return Reading.Unknown
        return Reading.Value(this == WiperState.ON)
    }

    private fun TurnSignalValue?.toReading(): Reading<Turn> =
        when (this) {
            null -> Reading.Unknown
            TurnSignalValue.LEFT -> Reading.Value(Turn.LEFT)
            TurnSignalValue.RIGHT -> Reading.Value(Turn.RIGHT)
            else -> Reading.Value(Turn.OFF)
        }

    private fun VehicleGear?.toReading(): Reading<Gear> =
        when (this) {
            null, VehicleGear.GEAR_UNKNOWN -> Reading.Unknown
            VehicleGear.GEAR_PARK -> Reading.Value(Gear.PARK)
            VehicleGear.GEAR_REVERSE -> Reading.Value(Gear.REVERSE)
            VehicleGear.GEAR_NEUTRAL -> Reading.Value(Gear.NEUTRAL)
            else -> Reading.Value(Gear.DRIVE)
        }
}
