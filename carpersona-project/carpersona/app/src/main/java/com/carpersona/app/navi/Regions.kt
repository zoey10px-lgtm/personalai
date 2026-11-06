package com.carpersona.app.navi

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 좌표 → 지역 이름.
 * 위치 SDK(FusedLocation)가 아직 준비 중이라, 내비 목적지 좌표만으로 "어느 지역에 다녀왔는지" 를 셉니다.
 * 시·군 중심 좌표(근사값) 중 가장 가까운 곳으로 이름을 붙입니다.
 */
object Regions {
    private val cities = listOf(
        "서울" to (37.5665 to 126.9780), "인천" to (37.4563 to 126.7052), "수원" to (37.2636 to 127.0286),
        "성남" to (37.4200 to 127.1267), "고양" to (37.6584 to 126.8320), "용인" to (37.2411 to 127.1776),
        "가평" to (37.8315 to 127.5105), "춘천" to (37.8813 to 127.7298), "원주" to (37.3422 to 127.9202),
        "강릉" to (37.7519 to 128.8761), "속초" to (38.2070 to 128.5918), "평창" to (37.3705 to 128.3903),
        "청주" to (36.6424 to 127.4890), "충주" to (36.9910 to 127.9259), "대전" to (36.3504 to 127.3845),
        "천안" to (36.8151 to 127.1139), "공주" to (36.4465 to 127.1190), "태안" to (36.7456 to 126.2980),
        "전주" to (35.8242 to 127.1480), "군산" to (35.9676 to 126.7366), "광주" to (35.1595 to 126.8526),
        "목포" to (34.8118 to 126.3922), "여수" to (34.7604 to 127.6622), "순천" to (34.9507 to 127.4872),
        "대구" to (35.8714 to 128.6014), "안동" to (36.5684 to 128.7294), "경주" to (35.8562 to 129.2247),
        "포항" to (36.0190 to 129.3435), "울산" to (35.5384 to 129.3114), "부산" to (35.1796 to 129.0756),
        "창원" to (35.2281 to 128.6811), "통영" to (34.8544 to 128.4331), "제주" to (33.4996 to 126.5312),
        "서귀포" to (33.2541 to 126.5600),
    )

    /** 가장 가까운 시·군 이름 (40km 밖이면 "격자" 이름) */
    fun nameOf(lat: Double, lon: Double): String {
        val best = cities.minBy { (_, c) -> km(lat, lon, c.first, c.second) }
        return if (km(lat, lon, best.second.first, best.second.second) <= 40) best.first
        else "%.1f,%.1f 부근".format(lat, lon)
    }

    /** 같은 장소인지 판단할 열쇠 (좌표를 약 1km 격자로 반올림) */
    fun placeKey(p: Place): String =
        if (p.lat != null && p.lon != null) "%.2f,%.2f".format(p.lat, p.lon) else "name:${p.name}"

    fun km(a: Place, b: Place): Double =
        if (a.lat == null || a.lon == null || b.lat == null || b.lon == null) 0.0 else km(a.lat, a.lon, b.lat, b.lon)

    fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }
}
