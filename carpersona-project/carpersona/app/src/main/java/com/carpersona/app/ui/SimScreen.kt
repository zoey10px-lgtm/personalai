package com.carpersona.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.life.AppClock
import com.carpersona.app.life.CarCompanion
import com.carpersona.app.persona.SampleCars
import com.carpersona.app.vehicle.CarSnapshot
import com.carpersona.app.vehicle.FakeCar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 시뮬레이터 (가짜 차일 때만 보임). "내 차" 에 일을 일으켜 인격·기분이 어떻게 반응하는지 봅니다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SimScreen(fake: FakeCar, car: CarSnapshot, companion: CarCompanion, now: Long) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            Text("차 안 시계 ${fmt.format(now)} · ${if (fake.driving.value) "주행 중 ${fake.speedKmh.value}km/h" else "정차"} · " +
                "${car.energyWord} ${car.energyPercent?.toInt() ?: 0}% · 누적 ${car.odometerKm?.toInt() ?: 0}km", fontSize = 17.sp)
            Text("정차 중엔 1초 = 1분, 주행은 약 20초 만에 도착하도록 빨리 감아요.", fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("1. 출발 (내비 목적지) — 출발하면 차가 먼저 말을 걸어요")
            FlowRow { fake.places.forEach { p -> SimButton(p.name, enabled = !fake.driving.value) { fake.depart(p) } } }
            SimButton("🏁 지금 도착", primary = true, enabled = fake.driving.value) { fake.arrive() }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("2. 차 상태 → 기분 (성격은 그대로)")
            FlowRow {
                SimButton("🔋 −20%") { fake.addEnergy(-20f) }
                SimButton("⚡ 충전 +40%") { fake.addEnergy(40f) }
                SimButton(if (car.lowTirePressure == true) "타이어 정상으로" else "⚠ 타이어 공기압 낮음") { fake.setTire(car.lowTirePressure != true) }
                SimButton(if (car.engineOilLow == true) "엔진오일 보충" else "⚠ 엔진오일 부족") { fake.setOil(car.engineOilLow != true) }
                SimButton(if (car.lowWasherFluid == true) "워셔액 채우기" else "워셔액 부족") { fake.setWasher(car.lowWasherFluid != true) }
                SimButton(if (car.wiperOn == true) "☔ 비 그침" else "☔ 비") { fake.setRain(car.wiperOn != true) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("3. 기념일 — 알림음 후 차가 먼저 말해요 (성격마다 다르게)")
            FlowRow {
                SimButton("🎉 1만 km 직전으로 (출발하면 곧 넘음)", enabled = !fake.driving.value) {
                    val odo = car.odometerKm ?: 0f
                    fake.setOdometer(((odo / 10_000).toInt() + 1) * 10_000f - 3f)
                }
                SimButton("🎂 다음 출고 기념일로 시계 이동") { companion.jumpToBirthday() }
            }
            Text("출고일을 넣지 않았으면 내일 날짜로 정해서 시험해요 (설정 탭에서 바꿀 수 있어요).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("4. 빨리 감기 — 운행 방식이 내 차의 성격을 바꿉니다 (성장 기록 탭에서 확인)")
            FlowRow {
                SimButton("🏢 출퇴근 1주 (10회)", enabled = !busy && !fake.driving.value) {
                    scope.launch {
                        busy = true
                        repeat(5) {
                            AppClock.advance(AppClock.DAY)
                            companion.addTripNow(SampleCars.WORK, 18f); delay(250)
                            companion.addTripNow(SampleCars.HOME, 18f); delay(250)
                        }
                        companion.talkAboutChange(); busy = false
                    }
                }
                SimButton("🗺 전국 여행 2주 (12곳)", enabled = !busy && !fake.driving.value) {
                    scope.launch {
                        busy = true
                        val start = (AppClock.now() / AppClock.DAY % SampleCars.TRAVEL.size).toInt()
                        repeat(12) { i ->
                            AppClock.advance(AppClock.DAY)
                            companion.addTripNow(SampleCars.TRAVEL[(start + i * 5) % SampleCars.TRAVEL.size], 150f)
                            delay(400)
                        }
                        companion.talkAboutChange(); busy = false
                    }
                }
            }
            Text("위치 저장에 동의하지 않았으면 목적지를 기억하지 않아 모험심이 변하지 않아요 (설정 탭).",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SimButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val m = Modifier.padding(end = 8.dp, bottom = 8.dp)
    if (primary) Button(onClick = onClick, enabled = enabled, modifier = m) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = m) { Text(text, fontSize = 15.sp) }
}
