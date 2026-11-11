package io.backseatlab.playground.di

import io.backseatlab.playground.pleos.PleosClient

/** release 빌드: Pleos를 그대로 쓴다. Sim 도구는 들어 있지 않다. */
fun decorateForBuildType(base: PleosClient): PleosClient = base
