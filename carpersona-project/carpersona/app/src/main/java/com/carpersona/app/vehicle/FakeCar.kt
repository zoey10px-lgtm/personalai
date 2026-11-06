package com.carpersona.app.vehicle

import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.carpersona.app.life.AppClock
import com.carpersona.app.navi.NaviSource
import com.carpersona.app.navi.NaviState
import com.carpersona.app.navi.Place
import com.carpersona.app.navi.Regions
import com.carpersona.app.persona.SampleCars
import kotlin.math.roundToInt

/**
 * 가짜 차 + 가짜 내비 (시뮬레이터).
 * 일반 폰·에뮬레이터에는 Pleos 차량 서비스가 없어서 이걸로 시연합니다.
 * 차 안 시계는 1초에 1분씩 흐르고, 주행은 거리와 상관없이 약 20초 만에 도착하도록 빨리 감습니다.
 */
class FakeCar : CarSource, NaviSource {

    override val label = "가짜 차 (시뮬레이터)"
    override val isReal = false

    private val handler = Handler(Looper.getMainLooper())
    private val _snapshot = mutableStateOf(
        CarSnapshot(
            odometerKm = 38_200f, tripDistanceKm = 0f, tripTimeMin = 0f,
            // 차대번호 10번째 글자 'P' = 2023년식
            vin = "KMHK381GFPU123456",
            energyPercent = 72f, isEv = true, lowTirePressure = false, lowWasherFluid = false,
            engineOilLow = false, brakeFluidLow = false, cabinTempC = 22f, wiperOn = false, drivingMode = "NORMAL",
        )
    )
    override val snapshot: State<CarSnapshot> = _snapshot

    private val _navi = mutableStateOf(NaviState(currentLocation = SampleCars.HOME))
    override val state: State<NaviState> = _navi

    val driving = mutableStateOf(false)
    val speedKmh = mutableStateOf(0)

    /** 고를 수 있는 목적지: 집·회사(출퇴근) + 여행지 */
    val places: List<Place> = listOf(SampleCars.HOME, SampleCars.WORK) + SampleCars.TRAVEL

    private var running = false
    private var routeKm = 0.0
    private var origin: Place = SampleCars.HOME

    private val tick = object : Runnable {
        override fun run() {
            step()
            if (running) handler.postDelayed(this, 1000)
        }
    }

    override fun start() {
        if (running) return
        if (AppClock.simulatedMillis == null) AppClock.simulatedMillis = System.currentTimeMillis()
        running = true
        handler.post(tick)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    // ───────── 시뮬레이터 버튼 ─────────

    fun depart(place: Place) {
        origin = _navi.value.currentLocation ?: SampleCars.HOME
        routeKm = (Regions.km(origin, place) * 1.3).coerceAtLeast(6.0)
        _navi.value = _navi.value.copy(destination = place)
        update { it.copy(tripDistanceKm = 0f, tripTimeMin = 0f) }
        driving.value = true
    }

    fun arrive() {
        if (!driving.value) return
        driving.value = false
        speedKmh.value = 0
        val d = _navi.value.destination
        _navi.value = _navi.value.copy(arrivedAt = AppClock.now(), currentLocation = d ?: _navi.value.currentLocation, destination = null)
    }

    fun addEnergy(delta: Float) = update { it.copy(energyPercent = ((it.energyPercent ?: 50f) + delta).coerceIn(1f, 100f)) }
    fun setTire(on: Boolean) = update { it.copy(lowTirePressure = on) }
    fun setWasher(on: Boolean) = update { it.copy(lowWasherFluid = on) }
    fun setOil(on: Boolean) = update { it.copy(engineOilLow = on) }
    fun setRain(on: Boolean) = update { it.copy(wiperOn = on) }
    fun setOdometer(km: Float) = update { it.copy(odometerKm = km) }

    // ───────── 내부 ─────────

    private fun step() {
        if (!driving.value) {
            AppClock.advance(60_000)
            update { it }
            return
        }
        // 약 20초 만에 도착하도록 1초당 달리는 거리를 정함 (최소 1.3km = 시속 80km로 1분)
        val km = (routeKm / 20).coerceAtLeast(1.3).toFloat()
        val minutes = km / 80f * 60f
        AppClock.advance((minutes * 60_000).toLong())
        speedKmh.value = 80
        val s = _snapshot.value
        val trip = (s.tripDistanceKm ?: 0f) + km
        val d = _navi.value.destination
        if (d?.lat != null && d.lon != null && origin.lat != null && origin.lon != null) {
            val t = (trip / routeKm).coerceIn(0.0, 1.0)
            _navi.value = _navi.value.copy(
                currentLocation = Place("현재 위치", origin.lat!! + (d.lat - origin.lat!!) * t, origin.lon!! + (d.lon - origin.lon!!) * t)
            )
        }
        update {
            it.copy(
                tripDistanceKm = trip,
                tripTimeMin = (it.tripTimeMin ?: 0f) + minutes,
                odometerKm = (it.odometerKm ?: 0f) + km,
                energyPercent = ((it.energyPercent ?: 50f) - km * 0.12f).coerceAtLeast(1f),
            )
        }
        if (trip >= routeKm) arrive()
    }

    private fun update(change: (CarSnapshot) -> CarSnapshot) {
        _snapshot.value = change(_snapshot.value).withRaw()
    }

    /** "차량 데이터" 탭에 보일 값 (실차에서 쓰는 API 이름과 같게) */
    private fun CarSnapshot.withRaw() = copy(
        raw = linkedMapOf(
            "Odometer.getCurrentDistanceDriven" to "${(odometerKm ?: 0f).roundToInt()} km",
            "Odometer.getTripCurrentDrivingDistance" to "%.1f km".format(tripDistanceKm ?: 0f),
            "Odometer.getTripCurrentDrivingTime" to "${(tripTimeMin ?: 0f).roundToInt()} 분",
            "CarInfo.getVINInfo" to (vin ?: "-"),
            "EvBattery.getEvBatteryStateOfCharge" to "%.0f %%".format(energyPercent ?: 0f),
            "Tire.getLowTirePressure" to "$lowTirePressure",
            "Safety.hasLowWasherFluidWarning" to "$lowWasherFluid",
            "Safety.getEngineOilLevelState" to if (engineOilLow == true) "LOW" else "NORMAL",
            "Safety.getBrakeFluidLevelState" to if (brakeFluidLow == true) "LOW" else "NORMAL",
            "HVAC.getHvacTemperatureCurrent" to "$cabinTempC ℃",
            "Wiper.getCurrentWiperState" to if (wiperOn == true) "LOW (작동)" else "OFF",
            "DrivingMode.getCurrentDrivingModeState" to (drivingMode ?: "-"),
        )
    )
}
