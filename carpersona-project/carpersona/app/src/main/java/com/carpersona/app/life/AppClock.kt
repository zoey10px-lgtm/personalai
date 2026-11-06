package com.carpersona.app.life

/**
 * 앱 전체가 쓰는 "지금 몇 시".
 * 실제 차: 차량 시스템 시계. 가짜 차 시뮬레이터: 1초 = 1분으로 빨리 흐르는 시계.
 */
object AppClock {
    @Volatile
    var simulatedMillis: Long? = null

    fun now(): Long = simulatedMillis ?: System.currentTimeMillis()

    fun useRealClock() { simulatedMillis = null }

    fun advance(ms: Long) {
        simulatedMillis = (simulatedMillis ?: System.currentTimeMillis()) + ms
    }

    const val DAY = 24L * 60 * 60 * 1000
}
