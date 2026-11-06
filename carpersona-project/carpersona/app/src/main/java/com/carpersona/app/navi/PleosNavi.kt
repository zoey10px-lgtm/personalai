package com.carpersona.app.navi

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.carpersona.app.life.AppClock
import com.carpersona.app.vehicle.PleosReflect

/**
 * Pleos NaviHelper SDK 연결부 (조회·이벤트만).
 *   NaviHelper(context).initialize(onConnected, onFailed)
 *   addListener(NaviHelperEventListener) → onRouteStarted, onDestinationArrived, onDestinationInfo, onCurrentLocationInfo ...
 *   getDestinationInfo() → onDestinationInfo
 * 목적지 좌표로 "처음 가는 곳인지", "어느 지역인지" 를 판단합니다 (모험심의 재료).
 */
class PleosNavi(context: Context) : NaviSource {

    override val label = "Pleos 내비"
    private val appContext = context.applicationContext
    private val _state = mutableStateOf(NaviState())
    override val state: State<NaviState> = _state

    val connected = mutableStateOf(false)
    private var helper: Any? = null
    private var listener: Any? = null
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "CarPersona/PleosNavi"
        private const val HELPER = "ai.pleos.playground.navi.helper.NaviHelper"
        private const val LISTENER = "ai.pleos.playground.navi.helper.listener.NaviHelperEventListener"
    }

    override fun start() {
        val cls = PleosReflect.classOrNull(HELPER) ?: return
        try {
            val h = cls.java.getConstructor(Context::class.java).newInstance(appContext)
            helper = h
            listener = PleosReflect.listener(LISTENER, ::onEvent)
            val withCallbacks = PleosReflect.functionsOf(h).firstOrNull { it.name == "initialize" && it.parameters.size >= 3 }
            val init = PleosReflect.findFunction(h, "initialize")
            if (withCallbacks != null) {
                PleosReflect.get(h, withCallbacks, null, onValue = { onConnected() }, onError = { Log.w(TAG, "연결 실패: $it") })
            } else if (init != null) {
                init.call(h); onConnected()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "NaviHelper 시작 실패: ${t.message}")
        }
    }

    private fun onConnected() {
        val h = helper ?: return
        connected.value = true
        runCatching { h.javaClass.methods.first { it.name == "addListener" }.invoke(h, listener) }
        askDestination()
        handler.post(locationPoll)
    }

    /** 30초마다 현재 위치를 물어봄 → onCurrentLocationInfo (목적지 없이 달린 주행의 도착 지역용) */
    private val locationPoll = object : Runnable {
        override fun run() {
            val h = helper ?: return
            runCatching {
                h.javaClass.methods.firstOrNull {
                    it.parameterCount == 0 && it.name.contains("CurrentLocation") &&
                        (it.name.startsWith("get") || it.name.startsWith("request"))
                }?.invoke(h)
            }
            handler.postDelayed(this, 30_000)
        }
    }

    fun askDestination() {
        val h = helper ?: return
        runCatching { h.javaClass.methods.first { it.name == "getDestinationInfo" && it.parameterCount == 0 }.invoke(h) }
    }

    private fun onEvent(name: String, args: List<Any?>) {
        Log.d(TAG, "event $name $args")
        when (name) {
            "onRouteStarted", "onDestinationInfo", "onDestinationChanged" -> {
                placeFrom(args.firstOrNull())?.let { _state.value = _state.value.copy(destination = it) }
                if (name != "onDestinationInfo") askDestination()
            }
            "onDestinationArrived" -> _state.value = _state.value.copy(arrivedAt = AppClock.now())
            "onCurrentLocationInfo" -> placeFrom(args.firstOrNull())?.let { _state.value = _state.value.copy(currentLocation = it) }
            "onRouteCancelled" -> _state.value = _state.value.copy(destination = null)
        }
    }

    private fun placeFrom(info: Any?): Place? {
        info ?: return null
        val name = PleosReflect.prop(info, "poiName", "name", "destinationName", "title")?.toString()
        val lat = PleosReflect.prop(info, "latitude", "lat")?.toString()?.toDoubleOrNull()
        val lon = PleosReflect.prop(info, "longitude", "lon", "lng")?.toString()?.toDoubleOrNull()
        if (name.isNullOrBlank() && lat == null) return null
        return Place(name?.takeIf { it.isNotBlank() } ?: "목적지", lat, lon)
    }

    override fun stop() {
        handler.removeCallbacks(locationPoll)
        val h = helper ?: return
        runCatching { h.javaClass.methods.first { it.name == "removeListener" }.invoke(h, listener) }
        runCatching { h.javaClass.getMethod("release").invoke(h) }
        helper = null
    }
}
