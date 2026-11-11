package io.backseatlab.playground.place

import io.backseatlab.playground.game.api.Place
import io.backseatlab.playground.game.api.PlaceSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.EmptyCoroutineContext

/** 현재 위치와 진행 방향(도). 중계 API를 부를 때만 쓰고 저장하거나 로그에 남기지 않는다. */
data class Fix(
    val lat: Double,
    val lng: Double,
    val heading: Float,
)

/**
 * 장소 중계 API(docs/api-places.md)로 진행 방향 앞의 장소를 가져온다. 카카오 키는 중계 서버에만 있다.
 * 주소가 없거나 위치를 모르거나 실패·2초 초과면 빈 목록을 돌려 게임이 데이터 없는 미션으로 바꾼다.
 */
class HttpPlaceSource(
    private val baseUrl: String,
    private val locate: suspend () -> Fix?,
    private val log: (String) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val fetch: suspend (String) -> String = ::httpGet,
) : PlaceSource {
    override suspend fun ahead(category: String?): List<Place> {
        val fix = if (baseUrl.isBlank()) null else runCatching { locate() }.onFailure(::rethrowCancel).getOrNull()
        if (fix == null) {
            log("PLACE fallback reason=${if (baseUrl.isBlank()) "NO_URL" else "NO_LOCATION"}")
            return emptyList()
        }
        val started = clock()
        // 어떤 실패든 빈 목록으로: 서버가 꺼져 있어도 이용은 끊기지 않는다. 이용 종료(취소)만 그대로 올린다
        val result = runCatching { withTimeoutOrNull(TIMEOUT_MS) { parse(fetch(url(fix, category))) } }
        result.exceptionOrNull()?.let(::rethrowCancel)
        val places = result.getOrNull()
        val reason =
            when {
                result.isFailure -> result.exceptionOrNull()?.let { it::class.simpleName }
                places == null -> "TIMEOUT"
                else -> null
            }
        reason?.let { log("PLACE fallback reason=$it") }
        return places.orEmpty().also { log("PLACE fetch count=${it.size} ms=${clock() - started}") }
    }

    private fun rethrowCancel(error: Throwable) {
        if (error is CancellationException) throw error
    }

    private fun url(
        fix: Fix,
        category: String?,
    ): String {
        val query = "lat=${fix.lat}&lng=${fix.lng}&heading=${fix.heading}"
        val extra = category?.let { "&category=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return baseUrl.trimEnd('/') + "/api/places/ahead?" + query + extra
    }

    private fun parse(body: String): List<Place> {
        val items = JSONArray(body)
        return (0 until minOf(items.length(), MAX_PLACES)).map { i ->
            val item = items.getJSONObject(i)
            Place(item.getString("name"), item.getString("category"), item.getInt("distanceM"))
        }
    }

    companion object {
        const val TIMEOUT_MS = 2_000L
        private const val MAX_PLACES = 3
    }
}

/**
 * 기본 HTTP GET. 200이 아니면 IOException. 블로킹 소켓은 코루틴 취소로 멈추지 않으므로,
 * 취소(2초 초과)되면 연결을 끊어 호출한 쪽이 바로 돌아가게 한다.
 */
private suspend fun httpGet(url: String): String =
    suspendCancellableCoroutine { cont ->
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = HttpPlaceSource.TIMEOUT_MS.toInt()
        connection.readTimeout = HttpPlaceSource.TIMEOUT_MS.toInt()
        cont.invokeOnCancellation { connection.disconnect() }
        Dispatchers.IO.dispatch(
            EmptyCoroutineContext,
            Runnable {
                val body =
                    runCatching {
                        val status = connection.responseCode
                        if (status != HttpURLConnection.HTTP_OK) throw IOException("status $status")
                        connection.inputStream.bufferedReader().use { it.readText() }
                    }
                connection.disconnect()
                cont.resumeWith(body)
            },
        )
    }
