package com.carpersona.app.vehicle

import androidx.compose.runtime.State

/**
 * 차 상태 한 장면(스냅샷).
 *
 * 모두 Pleos Vehicle SDK의 "조회(get)" API로 읽을 수 있는 값입니다.
 * 값을 못 읽었으면 null (예: 이 차에는 그 센서가 없음).
 * 차를 "조종"하는 값은 하나도 없습니다.
 */
data class CarSnapshot(
    /** Odometer.getCurrentDistanceDriven : 총 누적 주행거리 (km) → 노련함 */
    val odometerKm: Float? = null,
    /** Odometer.getTripCurrentDrivingDistance : 이번 주행 거리 (km) */
    val tripDistanceKm: Float? = null,
    /** Odometer.getTripCurrentDrivingTime : 이번 주행 시간 (분) */
    val tripTimeMin: Float? = null,
    /** CarInfo.getVINInfo : 차대번호. 10번째 글자가 연식(모델 연도) */
    val vin: String? = null,
    /** EvBattery.getEvBatteryStateOfCharge 또는 Fuel.getFuelPercent (%) → 기분(배고픔) */
    val energyPercent: Float? = null,
    /** 전기차면 true (배터리), 아니면 false (연료) */
    val isEv: Boolean? = null,
    /** Tire.getLowTirePressure */
    val lowTirePressure: Boolean? = null,
    /** Safety.hasLowWasherFluidWarning */
    val lowWasherFluid: Boolean? = null,
    /** Safety.getEngineOilLevelState (LOW 등) */
    val engineOilLow: Boolean? = null,
    /** Safety.getBrakeFluidLevelState (LOW 등) */
    val brakeFluidLow: Boolean? = null,
    /** HVAC.getHvacTemperatureCurrent : 실내 온도 */
    val cabinTempC: Float? = null,
    /** Wiper.getCurrentWiperState : 비 */
    val wiperOn: Boolean? = null,
    /** DrivingMode.getCurrentDrivingModeState */
    val drivingMode: String? = null,
    /** "차량 데이터" 탭에 그대로 보여 줄 원본 값 (API 이름 → 값) */
    val raw: Map<String, String> = emptyMap(),
) {
    val energyWord: String get() = if (isEv == false) "연료" else "배터리"
}

/**
 * 차에서 값을 읽어 오는 창구.
 *  - FakeCar  : 일반 폰/에뮬레이터용 가짜 차 (시뮬레이터 탭으로 조작)
 *  - PleosCar : Pleos 에뮬레이터·실차용 (Vehicle SDK 조회 API)
 */
interface CarSource {
    val snapshot: State<CarSnapshot>
    val label: String
    val isReal: Boolean
    fun start()
    fun stop()
}
