package io.backseatlab.playground.pleos.sdk

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.SystemClock
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.CarLocation

/**
 * 안드로이드 GPS 위치. 에뮬레이터에서 NaviHelper 현재 위치는 `adb emu geo fix`를 따라가지 않아(D-031),
 * 시연 경로(scripts/route.sh)를 재현하려고 GPS를 먼저 쓴다. 진행 방향은 GPS 방향이 없으면 앞뒤 좌표로 계산한다.
 */
class GpsLocation(
    context: Context,
) {
    private val manager = context.getSystemService(LocationManager::class.java)

    @Volatile private var listening = false

    @Volatile private var latest: CarLocation? = null

    @Volatile private var latestAt = 0L
    private var previous: Location? = null

    private val listener = LocationListener { update(it) }

    /** 10초 안의 GPS 위치. 진행 방향을 아직 모르거나(첫 위치) 오래됐으면 null(터널 등에서 옛 위치로 찾지 않게). */
    fun latest(): CarLocation? {
        if (!listening) listen()
        return latest?.takeIf { SystemClock.elapsedRealtime() - latestAt <= MAX_AGE_MS }
    }

    // 권한은 동의 버튼(F-11)에서 받는다. 권한 전이면 SecurityException → 다음 호출에서 다시 시도
    @SuppressLint("MissingPermission")
    private fun listen() {
        runCatching {
            val looper = Looper.getMainLooper()
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, 0f, listener, looper)
            listening = true
        }.onFailure { BackseatLog.i("GPS listen failed=${it::class.simpleName}") }
    }

    private fun update(location: Location) {
        val before = previous
        val heading =
            when {
                location.hasBearing() && location.speed > 0f -> location.bearing.toDouble()
                before != null && before.distanceTo(location) >= MIN_MOVE_M -> before.bearingTo(location).mod(FULL_TURN)
                else -> latest?.heading
            }
        if (before == null || before.distanceTo(location) >= MIN_MOVE_M) previous = location
        latest = heading?.let { CarLocation(location.latitude, location.longitude, it) }
        latestAt = SystemClock.elapsedRealtime()
    }

    private companion object {
        const val INTERVAL_MS = 1_000L
        const val MAX_AGE_MS = 10_000L
        const val MIN_MOVE_M = 5f
        const val FULL_TURN = 360.0
    }
}
