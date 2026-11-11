package io.backseatlab.playground.session

import io.backseatlab.playground.pleos.PleosClient
import io.backseatlab.playground.pleos.RecordResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** 녹음 화면(SC-08)의 탭. 미션 카드는 난이도별, 차량 반응 카드는 한 탭. */
enum class CardTab { EASY, NORMAL, HARD, VEHICLE }

/** 녹음할 수 있는 카드 한 칸. [cardId]는 game/api `CardIds` 형식. */
data class CardSlot(
    val cardId: String,
    val label: String,
    val tab: CardTab,
)

enum class TakeResult { SAVED, TRY_LOUDER, FAILED, NO_CONSENT }

/** 녹음 화면은 주차 중이고 이용 전·종료 후일 때만 열 수 있다. 이용 중 STT와 마이크가 겹치지 않게. */
fun cardsAvailable(
    parked: Boolean,
    phase: Phase,
): Boolean = parked && (phase == Phase.IDLE || phase == Phase.ENDED)

/**
 * 아이 목소리 카드 녹음·보관(F-08). 카드는 [dir]/<cardId>.m4a로 이 기기에만 저장되고,
 * di의 카드 찾기(VoiceCardStore)가 같은 위치를 샘플 카드보다 먼저 본다.
 * 보호자 동의 시각은 [saveConsent]로 di가 기기에 저장한다. 동의를 철회하면 카드를 모두 지운다.
 */
class CardStudio(
    private val dir: File,
    private val pleos: PleosClient,
    consentAt: Long?,
    private val saveConsent: (Long?) -> Unit,
    private val clock: () -> Long,
    private val log: (String) -> Unit,
) {
    private val consent = MutableStateFlow(consentAt)
    val consentAt: StateFlow<Long?> = consent.asStateFlow()

    private val take = File(dir, ".take.m4a")

    // 녹음 중 앱이 죽어 남은 임시 녹음을 지운다
    private val cards = MutableStateFlow(take.delete().let { scan() })
    val recorded: StateFlow<Set<String>> = cards.asStateFlow()

    fun agree() {
        val now = clock()
        consent.value = now
        saveConsent(now)
        log("CARD consent")
    }

    fun revoke() {
        deleteAll()
        consent.value = null
        saveConsent(null)
        log("CARD consent revoked")
    }

    /** 녹음해서 기준을 넘으면 저장한다. 0.5초 미만이거나 음량이 작으면 저장하지 않고 다시 받는다(기준값: sdk-notes/Voice.md). */
    suspend fun record(cardId: String): TakeResult {
        if (consent.value == null) return TakeResult.NO_CONSENT
        dir.mkdirs()
        try {
            val result = pleos.record(take.path, MAX_RECORD_MS)
            val outcome =
                when {
                    result !is RecordResult.Done -> TakeResult.FAILED
                    result.durationMs < MIN_RECORD_MS || result.maxAmplitude < MIN_AMPLITUDE -> TakeResult.TRY_LOUDER
                    take.renameTo(fileOf(cardId)) -> TakeResult.SAVED
                    else -> TakeResult.FAILED
                }
            val detail = (result as? RecordResult.Done)?.let { " ms=${it.durationMs} amp=${it.maxAmplitude}" }.orEmpty()
            log("CARD record id=$cardId result=$outcome$detail")
            return outcome
        } finally {
            take.delete()
            cards.value = scan()
        }
    }

    fun stop() = pleos.stopRecording()

    fun delete(cardId: String) {
        fileOf(cardId).delete()
        cards.value = scan()
        log("CARD delete id=$cardId")
    }

    /** 녹음한 카드 파일. 이용 중 카드 찾기(di)가 샘플보다 먼저 본다. 없으면 null. */
    fun recordedFile(cardId: String): File? = fileOf(cardId).takeIf { it.exists() }

    fun deleteAll() {
        recorded.value.forEach { fileOf(it).delete() }
        take.delete()
        cards.value = scan()
        log("CARD delete all")
    }

    private fun fileOf(cardId: String) = File(dir, "$cardId.m4a")

    private fun scan(): Set<String> =
        dir
            .listFiles { file -> file.name.endsWith(".m4a") && !file.name.startsWith(".") }
            .orEmpty()
            .map { it.name.removeSuffix(".m4a") }
            .toSet()

    companion object {
        const val MAX_RECORD_MS = 8_000L
        const val MIN_RECORD_MS = 500L
        const val MIN_AMPLITUDE = 1_000
    }
}
