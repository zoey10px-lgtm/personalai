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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.life.CarCompanion
import com.carpersona.app.persona.TalkContext

val QUICK_QUESTIONS = listOf("안녕?", "오늘 어디 갈까?", "너는 어떤 차야?", "기분 어때?", "배터리 얼마나 남았어?", "요즘 어때?", "점검할 거 있어?")

/**
 * 내 차와 대화 (첫 화면).
 * 왼쪽: 살아 있는 얼굴 + 성격 + 기분 / 오른쪽: 대화. 좁은 화면이면 위아래로.
 */
@Composable
fun TalkScreen(ctx: TalkContext, companion: CarCompanion, listening: Boolean, aiReady: Boolean, wide: Boolean, onMic: () -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(0.42f).verticalScroll(rememberScrollState())) {
                PersonaCard(ctx.life, ctx.mood, ctx.now, excited = ctx.driving)
                Spacer(Modifier.height(12.dp))
                StatusCard(ctx, aiReady)
            }
            Column(Modifier.weight(0.58f)) { ChatPanel(ctx, companion, listening, onMic) }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            CompactPersona(ctx)
            Spacer(Modifier.height(8.dp))
            ChatPanel(ctx, companion, listening, onMic)
        }
    }
}

@Composable
private fun CompactPersona(ctx: TalkContext) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LivingFace(ctx.mood.kind.emoji, ctx.driving, 64)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(com.carpersona.app.persona.PersonaEngine.archetype(ctx.life.traits), fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("${ctx.mood.kind.emoji} ${ctx.mood.kind.word} · ${ctx.mood.reason}", fontSize = 14.sp)
                Text("출고 ${ctx.life.ageYears(ctx.now)}년 차 · ${com.carpersona.app.persona.Dialogue.formatKm(ctx.life.odometerKm)}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StatusCard(ctx: TalkContext, aiReady: Boolean) {
    Card {
        SectionTitle("지금")
        Text(if (ctx.driving) "🚗 주행 중 — 짧게만 말해요" else "🅿️ 정차 중", fontSize = 16.sp)
        ctx.destination?.let {
            Text("📍 ${it.name} " + if (ctx.destinationVisits == 0) "(처음 가는 곳)" else "(${ctx.destinationVisits}번 가 봄)", fontSize = 16.sp)
        }
        Text(if (aiReady) "🧠 대답: Claude AI (실패 시 기본 대사)" else "🧠 대답: 기본 대사 (설정에서 AI 키를 넣으면 AI 대화)",
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChatPanel(ctx: TalkContext, companion: CarCompanion, listening: Boolean, onMic: () -> Unit) {
    val list = rememberLazyListState()
    val chat = companion.chat
    LaunchedEffect(chat.size, companion.thinking.value) { if (chat.isNotEmpty()) list.animateScrollToItem(chat.size) }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list) {
        if (chat.isEmpty()) item {
            Text("차에게 말을 걸어 보세요. 🎙 버튼을 누르거나 아래 질문을 눌러도 돼요.\n출발하면 차가 먼저 말을 걸어요.",
                fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        items(chat) { line -> Bubble(line.text, line.fromCar, if (line.fromCar && line.byAi) "Claude AI" else null) }
        item { if (companion.thinking.value) Bubble("…", true) else Spacer(Modifier.height(4.dp)) }
    }
    // 운전 중에는 질문 단추만 크게 (글자 입력 숨김)
    FlowRow(Modifier.padding(top = 6.dp)) {
        QUICK_QUESTIONS.forEach { q ->
            OutlinedButton(onClick = { companion.ask(q) }, modifier = Modifier.padding(end = 6.dp, bottom = 6.dp)) { Text(q, fontSize = 15.sp) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onMic, modifier = Modifier.size(width = 120.dp, height = 56.dp)) {
            Text(if (listening) "듣는 중…" else "🎙 말하기", fontSize = 17.sp)
        }
        if (!ctx.driving) {
            var text by remember { mutableStateOf("") }
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(text, { text = it }, Modifier.weight(1f), placeholder = { Text("글자로 물어보기") }, singleLine = true)
            Spacer(Modifier.width(8.dp))
            Button(onClick = { companion.ask(text); text = "" }, enabled = text.isNotBlank()) { Text("보내기") }
        }
    }
}
