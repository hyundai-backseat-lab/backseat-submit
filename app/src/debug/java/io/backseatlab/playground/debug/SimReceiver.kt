package io.backseatlab.playground.debug

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import io.backseatlab.playground.App

/** `scripts/sim.sh`가 보내는 브로드캐스트(`--es event brake-on`)를 받아 가짜 차량 신호·인식 결과를 반영한다. */
class SimReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val container = (context.applicationContext as App).container
        val sim = container.pleos as? SimPleosClient ?: return
        when (val event = intent.getStringExtra("event")) {
            null -> Unit
            // sim.sh start: 화면의 시작 버튼과 같은 길로 시작을 요청한다(주차·권한·음성 준비 확인 포함)
            "start" -> {
                val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                container.session.requestStart(micGranted = mic == PackageManager.PERMISSION_GRANTED)
            }
            // sim.sh stt "<문장>": 인식 결과를 주입한다
            "stt" -> intent.getStringExtra("text")?.let { sim.inject(it) }
            else -> sim.send(event)
        }
    }
}
