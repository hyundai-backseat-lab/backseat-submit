package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.CarLocation
import io.backseatlab.playground.pleos.PleosClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 위치정보 이용 동의(F-11). 동의 전에는 위치를 쓰지 않고 데이터 없는 미션만 낸다. 기기 저장은 di가 [save]로 맡는다. */
class LocationConsent(
    initial: Boolean,
    private val save: (Boolean) -> Unit,
) {
    private val state = MutableStateFlow(initial)
    val granted: StateFlow<Boolean> = state.asStateFlow()

    fun grant() {
        state.value = true
        save(true)
    }
}

/** 동의했을 때만 위치를 묻는다. 동의 전에는 PleosClient에 위치를 요청하지도 않는다. */
suspend fun LocationConsent.locate(pleos: PleosClient): CarLocation? = if (granted.value) pleos.location() else null
