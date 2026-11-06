package com.carpersona.app.vehicle

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlin.reflect.KFunction

/**
 * 진짜 차 (Pleos Vehicle SDK, 조회 API만 사용).
 * 2초마다 필요한 값을 get 으로 읽어 CarSnapshot 을 채웁니다.
 *
 * 인격에 쓰는 API (모두 조회):
 *   Odometer.getCurrentDistanceDriven (누적 주행거리 → 노련함)
 *   Odometer.getTripCurrentDrivingDistance / getTripCurrentDrivingTime (주행 감지·주행 기록)
 *   CarInfo.getVINInfo (차대번호 → 연식)
 *   EvBattery.getEvBatteryStateOfCharge / Fuel.getFuelPercent (기분: 배고픔)
 *   Tire.getLowTirePressure, Safety.hasLowWasherFluidWarning,
 *   Safety.getEngineOilLevelState, Safety.getBrakeFluidLevelState (기분: 불편함 + 정확한 경고)
 *   HVAC.getHvacTemperatureCurrent, Wiper.getCurrentWiperState, DrivingMode.getCurrentDrivingModeState (대화 소재)
 */
class PleosCar(context: Context) : CarSource {

    override val label = "Pleos 차량"
    override val isReal = true

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val _snapshot = mutableStateOf(CarSnapshot())
    override val snapshot: State<CarSnapshot> = _snapshot

    val receivedAny = mutableStateOf(false)

    private var vehicle: Any? = null
    private var running = false

    private class Probe(val key: String, val domain: Any, val fn: KFunction<*>, val areas: List<String?>)
    private val probes = mutableListOf<Probe>()
    private val values = mutableMapOf<String, MutableMap<String?, Any?>>()

    companion object {
        private const val TAG = "CarPersona/PleosCar"
        private const val VEHICLE_CLASS = "ai.pleos.playground.vehicle.Vehicle"
        fun sdkInApk(): Boolean = PleosReflect.classOrNull(VEHICLE_CLASS) != null
    }

    fun connect(): Boolean = try {
        val cls = Class.forName(VEHICLE_CLASS)
        val v = cls.getConstructor(Context::class.java).newInstance(appContext)
        cls.getMethod("initialize").invoke(v)
        vehicle = v
        buildProbes(v)
        Log.i(TAG, "Pleos 연결, 읽을 항목 ${probes.size}개: ${probes.map { it.key }}")
        probes.isNotEmpty()
    } catch (t: Throwable) {
        Log.w(TAG, "Pleos 연결 실패: ${t.cause?.message ?: t.message}")
        false
    }

    private fun domain(v: Any, getter: String): Any? = runCatching { v.javaClass.getMethod(getter).invoke(v) }.getOrNull()

    private fun add(key: String, domain: Any?, name: String, areaFilter: ((String) -> Boolean)? = null) {
        domain ?: return
        val fn = PleosReflect.findFunction(domain, name) ?: run { Log.i(TAG, "이 SDK에 $name 없음"); return }
        val all = PleosReflect.areaNames(fn)
        val areas: List<String?> = when {
            all.isEmpty() -> listOf(null)
            areaFilter == null -> listOf(all.first())
            else -> all.filter(areaFilter).ifEmpty { listOf(all.first()) }
        }
        probes += Probe(key, domain, fn, areas)
    }

    private fun buildProbes(v: Any) {
        probes.clear()
        val odo = domain(v, "getOdometer")
        add("odometer", odo, "getCurrentDistanceDriven")
        add("tripKm", odo, "getTripCurrentDrivingDistance")
        add("tripMin", odo, "getTripCurrentDrivingTime")
        add("vin", domain(v, "getCarInfo"), "getVINInfo")
        add("evSoc", domain(v, "getEvBattery"), "getEvBatteryStateOfCharge")
        add("fuel", domain(v, "getFuel"), "getFuelPercent")
        add("tire", domain(v, "getTire"), "getLowTirePressure", { true })
        val safety = domain(v, "getSafety")
        add("washer", safety, "hasLowWasherFluidWarning")
        add("oil", safety, "getEngineOilLevelState")
        add("brakeFluid", safety, "getBrakeFluidLevelState")
        add("cabinTemp", domain(v, "getHVAC"), "getHvacTemperatureCurrent", { it.contains("ROW_1_LEFT") || it.contains("FRONT_LEFT") })
        add("wiper", domain(v, "getWiper"), "getCurrentWiperState", { it.contains("FRONT") })
        add("drivingMode", domain(v, "getDrivingMode"), "getCurrentDrivingModeState")
    }

    private val poll = object : Runnable {
        override fun run() {
            for (p in probes) for (area in p.areas) {
                PleosReflect.get(p.domain, p.fn, area, onValue = { value ->
                    values.getOrPut(p.key) { mutableMapOf() }[area] = value
                    receivedAny.value = true
                    publish()
                })
            }
            if (running) handler.postDelayed(this, 2000)
        }
    }

    override fun start() {
        if (running || vehicle == null) return
        running = true
        handler.post(poll)
    }

    override fun stop() {
        running = false
        handler.removeCallbacks(poll)
        vehicle?.let { v -> runCatching { v.javaClass.getMethod("release").invoke(v) } }
        vehicle = null
    }

    private fun one(key: String): Any? = values[key]?.values?.firstOrNull()

    private fun publish() {
        val raw = linkedMapOf<String, String>()
        for (p in probes) {
            val m = values[p.key] ?: continue
            raw[p.fn.name] = if (m.size == 1 && m.keys.first() == null) PleosReflect.text(m.values.first())
            else m.entries.joinToString { "${it.key}=${PleosReflect.text(it.value)}" }
        }
        val ev = PleosReflect.number(one("evSoc"))
        // 단위가 m 로 오는 경우 대비: 200만이 넘으면 m 로 보고 km 로 바꿈
        val odo = PleosReflect.number(one("odometer"))?.let { if (it > 2_000_000f) it / 1000f else it }
        val vin = one("vin")?.let { PleosReflect.prop(it, "vin", "vinInfo", "value")?.toString() ?: it.toString() }
        _snapshot.value = CarSnapshot(
            odometerKm = odo,
            tripDistanceKm = PleosReflect.number(one("tripKm")),
            tripTimeMin = PleosReflect.number(one("tripMin")),
            vin = vin,
            energyPercent = ev ?: PleosReflect.number(one("fuel")),
            isEv = if (ev != null) true else if (one("fuel") != null) false else null,
            lowTirePressure = values["tire"]?.values?.any { PleosReflect.isOn(it) == true },
            lowWasherFluid = PleosReflect.isOn(one("washer")),
            engineOilLow = one("oil")?.let { lowLevel(it) },
            brakeFluidLow = one("brakeFluid")?.let { lowLevel(it) },
            cabinTempC = PleosReflect.number(one("cabinTemp")),
            wiperOn = PleosReflect.isOn(one("wiper")),
            drivingMode = one("drivingMode")?.let { PleosReflect.text(it) },
            raw = raw,
        )
    }

    /** 액체 레벨 상태: 이름에 LOW/WARN 이 들어가면 부족 */
    private fun lowLevel(v: Any): Boolean {
        val s = PleosReflect.text(v).uppercase()
        return s.contains("LOW") || s.contains("WARN")
    }
}
