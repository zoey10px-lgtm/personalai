package com.carpersona.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.persona.CarLife
import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.PersonaEngine
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 성장 기록: 내 운행 방식이 차의 성격을 어떻게 바꿨는지 보여 주는 화면.
 */
@Composable
fun GrowthScreen(life: CarLife, now: Long, wide: Boolean) {
    val f = PersonaEngine.features(life, now)
    val fmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            SectionTitle("성격이 변해 온 길 (주행 ${life.history.size - 1}번)")
            TraitChart(life.history)
            ChartLegend()
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("왜 이런 성격일까? — 계산에 쓴 데이터")
            Fact("연식", "${life.modelYear}년식 (출고 ${life.ageYears(now)}년 차)", "노련함·말투")
            Fact("누적 주행거리", Dialogue.formatKm(life.odometerKm), "노련함")
            Fact("최근 30일 주행", "${f.recentTrips}회 · 주 ${"%.1f".format(f.tripsPerWeek)}회 · 주 ${f.kmPerWeek.roundToInt()}km", "활동성")
            Fact("처음 가 본 곳 비율", "${(f.newPlaceRatio * 100).roundToInt()}%", "모험심 ↑ / 익숙함 ↓")
            Fact("목적지 다양성", "${(f.diversity * 100).roundToInt()}%", "모험심")
            Fact("같은 곳 반복 (상위 2곳)", "${(f.regularity * 100).roundToInt()}%", "익숙함 사랑")
            Fact("장거리(50km+) 비율", "${(f.longTripRatio * 100).roundToInt()}%", "모험심")
            Fact("다녀 본 지역", "${life.visitedRegions.size}곳 ${life.visitedRegions.take(8).joinToString(" ")}", "기억")
            Text("성격은 주행이 끝날 때마다 목표 쪽으로 ${(PersonaEngine.STEP * 100).roundToInt()}%씩만 움직여요. 하루아침에 바뀌지 않아요.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("차의 기억 (${life.memories.size})")
            life.memories.asReversed().take(40).forEach { m ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(m.icon, fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(m.text, fontSize = 16.sp)
                        Text(fmt.format(m.at), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Fact(name: String, value: String, affects: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(name, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(170.dp))
        Text(value, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("→ $affects", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
    }
}
