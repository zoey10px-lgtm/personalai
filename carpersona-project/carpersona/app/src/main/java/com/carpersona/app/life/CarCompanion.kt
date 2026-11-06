package com.carpersona.app.life

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import com.carpersona.app.ai.Brain
import com.carpersona.app.ai.ChatLine
import com.carpersona.app.ai.ClaudeBrain
import com.carpersona.app.ai.RuleBrain
import com.carpersona.app.navi.NaviSource
import com.carpersona.app.navi.Place
import com.carpersona.app.navi.Regions
import com.carpersona.app.persona.CarLife
import com.carpersona.app.persona.Celebrations
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.Intent
import com.carpersona.app.persona.MoodEngine
import com.carpersona.app.persona.PersonaEngine
import com.carpersona.app.persona.Register
import com.carpersona.app.persona.TalkContext
import com.carpersona.app.vehicle.CarSource
import java.util.concurrent.Executors

/**
 * 내 차와 운전자 사이를 잇는 "동반자".
 *  - 1초마다 차 상태·내비를 보고 주행 시작/끝을 알아챔 → 끝나면 PersonaEngine 으로 성격·기억 갱신
 *  - 차가 먼저 짧게 말을 거는 순간: 출발, 처음 가는 목적지, 배터리 부족, 경고, 도착, 성격 변화
 *  - 운전 중에는 말 길이를 줄이고, 경고가 아닌 선제 발화는 [TALK_GAP_MIN] 분에 한 번만
 */
