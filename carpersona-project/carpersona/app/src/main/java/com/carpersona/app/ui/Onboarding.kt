package com.carpersona.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.vehicle.CarSnapshot
import com.carpersona.app.vehicle.Vin
import java.util.Calendar

/**
 * 처음 실행: 연식·주행거리 확인 + 위치 기록 동의 → 차가 깨어남.
 */
@Composable
fun Onboarding(car: CarSnapshot, onStart: (name: String, year: Int, odometerKm: Float, consent: Boolean, releaseDate: Long?) -> Unit) {
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)
    val vinYear = Vin.modelYear(car.vin, thisYear)
    var name by remember { mutableStateOf("내 차") }
    var year by remember { mutableStateOf((vinYear ?: (thisYear - 3)).toString()) }
    var odo by remember { mutableStateOf(car.odometerKm?.toInt()?.toString() ?: "") }
    var consent by remember { mutableStateOf(true) }
    var release by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            LivingFace("😴", false, 120)
            Spacer(Modifier.height(12.dp))
            Text("모든 자동차에는 저마다의 삶이 있어요.", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("지금까지 살아온 연식과 주행거리, 앞으로 다닐 길로 이 차만의 성격이 만들어져요.",
                fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it }, label = { Text("차 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) },
                label = { Text(if (vinYear != null) "연식 (차대번호에서 읽음)" else "연식 (모델 연도)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(odo, { odo = it.filter(Char::isDigit).take(7) },
                label = { Text(if (car.odometerKm != null) "누적 주행거리 km (차에서 읽음)" else "누적 주행거리 km (계기판 숫자)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(release, { release = it.filter { c -> c.isDigit() || c == '-' }.take(10) },
                label = { Text("출고일 (자동차 등록증의 최초 등록일, 예: ${year}-03-15)") }, singleLine = true,
                supportingText = { Text("넣어 두면 출고 기념일마다 차가 먼저 축하 인사를 해요. 나중에 설정에서 넣어도 돼요.") },
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Card {
                Toggle("목적지·지역 기록을 이 기기에 저장할게요", consent) { consent = it }
                Text("어디를 다녀왔는지가 '모험심'과 '익숙함'을 만들어요. 기록은 기기 밖으로 나가지 않고, 설정에서 언제든 지울 수 있어요. " +
                    "동의하지 않으면 거리·시간만 기록해요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = {
                onStart(name.ifBlank { "내 차" }, year.toIntOrNull() ?: thisYear, odo.toFloatOrNull() ?: 0f, consent, parseDate(release))
            }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text("✨ 깨우기", fontSize = 19.sp) }
        }
    }
}

/** "2023-03-15" → 그날 0시 (형식이 틀리면 null) */
fun parseDate(text: String): Long? = runCatching {
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).apply { isLenient = false }.parse(text.trim())?.time
}.getOrNull()

fun formatDate(millis: Long?): String =
    millis?.let { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.KOREA).format(it) } ?: ""
