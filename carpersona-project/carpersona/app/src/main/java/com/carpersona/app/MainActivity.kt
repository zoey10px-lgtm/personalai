package com.carpersona.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.ai.ClaudeBrain
import com.carpersona.app.ai.RuleBrain
import com.carpersona.app.life.AppClock
import com.carpersona.app.life.CarCompanion
import com.carpersona.app.life.LifeStore
import com.carpersona.app.navi.NaviSource
import com.carpersona.app.navi.PleosNavi
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.Intent
import com.carpersona.app.persona.PersonaEngine
import com.carpersona.app.ui.CarDataScreen
import com.carpersona.app.ui.DemoScreen
import com.carpersona.app.ui.GrowthScreen
import com.carpersona.app.ui.Onboarding
import com.carpersona.app.ui.PersonaColors
import com.carpersona.app.ui.SettingsScreen
import com.carpersona.app.ui.SimScreen
import com.carpersona.app.ui.TalkScreen
import com.carpersona.app.vehicle.CarSource
import com.carpersona.app.vehicle.FakeCar
import com.carpersona.app.vehicle.PleosCar
import com.carpersona.app.voice.AndroidVoice
import com.carpersona.app.voice.Chime
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 앱의 시작점.
 *  1) Pleos SDK가 APK 안에 있고 차량 서비스에 연결되면 → 진짜 차 (PleosCar + PleosNavi)
 *  2) 아니면(일반 폰·일반 에뮬레이터) → 가짜 차 (FakeCar, 시뮬레이터 탭이 생김)
 *  진짜 차에 연결됐는데 6초 동안 값이 하나도 안 오면 자동으로 가짜 차로 바꿉니다.
 */
class MainActivity : ComponentActivity() {

    private class Setup(val car: CarSource, val navi: NaviSource, val companion: CarCompanion, val fake: FakeCar?)

    private lateinit var store: LifeStore
    private lateinit var voice: AndroidVoice
    private val setup = mutableStateOf<Setup?>(null)
    private val status = mutableStateOf<String?>(null)
    private val handler = Handler(Looper.getMainLooper())
    private var pendingMic = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LifeStore(this)
        voice = AndroidVoice(this).also { it.enabled = store.voiceOn }
        if (!tryPleos()) useFake("Pleos 차량 서비스가 없어 가짜 차로 실행 중이에요.")

        setContent {
            MaterialTheme(colorScheme = PersonaColors) {
                val s = setup.value ?: return@MaterialTheme
                App(s)
            }
        }
    }

    private fun tryPleos(): Boolean {
        if (!PleosCar.sdkInApk()) return false
        val car = PleosCar(this)
        if (!car.connect()) return false
        val navi = PleosNavi(this)
        replace(Setup(car, navi, companionFor(car, navi), null))
        status.value = "Pleos 차량에 연결했어요. 값을 기다리는 중…"
        handler.postDelayed({
            if (setup.value?.car === car) {
                if (car.receivedAny.value) status.value = "Pleos 차량 값을 받고 있어요."
                else useFake("Pleos 차량에서 6초 동안 값이 오지 않아 가짜 차로 바꿨어요.")
            }
        }, 6000)
        return true
    }

    private fun useFake(reason: String) {
        val fake = FakeCar()
        replace(Setup(fake, fake, companionFor(fake, fake), fake))
        status.value = reason
    }

    private fun companionFor(car: CarSource, navi: NaviSource) =
        CarCompanion(
            car, navi, store,
            speak = { text, register -> voice.speak(text, register) },
            chime = { if (store.voiceOn) Chime.play(it) },
            chimeMs = { if (store.voiceOn) Chime.durationMs(it) else 0L },
        )

    private fun replace(next: Setup) {
        setup.value?.let { old -> old.companion.stop(); old.car.stop(); old.navi.stop() }
        if (next.fake == null) AppClock.useRealClock()
        next.car.start(); next.navi.start(); next.companion.start()
        setup.value = next
    }

    /** 🎙 버튼: 마이크 권한 확인 → 듣기 → 질문 */
    private fun onMic(companion: CarCompanion) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            pendingMic = true
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        voice.listen(onText = { companion.ask(it) }, onError = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() })
    }

    @Deprecated("Activity API")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (pendingMic && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            setup.value?.let { onMic(it.companion) }
        }
        pendingMic = false
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        setup.value?.let { it.companion.stop(); it.car.stop(); it.navi.stop() }
        voice.release()
        super.onDestroy()
    }

    @Composable
    private fun App(s: Setup) {
        var tab by remember { mutableIntStateOf(0) }
        val nowMillis by produceState(AppClock.now()) { while (true) { value = AppClock.now(); delay(1000) } }
        val life = store.life.value
        val car = s.car.snapshot.value

        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()) {
            val wide = maxWidth > 700.dp
            if (life == null) {
                Onboarding(car) { name, year, odo, consent, release ->
                    store.locationConsent = consent
                    store.save(PersonaEngine.birth(name, year, car.odometerKm ?: odo, AppClock.now(), release))
                    // 깨어나서 첫 인사 (자기소개)
                    val ctx = s.companion.context()
                    val first = Dialogue.compose(ctx, Intent.WHO)
                    s.companion.chat += com.carpersona.app.ai.ChatLine(fromCar = true, text = first)
                    voice.speak(first, ctx.life.register)
                }
                return@BoxWithConstraints
            }
            val ctx = s.companion.context(life).copy(now = nowMillis)
            val aiReady = store.aiOn && store.apiKey.isNotBlank()
            val demoBrain = remember(aiReady, store.apiKey) { if (aiReady) ClaudeBrain(store.apiKey) else RuleBrain }
            val tabs = buildList {
                add("💬 대화"); add("🌱 성장 기록"); add("🎬 시연"); add("📊 차량 데이터")
                if (s.fake != null) add("🕹 시뮬레이터")
                add("⚙ 설정")
            }
            if (tab >= tabs.size) tab = 0
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("CAR PERSONA", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("  ${life.nickname}의 인격", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("${s.car.label} · ${SimpleDateFormat("HH:mm", Locale.KOREA).format(nowMillis)}", fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background, edgePadding = 12.dp) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 16.sp) }) }
                }
                when (tabs[tab]) {
                    "💬 대화" -> TalkScreen(ctx, s.companion, voice.listening.value, aiReady, wide) { onMic(s.companion) }
                    "🌱 성장 기록" -> GrowthScreen(life, nowMillis, wide)
                    "🎬 시연" -> DemoScreen(demoBrain, voice, wide)
                    "📊 차량 데이터" -> CarDataScreen(ctx, s.car.label, s.car.isReal, status.value,
                        onUseFake = { useFake("직접 가짜 차로 바꿨어요.") },
                        onTryPleos = { if (!tryPleos()) status.value = "Pleos 차량 서비스에 연결할 수 없어요 (일반 폰/에뮬레이터)." })
                    "🕹 시뮬레이터" -> s.fake?.let { SimScreen(it, car, s.companion, nowMillis) }
                    else -> SettingsScreen(store) { voice.enabled = it }
                }
            }
        }
    }
}
