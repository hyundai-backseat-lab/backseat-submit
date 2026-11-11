package io.backseatlab.playground.place

import io.backseatlab.playground.game.api.Place
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ServerSocket

class HttpPlaceSourceTest {
    private val logs = mutableListOf<String>()
    private val here = Fix(37.5665, 126.978, 90f)
    private val four =
        """[{"name":"국밥집","category":"FOOD","lat":1.0,"lng":2.0,"distanceM":200},
           {"name":"약국","category":"PHARMACY","lat":1.0,"lng":2.0,"distanceM":300},
           {"name":"빵집","category":"FOOD","lat":1.0,"lng":2.0,"distanceM":400},
           {"name":"문구점","category":"SHOP","lat":1.0,"lng":2.0,"distanceM":450}]"""

    private fun source(
        url: String = "http://10.0.2.2:8080",
        fix: Fix? = here,
        fetch: suspend (String) -> String,
    ) = HttpPlaceSource(url, { fix }, { logs += it }, fetch = fetch)

    @Test
    fun `응답에서 최대 3개를 돌려주고 좌표는 로그에 남기지 않는다`() =
        runTest {
            var called = ""
            val places =
                source {
                    called = it
                    four
                }.ahead("FOOD")

            assertEquals(Place("국밥집", "FOOD", 200), places.first())
            assertEquals(3, places.size)
            assertTrue(called.startsWith("http://10.0.2.2:8080/api/places/ahead?lat=37.5665&lng=126.978&heading=90.0"))
            assertTrue(called.endsWith("&category=FOOD"))
            assertTrue(logs.single().startsWith("PLACE fetch count=3 ms="))
            assertTrue(logs.none { "37.5" in it || "126.9" in it })
        }

    @Test
    fun `주소가 없거나 위치를 모르거나 실패·2초 초과면 빈 목록이다`() =
        runTest {
            assertEquals(emptyList<Place>(), source(url = "") { four }.ahead(null))
            assertEquals(emptyList<Place>(), source(fix = null) { four }.ahead(null))
            assertEquals(emptyList<Place>(), source { throw IOException("status 500") }.ahead(null))
            assertEquals(emptyList<Place>(), source { "not json" }.ahead(null))
            assertEquals(
                emptyList<Place>(),
                source {
                    delay(3_000)
                    four
                }.ahead(null),
            )
            assertTrue("PLACE fallback reason=NO_URL" in logs)
            assertTrue("PLACE fallback reason=NO_LOCATION" in logs)
            assertTrue("PLACE fallback reason=TIMEOUT" in logs)
        }

    @Test
    fun `서버가 연결만 받고 응답하지 않아도 실제 2초 안팎에 빈 목록으로 돌아온다`() =
        runBlocking {
            ServerSocket(0).use { server ->
                val silent = HttpPlaceSource("http://127.0.0.1:${server.localPort}", { here }, { logs += it })
                val started = System.currentTimeMillis()
                val places = silent.ahead(null)
                val elapsed = System.currentTimeMillis() - started

                assertEquals(emptyList<Place>(), places)
                assertTrue("걸린 시간 $elapsed ms", elapsed < 2_800)
                assertTrue("PLACE fallback reason=TIMEOUT" in logs)
            }
        }
}
