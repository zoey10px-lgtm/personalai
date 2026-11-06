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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.carpersona.app.ai.PersonaPrompt
import com.carpersona.app.persona.TalkContext

/**
 * 심사용 화면: 앱이 읽는 Pleos API 값 + 그 값으로 만든 AI 입력(성격·기분·기억·상태).
 * "차량 데이터 → 인격" 인과관계를 그대로 보여 줍니다.
 */
@Composable
fun CarDataScreen(ctx: TalkContext, source: String, isReal: Boolean, status: String?, onUseFake: () -> Unit, onTryPleos: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card {
            SectionTitle("데이터 출처")
            Text(source, fontSize = 20.sp)
            status?.let { Text(it, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(8.dp))
            Row {
                if (isReal) OutlinedButton(onClick = onUseFake) { Text("가짜 차로 바꾸기") }
                else OutlinedButton(onClick = onTryPleos) { Text("Pleos 차량 연결 다시 시도") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("Pleos Vehicle SDK 조회 값 (제어 API는 쓰지 않음)")
            if (ctx.car.raw.isEmpty()) Text("아직 받은 값이 없어요.", fontSize = 15.sp)
            ctx.car.raw.forEach { (k, v) ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text(k, fontSize = 14.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(v, fontSize = 14.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card {
            SectionTitle("AI에게 넘기는 정보 (성격 + 기분 + 기억 + 실제 상태)")
            Text(PersonaPrompt.system(ctx), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(24.dp))
    }
}
