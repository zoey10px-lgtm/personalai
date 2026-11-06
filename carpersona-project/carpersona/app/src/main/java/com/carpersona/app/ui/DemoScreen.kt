package com.carpersona.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.ai.Brain
import com.carpersona.app.ai.Reply
import com.carpersona.app.life.AppClock
import com.carpersona.app.persona.CarLife
import com.carpersona.app.persona.Celebrations
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.Intent
import com.carpersona.app.persona.MoodEngine
import com.carpersona.app.persona.PersonaEngine
import com.carpersona.app.persona.Register
import com.carpersona.app.persona.SampleCars
import com.carpersona.app.persona.TalkContext
import com.carpersona.app.voice.Chime
import com.carpersona.app.voice.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 공모전 시연 3개 (가짜 차·진짜 차와 상관없이 언제나 동작).
 *  1) 같은 질문, 다른 두 차   2) 배터리가 변해도 성격은 그대로, 기분만 변함   3) 새 지역이 쌓이며 성격이 변함
 */
@Composable
fun DemoScreen(brain: Brain, voice: Voice, wide: Boolean) {
    var demo by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "① 같은 질문, 다른 두 차", 2 to "② 배터리와 기분", 3 to "③ 한 달 여행 후 변화", 4 to "④ 기념일").forEach { (n, label) ->
                if (demo == n) Button(onClick = { demo = n }) { Text(label, fontSize = 15.sp) }
                else OutlinedButton(onClick = { demo = n }) { Text(label, fontSize = 15.sp) }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (demo) {
            1 -> SameQuestion(brain, voice, wide)
            2 -> BatteryMood(voice, wide)
            3 -> MonthOfTravel(voice, wide)
            else -> Anniversary(voice, wide)
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun demoCtx(life: CarLife, energy: Float, tire: Boolean, now: Long): TalkContext {
    val snap = SampleCars.snapshot(energy, tire, life.odometerKm)
    return TalkContext(life, snap, MoodEngine.of(snap, false, false), null, 0, false, now)
}

@Composable
private fun Pair2(wide: Boolean, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    if (wide) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) { left() }
        Column(Modifier.weight(1f)) { right() }
    } else Column { left(); Spacer(Modifier.height(12.dp)); right() }
}

// ───────── 시연 1 ─────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SameQuestion(brain: Brain, voice: Voice, wide: Boolean) {
    val now = remember { System.currentTimeMillis() }
    val a = remember { SampleCars.carA(now) }
    val b = remember { SampleCars.carB(now) }
    var question by remember { mutableStateOf<String?>(null) }
    var answers by remember { mutableStateOf<Pair<Reply?, Reply?>>(null to null) }
    val scope = rememberCoroutineScope()
    Text("같은 모델의 차 두 대. 한 대는 여기저기 여행을 다녔고, 한 대는 10년 동안 출퇴근만 했어요. 같은 질문을 해 보세요.",
        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(Modifier.padding(vertical = 8.dp)) {
        QUICK_QUESTIONS.forEach { q ->
            OutlinedButton(onClick = {
                question = q
                answers = null to null
                scope.launch {
                    val ra = async(Dispatchers.IO) { brain.reply(demoCtx(a, 70f, false, now), q, emptyList()) }
                    val rb = async(Dispatchers.IO) { brain.reply(demoCtx(b, 70f, false, now), q, emptyList()) }
                    answers = ra.await() to rb.await()
                    voice.speak(answers.first!!.text, a.register)
                    voice.speak(answers.second!!.text, b.register, queue = true)
                }
            }, modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)) { Text(q, fontSize = 15.sp) }
        }
    }
    question?.let { Bubble(it, fromCar = false) }
    Pair2(wide,
        { CarWithAnswer(a, "A차 · 같은 모델", demoCtx(a, 70f, false, now), now, answers.first) },
        { CarWithAnswer(b, "B차 · 같은 모델", demoCtx(b, 70f, false, now), now, answers.second) })
}

@Composable
private fun CarWithAnswer(life: CarLife, title: String, ctx: TalkContext, now: Long, reply: Reply?) {
    PersonaCard(life, ctx.mood, now, title = title)
    reply?.let { Bubble(it.text, true, if (it.byAi) "Claude AI" else it.note) }
}

// ───────── 시연 2 ─────────

