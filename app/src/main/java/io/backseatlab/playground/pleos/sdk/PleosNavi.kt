package io.backseatlab.playground.pleos.sdk

import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import io.backseatlab.playground.BackseatLog
import io.backseatlab.playground.pleos.CarLocation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Pleos NaviHelper(3.0.5)에서 목적지 도착과 현재 위치를 받는다. 사용법: docs/sdk-notes/Navi.md */
class PleosNavi(
    context: Context,
) {
    private val navi = NaviHelper(context)
    private val mutableArrivals =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val arrivals: SharedFlow<Unit> = mutableArrivals

    @Volatile private var connected = false

    @Volatile private var pending: CompletableDeferred<CurrentLocationInfo>? = null
    private val asking = Mutex()

    private val events =
        object : NaviHelperEventListener {
            override fun onDestinationArrived(info: DestinationArrivedInfo) {
                BackseatLog.i("NAV arrived")
                mutableArrivals.tryEmit(Unit)
            }

            override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
                pending?.complete(currentLocationInfo)
            }
        }

    fun start() {
        // SDK 규칙: initialize가 끝난 뒤에 다른 API가 동작한다
        navi.initialize(
            onConnected = {
                BackseatLog.i("NAV init done")
                navi.addListener(events)
                connected = true
            },
            onFailed = { BackseatLog.w("NAV init failed", it) },
        )
    }

    /** getCurrentLocationInfo()는 결과를 onCurrentLocationInfo 콜백으로 준다. 한 번에 하나만 묻고 1초 안에 답이 없으면 null. */
    suspend fun location(): CarLocation? {
        if (!connected) return null
        return asking.withLock {
            val answer = CompletableDeferred<CurrentLocationInfo>()
            pending = answer
            val info =
                try {
                    navi.getCurrentLocationInfo()
                    withTimeoutOrNull(LOCATION_TIMEOUT_MS) { answer.await() }
                } finally {
                    pending = null
                }
            if (info == null) BackseatLog.i("NAV location none")
            info?.let { CarLocation(it.latitude, it.longitude, it.heading) }
        }
    }

    private companion object {
        const val LOCATION_TIMEOUT_MS = 1_000L
    }
}
