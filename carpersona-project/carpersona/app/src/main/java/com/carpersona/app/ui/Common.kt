package com.carpersona.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.persona.CarLife
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.Mood
import com.carpersona.app.persona.PersonaEngine
import com.carpersona.app.persona.TraitPoint
import com.carpersona.app.persona.Traits
import kotlin.math.roundToInt

/** 차 화면에 어울리는 어두운 색 */
val PersonaColors = darkColorScheme(
    primary = Color(0xFF9EE6C9),        // 살아 있는 느낌의 민트
    onPrimary = Color(0xFF00382A),
    secondary = Color(0xFFFFC27A),
    background = Color(0xFF0E1215),
    surface = Color(0xFF0E1215),
    surfaceVariant = Color(0xFF1A2026),
    onSurface = Color(0xFFECEFF3),
    onSurfaceVariant = Color(0xFFA9B1BC),
    error = Color(0xFFFF7A7A),
)

/** 성격 네 축의 색 (모험심, 익숙함 사랑, 노련함, 활동성) */
val TraitColors = listOf(Color(0xFFFFA45B), Color(0xFF7AB8FF), Color(0xFFC9B6FF), Color(0xFF7DDC9A))
val Warn = Color(0xFFFFC857)

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
fun Chip(text: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Box(
        Modifier.padding(end = 8.dp, bottom = 8.dp).clip(RoundedCornerShape(50))
            .background(Color(0x22FFFFFF)).padding(horizontal = 14.dp, vertical = 7.dp)
    ) { Text(text, fontSize = 15.sp, color = color) }
}

/**
 * 살아 있는 얼굴: 숨 쉬듯 커졌다 작아짐. 주행 중·설렘이면 더 빨리 숨 쉼.
 */
@Composable
fun LivingFace(emoji: String, excited: Boolean, size: Int = 96) {
    val t = rememberInfiniteTransition(label = "breath")
    val s by t.animateFloat(
        initialValue = 0.94f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(if (excited) 600 else 1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        Modifier.size(size.dp).scale(s).clip(CircleShape).background(Color(0x1A9EE6C9)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = (size * 0.55).sp) }
}

/** 성격 네 축 막대 (값이 바뀌면 부드럽게 움직임) */
@Composable
fun TraitBars(t: Traits) {
    t.toList().forEachIndexed { i, v ->
        val anim by animateFloatAsState(v, tween(600), label = "trait$i")
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Traits.NAMES[i], fontSize = 15.sp, modifier = Modifier.width(92.dp))
            Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x22FFFFFF))) {
                Box(Modifier.fillMaxWidth(anim / 100f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(TraitColors[i]))
            }
            Text("${anim.roundToInt()}", fontSize = 15.sp, modifier = Modifier.width(40.dp).padding(start = 8.dp))
        }
    }
}

/** 차 한 대의 프로필 카드: 얼굴 + 성격 이름 + 연식·주행거리 + 기분 + 성격 막대 */
@Composable
fun PersonaCard(life: CarLife, mood: Mood, now: Long, excited: Boolean = false, title: String? = null) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivingFace(mood.kind.emoji, excited || mood.kind.name == "EXCITED", 84)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title ?: life.nickname, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(PersonaEngine.archetype(life.traits), fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary)
                Text("출고 ${life.ageYears(now)}년 차 · ${Dialogue.formatKm(life.odometerKm)} · ${life.register.label}",
                    fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))
        MoodLine(mood)
        Spacer(Modifier.height(10.dp))
        Text("장기적 성격 (주행 이력으로 계산)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TraitBars(life.traits)
    }
}

@Composable
fun MoodLine(mood: Mood) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("지금 기분 ", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Chip("${mood.kind.emoji} ${mood.kind.word} · ${mood.reason}",
            if (mood.facts.isNotEmpty()) Warn else MaterialTheme.colorScheme.onSurface)
    }
}

/** 말풍선 */
@Composable
fun Bubble(text: String, fromCar: Boolean, badge: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (!fromCar) Spacer(Modifier.weight(1f))
        Column(
            Modifier.clip(RoundedCornerShape(18.dp))
                .background(if (fromCar) Color(0xFF243039) else Color(0xFF2E3B2F))
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .fillMaxWidth(0.85f)
        ) {
            text.split("\n").forEach { line ->
                val fact = line.startsWith("[차량 정보]")
                Text(line, fontSize = if (fact) 16.sp else 19.sp, color = if (fact) Warn else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (fact) FontWeight.SemiBold else FontWeight.Normal)
            }
            badge?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** 성격 변화 그래프 (네 축 선) */
@Composable
fun TraitChart(points: List<TraitPoint>, modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        // 가로 눈금 (0, 50, 100)
        listOf(0f, 0.5f, 1f).forEach { y ->
            drawLine(Color(0x33FFFFFF), Offset(0f, h * y), Offset(w, h * y), 1f)
        }
        if (points.size < 2) return@Canvas
        for (axis in 0 until 4) {
            val path = androidx.compose.ui.graphics.Path()
            points.forEachIndexed { i, p ->
                val x = w * i / (points.size - 1)
                val y = h * (1f - p.traits.toList()[axis] / 100f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, TraitColors[axis], style = Stroke(width = if (axis == 0) 6f else 4f))
        }
    }
}

@Composable
fun ChartLegend() {
    Row(Modifier.padding(top = 6.dp)) {
        Traits.NAMES.forEachIndexed { i, n -> Chip(n, TraitColors[i]) }
    }
}