@Composable
private fun BatteryMood(voice: Voice, wide: Boolean) {
    val now = remember { System.currentTimeMillis() }
    val a = remember { SampleCars.carA(now) }
    val b = remember { SampleCars.carB(now) }
    var energy by remember { mutableFloatStateOf(80f) }
    var tire by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("배터리를 줄여 보세요. 두 차의 성격 점수(막대)는 그대로이고, 기분과 말만 바뀌어요. 같은 \"배고픔\"도 성격에 따라 다르게 말해요.",
        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("배터리 ${energy.toInt()}%", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Slider(energy, { energy = it }, valueRange = 5f..100f, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = {
            scope.launch {
                tire = false
                for (v in 90 downTo 20 step 5) { energy = v.toFloat(); delay(250) }
                val ca = demoCtx(a, energy, false, now); val cb = demoCtx(b, energy, false, now)
                voice.speak(Dialogue.compose(ca, Intent.MOOD), a.register)
                voice.speak(Dialogue.compose(cb, Intent.MOOD), b.register, queue = true)
            }
        }) { Text("▶ 90% → 20% 자동으로 줄이기", fontSize = 15.sp) }
        Spacer(Modifier.padding(8.dp))
        Text("타이어 경고", fontSize = 15.sp)
        Switch(tire, { tire = it; if (it) voice.speak(Dialogue.compose(demoCtx(b, energy, true, now), Intent.CHECK), b.register) })
    }
    Spacer(Modifier.height(8.dp))
    val ca = demoCtx(a, energy, tire, now)
    val cb = demoCtx(b, energy, tire, now)
    Pair2(wide,
        { PersonaCard(a, ca.mood, now, title = "A차"); Bubble(Dialogue.compose(ca, Intent.MOOD), true) },
        { PersonaCard(b, cb.mood, now, title = "B차"); Bubble(Dialogue.compose(cb, Intent.MOOD), true) })
}

// ───────── 시연 3 ─────────

@Composable
private fun MonthOfTravel(voice: Voice, wide: Boolean) {
    val t0 = remember { System.currentTimeMillis() }
    val start = remember { SampleCars.carB(t0) }
    var life by remember { mutableStateOf(start) }
    var day by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    var finalLine by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val now = t0 + day * AppClock.DAY
    Text("매일 같은 길로 출퇴근하던 B차(${PersonaEngine.archetype(start.traits)})가 한 달 동안 여러 지역을 여행합니다. 하루에 0.4초씩 빨리 감아요.",
        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        Button(enabled = !running, onClick = {
            scope.launch {
                running = true; finalLine = null; life = start; day = 0
                for (d in 1..30) {
                    val next = withContext(Dispatchers.Default) { SampleCars.monthDay(life, d, t0) }
                    life = next; day = d
                    delay(400)
                }
                val ctx = demoCtx(life, 70f, false, t0 + 30 * AppClock.DAY)
                finalLine = Dialogue.compose(ctx, Intent.MEMORY)
                voice.speak(finalLine!!, life.register)
                running = false
            }
        }) { Text("▶ 한 달 여행 빨리 감기", fontSize = 16.sp) }
        Spacer(Modifier.padding(8.dp))
        OutlinedButton(enabled = !running, onClick = { life = start; day = 0; finalLine = null }) { Text("처음으로") }
        Spacer(Modifier.padding(8.dp))
        Text(if (day == 0) "여행 전" else "${day}일째 · 다녀 본 지역 ${life.visitedRegions.size}곳", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
    val ctx = demoCtx(life, 70f, false, now)
    Pair2(wide,
        {
            PersonaCard(life, ctx.mood, now, title = "B차 (처음: ${PersonaEngine.archetype(start.traits)})")
            finalLine?.let { Bubble(it, true) }
        },
        {
            Card {
                SectionTitle("성격 변화 그래프 (최근 주행 기준)")
                TraitChart(life.history.takeLast(80))
                ChartLegend()
            }
            Spacer(Modifier.height(8.dp))
            Card {
                SectionTitle("새로 생긴 기억")
                life.memories.filter { it.at > t0 }.takeLast(5).asReversed().forEach { Text("${it.icon} ${it.text}", fontSize = 15.sp) }
            }
        })
}

// ───────── 시연 4: 기념일 ─────────

@Composable
private fun Anniversary(voice: Voice, wide: Boolean) {
    val now = remember { System.currentTimeMillis() }
    val a = remember { SampleCars.carA(now) }
    val b = remember { SampleCars.carB(now) }
    var lines by remember { mutableStateOf<Pair<String, String>?>(null) }
    val scope = rememberCoroutineScope()
    fun play(kind: Celebrations.Kind) {
        fun eventFor(l: CarLife) = Celebrations.Event(
            kind, if (kind == Celebrations.Kind.TEN_K) (l.odometerKm / 10_000).toInt() + 1 else l.ageYears(now) + 1, true)
        val la = Celebrations.line(a, eventFor(a), now)
        val lb = Celebrations.line(b, eventFor(b), now)
        lines = la to lb
        scope.launch {
            Chime.play(a.register); delay(Chime.durationMs(a.register))
            voice.speak(la, a.register)
            delay(1_500L + la.length * 110L)
            Chime.play(b.register); delay(Chime.durationMs(b.register))
            voice.speak(lb, b.register)
        }
    }
    Text("1만 km를 넘거나 출고 기념일이 되면 차가 알림음을 울리고 먼저 말해요. 알림음도, 말도 차의 성격마다 달라요.",
        fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { play(Celebrations.Kind.TEN_K) }) { Text("🎉 다음 1만 km 넘기", fontSize = 15.sp) }
        Button(onClick = { play(Celebrations.Kind.BIRTHDAY) }) { Text("🎂 출고 기념일", fontSize = 15.sp) }
    }
    Pair2(wide,
        { PersonaCard(a, demoCtx(a, 70f, false, now).mood, now, title = "A차 · 알림음: 띠리링!"); lines?.let { Bubble(it.first, true) } },
        { PersonaCard(b, demoCtx(b, 70f, false, now).mood, now, title = "B차 · 알림음: 댕-, 댕-"); lines?.let { Bubble(it.second, true) } })
}
