package com.carpersona.app.navi

import androidx.compose.runtime.State

/** 장소 (이름 + 좌표) */
data class Place(val name: String, val lat: Double? = null, val lon: Double? = null)

/** 내비에서 알고 있는 것 */
data class NaviState(
    /** 지금 안내 중인 목적지 (NaviHelper onRouteStarted / onDestinationInfo) */
    val destination: Place? = null,
    /** 도착 알림을 받은 시각 (NaviHelper onDestinationArrived) */
    val arrivedAt: Long? = null,
    /** 지금 차 위치 (NaviHelper onCurrentLocationInfo). 모르면 null */
    val currentLocation: Place? = null,
)

/**
 * 내비와 이야기하는 창구.
 *  - FakeCar   : 시뮬레이터용
 *  - PleosNavi : Pleos NaviHelper SDK
 */
interface NaviSource {
    val state: State<NaviState>
    val label: String
    fun start()
    fun stop()
}