class CarCompanion(
    private val car: CarSource,
    private val navi: NaviSource,
    private val store: LifeStore,
    /** 말하기 (말투별 목소리) */
    private val speak: (String, Register) -> Unit,
    /** 기념일 알림음 (말투별 소리) */
    private val chime: (Register) -> Unit = {},
    /** 알림음 길이(ms). 이만큼 기다렸다가 말함 */
    private val chimeMs: (Register) -> Long = { 0L },
) {
    companion object {
        private const val TAG = "CarPersona/Companion"
        /** 이만큼(분) 차 기록이 안 늘면 주행이 끝난 것으로 봄 */
        const val STOP_MIN = 3
        /** 경고가 아닌 선제 발화 사이 최소 간격(분) */
        const val TALK_GAP_MIN = 5
    }

    val chat = mutableStateListOf<ChatLine>()
    val thinking = mutableStateOf(false)
    val driving = mutableStateOf(false)

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var running = false

    private var lastTripKm: Float? = null
    private var lastTripMin: Float? = null
    private var lastMoveAt = 0L
    private var tripStartAt = 0L
    private var tripStartKm = 0f
    private var tripStartMin = 0f
    private var tripDestination: Place? = null
    private var lastDestKey: String? = null
    private var lastEnergy: Float? = null
    private var lastFacts: List<String> = emptyList()
    private var lastTalkAt = 0L
    private var handledArrival: Long? = null

    fun start() {
        if (running) return
        running = true
        handledArrival = navi.state.value.arrivedAt
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private val tick = object : Runnable {
        override fun run() {
            runCatching { step() }.onFailure { Log.w(TAG, "step 실패", it) }
            if (running) handler.postDelayed(this, 1000)
        }
    }

    /** 지금 상황으로 대화 맥락 만들기 */
    fun context(life: CarLife = store.life.value!!): TalkContext {
        val now = AppClock.now()
        val snap = car.snapshot.value
        val dest = navi.state.value.destination
        val visits = dest?.let { life.visitedPlaces[Regions.placeKey(it)] } ?: 0
        val mood = MoodEngine.of(snap, newDestination = dest != null && visits == 0, driving = driving.value)
        return TalkContext(life, snap, mood, dest, visits, driving.value, now)
    }

    /** 사용자의 질문 (음성 또는 글자) */
    fun ask(question: String) {
        if (question.isBlank() || store.life.value == null) return
        val ctx = context()
        val history = chat.toList()
        chat += ChatLine(fromCar = false, text = question)
        thinking.value = true
        val brain: Brain = if (store.aiOn && store.apiKey.isNotBlank()) claudeFor(store.apiKey) else RuleBrain
        worker.execute {
            val r = brain.reply(ctx, question, history)
            handler.post {
                thinking.value = false
                chat += ChatLine(fromCar = true, text = r.text, byAi = r.byAi)
                speak(r.text, ctx.life.register)
                // 성격 변화 이야기를 했으면 다시 꺼내지 않음
                if (Dialogue.intentOf(question) == Intent.MEMORY) clearPending()
            }
        }
    }

    private var claude: ClaudeBrain? = null
    private var claudeKey = ""
    private fun claudeFor(key: String): ClaudeBrain {
        if (claude == null || claudeKey != key) { claude = ClaudeBrain(key); claudeKey = key }
        return claude!!
    }

    /** 차가 먼저 말하기 */
    private fun carSays(text: String, important: Boolean = false) {
        val now = AppClock.now()
        if (!important && driving.value && now - lastTalkAt < TALK_GAP_MIN * 60_000L) return
        lastTalkAt = now
        chat += ChatLine(fromCar = true, text = text)
        store.life.value?.let { speak(text, it.register) }
    }

    private fun clearPending() {
        store.life.value?.takeIf { it.pendingTalk != null }?.let { store.save(it.copy(pendingTalk = null)) }
    }

    private fun step() {
        celebrate()
        val life = store.life.value ?: return
        val snap = car.snapshot.value
        val now = AppClock.now()

        // ── 주행 중인지: 이번 주행 거리/시간이 늘고 있으면 주행 중 (속도·기어 API가 없어서)
        val km = snap.tripDistanceKm
        val min = snap.tripTimeMin
        val moved = (km != null && lastTripKm != null && km > lastTripKm!! + 0.01f) ||
            (min != null && lastTripMin != null && min > lastTripMin!!)
        if (moved) lastMoveAt = now
        lastTripKm = km; lastTripMin = min
        val nowDriving = lastMoveAt > 0 && now - lastMoveAt < STOP_MIN * 60_000L

        if (nowDriving && !driving.value) {
            driving.value = true
            tripStartAt = now
            tripStartKm = km ?: 0f
            tripStartMin = min ?: 0f
            tripDestination = navi.state.value.destination
            lastDestKey = tripDestination?.let { Regions.placeKey(it) }
            carSays(Dialogue.onTripStart(context(life)), important = true)
            clearPending()
        }

        // ── 목적지가 바뀜 → 처음 가는 곳이면 설렘
        val dest = navi.state.value.destination
        val destKey = dest?.let { Regions.placeKey(it) }
        if (destKey != null && destKey != lastDestKey) {
            lastDestKey = destKey
            if (driving.value) {
                tripDestination = dest
                carSays(Dialogue.limit(context(life), Dialogue.compose(context(life), Intent.WHERE)))
            }
        }

        // ── 배터리/연료가 낮아지는 순간
        val e = snap.energyPercent
        if (e != null && lastEnergy != null && lastEnergy!! > MoodEngine.LOW_ENERGY && e <= MoodEngine.LOW_ENERGY) {
            carSays(Dialogue.limit(context(life), Dialogue.compose(context(life), Intent.MOOD)), important = true)
        }
        lastEnergy = e

        // ── 새 경고 → 정확한 정보 먼저
        val facts = MoodEngine.warnings(snap)
        if (facts.any { it !in lastFacts }) {
            carSays(Dialogue.compose(context(life), Intent.CHECK), important = true)
        }
        lastFacts = facts

        // ── 주행 끝: 내비 도착 알림 또는 3분 동안 기록이 안 늘어남
        val arrived = navi.state.value.arrivedAt
        val arrivalNew = arrived != null && arrived != handledArrival && driving.value
        if (driving.value && (arrivalNew || !nowDriving)) {
            handledArrival = arrived
            endTrip(life, snap.odometerKm, km, min)
        }
    }

    private fun endTrip(life: CarLife, odo: Float?, km: Float?, min: Float?) {
        driving.value = false
        val now = AppClock.now()
        val tripKm = ((km ?: 0f) - tripStartKm).let { if (it > 0) it else km ?: 0f }
        val tripMin = ((min ?: 0f) - tripStartMin).let { if (it > 0) it else min ?: 0f }
        if (tripKm < 0.3f) return
        // 장소: 위치 저장에 동의했을 때만 (목적지 → 없으면 현재 위치)
        val place = if (store.locationConsent) (tripDestination ?: navi.state.value.currentLocation) else null
        val region = place?.lat?.let { lat -> place.lon?.let { lon -> Regions.nameOf(lat, lon) } }
        val next = PersonaEngine.afterTrip(
            life, now, tripKm, tripMin,
            place?.let { Regions.placeKey(it) }, place?.name, region, odo,
        )
        store.save(next)
        val trip = next.trips.last()
        carSays(Dialogue.onArrive(context(next).copy(driving = false), trip), important = true)
    }

    /**
     * 기념일 확인 (1초마다): 1만 km를 넘었거나 출고 기념일이면 알림음 → 차가 먼저 말함.
     * 기념일 알림은 운전 중에도 하지만 한 문장 + 짧은 덧붙임으로 끝나고, 화면 조작은 필요 없습니다.
     */
    private fun celebrate() {
        val life = store.life.value ?: return
        val now = AppClock.now()
        val (next, events) = Celebrations.check(life, car.snapshot.value.odometerKm, now)
        if (next != life) store.save(next)
        events.filter { it.speak }.forEachIndexed { i, e ->
            // 대사는 축하 전 삶으로 만듦 (직전 1만 km 날짜 등)
            val text = Celebrations.line(life, e, now, driving.value)
            val r = life.register
            handler.postDelayed({
                chat += ChatLine(fromCar = true, text = (if (e.kind == Celebrations.Kind.TEN_K) "🎉 " else "🎂 ") + text)
                chime(r)
                handler.postDelayed({ speak(text, r) }, chimeMs(r))
            }, i * 9_000L)
            lastTalkAt = now
        }
    }

    /** 시뮬레이터용: 출고일이 없으면 내일로 정하고, 차 안 시계를 다음 출고 기념일 아침 9시 1분 전으로 옮김 */
    fun jumpToBirthday() {
        var life = store.life.value ?: return
        val now = AppClock.now()
        if (life.releaseDate == null) {
            val c = java.util.Calendar.getInstance().apply {
                timeInMillis = now; add(java.util.Calendar.DAY_OF_MONTH, 1); set(java.util.Calendar.YEAR, life.modelYear)
                set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            }
            life = life.copy(releaseDate = c.timeInMillis)
            life = life.copy(celebratedAge = life.ageYears(now))
            store.save(life)
        }
        val next = Celebrations.nextAnniversary(life, now) ?: return
        AppClock.simulatedMillis = next + 9 * 3_600_000L - 60_000L
    }

    /** 시뮬레이터용: 주행 기록을 바로 추가 (빨리 감기) */
    fun addTripNow(place: Place?, km: Float) {
        val life = store.life.value ?: return
        val p = if (store.locationConsent) place else null
        val region = p?.lat?.let { lat -> p.lon?.let { lon -> Regions.nameOf(lat, lon) } }
        store.save(PersonaEngine.afterTrip(life, AppClock.now(), km, km, p?.let { Regions.placeKey(it) }, p?.name, region, null))
    }

    /** 빨리 감기가 끝난 뒤 차가 변화를 이야기 */
    fun talkAboutChange() {
        val life = store.life.value ?: return
        val ctx = context(life)
        carSays(Dialogue.compose(ctx, Intent.MEMORY), important = true)
        clearPending()
    }
}
