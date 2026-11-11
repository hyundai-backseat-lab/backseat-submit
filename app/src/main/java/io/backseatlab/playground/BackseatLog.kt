package io.backseatlab.playground

import android.util.Log

/** 모든 로그는 태그 `Backseat` 하나로 남긴다. verify 스크립트가 이 로그를 보고 판정한다. */
object BackseatLog {
    private const val TAG = "Backseat"

    fun i(message: String) {
        Log.i(TAG, message)
    }

    fun w(
        message: String,
        error: Throwable? = null,
    ) {
        Log.w(TAG, message, error)
    }
}
