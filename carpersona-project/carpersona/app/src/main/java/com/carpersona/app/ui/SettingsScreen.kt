package com.carpersona.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.ai.ClaudeBrain
import com.carpersona.app.life.LifeStore

/** 설정: 개인정보(동의·삭제), 목소리, AI 키, 연식 */
@Composable
fun SettingsScreen(store: LifeStore, onVoice: (Boolean) -> Unit) {
    val life = store.life.value ?: return
    var consent by remember { mutableStateOf(store.locationConsent) }
    var voice by remember { mutableStateOf(store.voiceOn) }
    var ai by remember { mutableStateOf(store.aiOn) }
    var key by remember { mutableStateOf(store.apiKey) }
    var year by remember { mutableStateOf(life.modelYear.toString()) }
    var name by remember { mutableStateOf(life.nickname) }
    var release by remember { mutableStateOf(formatDate(life.releaseDate)) }
    var confirmWipe by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            SectionTitle("내 차")
            OutlinedTextField(name, { name = it }, label = { Text("차 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("연식 (모델 연도)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(release, { release = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                label = { Text("출고일 (예: 2023-03-15) — 출고 기념일 알림") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                val r = parseDate(release)
                var next = life.copy(nickname = name.ifBlank { life.nickname }, modelYear = year.toIntOrNull() ?: life.modelYear, releaseDate = r)
                // 출고일을 새로 넣거나 바꿨으면 이미 지난 기념일은 축하하지 않음
                if (r != life.releaseDate) next = next.copy(celebratedAge = next.ageYears(com.carpersona.app.life.AppClock.now()))
                store.save(next)
            }) { Text("저장") }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("개인정보")
            Toggle("목적지·지역 기록 저장 (모험심 계산에 사용)", consent) { consent = it; store.locationConsent = it }
            Text("이동 기록은 이 기기 안에만 저장되고 서버로 보내지 않아요. 끄면 거리·시간만 기록해요.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (!confirmWipe) OutlinedButton(onClick = { confirmWipe = true }) { Text("기억·성격 모두 지우기") }
            else Row(verticalAlignment = Alignment.CenterVertically) {
                Text("정말 지울까요? ", fontSize = 15.sp)
                Button(onClick = { store.wipe() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("모두 지우기") }
                OutlinedButton(onClick = { confirmWipe = false }, modifier = Modifier.padding(start = 8.dp)) { Text("취소") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("목소리")
            Toggle("차가 목소리로 말하기 (안드로이드 기본 TTS)", voice) { voice = it; store.voiceOn = it; onVoice(it) }
            Text("Pleos TTS·STT SDK가 공개되면 그걸로 바꿀 수 있게 따로 떼어 두었어요.", fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("AI 대화 (Claude API, 선택)")
            Toggle("AI로 대답하기", ai) { ai = it; store.aiOn = it }
            OutlinedTextField(key, { key = it }, label = { Text("Anthropic API 키 (sk-ant-...)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { store.apiKey = key }) { Text("키 저장") }
            Text("모델: ${ClaudeBrain.MODEL}. 키는 이 기기에만 저장돼요. 키가 없거나 인터넷이 안 되면 기본 대사로 똑같이 동작해요.\n" +
                "AI를 켜면 질문할 때 성격 점수·기분·최근 기억 요약·차 상태가 Claude API로 전송돼요.",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange)
    }
}
