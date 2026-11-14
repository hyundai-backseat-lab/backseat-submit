package io.backseatlab.playground.ui.theme

import androidx.compose.ui.unit.dp

/** 화면 공통 간격. 가장자리 여백은 [Space.edge]. */
object Space {
    val xs = 8.dp
    val s = 12.dp
    val m = 20.dp
    val l = 28.dp
    val xl = 40.dp
    val edge = 56.dp
}

/** 이 폭보다 좁으면 마스코트와 내용을 가로 대신 세로로 쌓는다. */
val WideLayout = 1000.dp
