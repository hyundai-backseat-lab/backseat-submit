package io.backseatlab.playground.di

import io.backseatlab.playground.debug.SimPleosClient
import io.backseatlab.playground.pleos.PleosClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** debug 빌드: Pleos 위에 `scripts/sim.sh`로 차량 신호와 인식 결과를 바꾸는 덮어쓰기를 얹는다. */
fun decorateForBuildType(base: PleosClient): PleosClient {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    return SimPleosClient(base, scope)
}
