package io.backseatlab.playground.place

import io.backseatlab.playground.game.api.Place
import io.backseatlab.playground.game.api.PlaceSource

/** 정해 둔 장소를 돌려주는 가짜 장소 검색. 테스트와 서버 없는 시연용. 부른 횟수를 [calls]에 센다. */
class FakePlaceSource(
    var places: List<Place> = emptyList(),
) : PlaceSource {
    var calls = 0
        private set

    override suspend fun ahead(category: String?): List<Place> {
        calls++
        return places
    }
}
