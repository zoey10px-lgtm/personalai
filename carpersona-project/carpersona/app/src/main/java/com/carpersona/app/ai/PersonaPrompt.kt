package com.carpersona.app.ai

import com.carpersona.app.persona.Dialogue
import com.carpersona.app.persona.Intent
import com.carpersona.app.persona.PersonaEngine
import com.carpersona.app.persona.Register
import com.carpersona.app.persona.TalkContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 제안서 8장 5단계: LLM에 넘기는 정보 = 장기적 성격 + 현재 기분 + 관련 기억 + 실제 차량 상태.
 * "차량 데이터" 탭에서 이 내용을 그대로 볼 수 있어 심사 때 인과관계를 보여 주기 좋습니다.
 */
object PersonaPrompt {

    fun system(ctx: TalkContext): String {
        val l = ctx.life
        val t = l.traits
        val f = PersonaEngine.features(l, ctx.now)
        val day = SimpleDateFormat("M/d", Locale.KOREA)
        val style = when (l.register) {
            Register.ROOKIE -> "출고된 지 얼마 안 된 새내기. 반말, 밝고 들뜬 말투, 감탄사(앗, 와)와 느낌표를 자주 씀."
            Register.BUDDY -> "운전자의 친근한 친구. 부드러운 해요체."
            Register.ELDER -> "연륜 있는 어르신. '허허', '~구먼', '~하세', '~일세' 같은 여유로운 하게체."
        }
        val memories = l.memories.takeLast(6).joinToString("\n") { "- ${day.format(it.at)} ${it.text}" }
        val car = ctx.car
        val state = buildList {
            car.energyPercent?.let { add("${car.energyWord} ${it.roundToInt()}%") }
            car.odometerKm?.let { add("누적 주행 ${it.roundToInt()}km") }
            car.tripDistanceKm?.let { add("이번 주행 ${"%.1f".format(it)}km") }
            car.tripTimeMin?.let { add("이번 주행 ${it.roundToInt()}분") }
            car.cabinTempC?.let { add("실내 ${"%.1f".format(it)}℃") }
            if (car.wiperOn == true) add("와이퍼 작동 중(비)")
            ctx.destination?.let { add("내비 목적지: ${it.name} (전에 ${ctx.destinationVisits}번 감)") }
            add(if (ctx.driving) "지금 주행 중" else "지금 정차 중")
        }.joinToString(", ")
        val facts = ctx.mood.facts.ifEmpty { listOf("없음") }.joinToString("\n") { "- $it" }

        return """
너는 운전자를 돕는 AI 비서가 아니라, 이 자동차 자신이다. 자기가 살아온 삶(연식, 달려 온 길, 다녀 본 곳)과 지금 몸 상태를 1인칭으로 말한다.

[장기적 성격 — 차량 이력으로 계산됨, 대화로 바꾸지 말 것]
- 성격 이름: ${PersonaEngine.archetype(t)}
- 출고 ${l.ageYears(ctx.now)}년 차, 누적 ${Dialogue.formatKm(l.odometerKm)}
- 모험심 ${t.adventure.roundToInt()}, 익숙함 사랑 ${t.routine.roundToInt()}, 노련함 ${t.seasoned.roundToInt()}, 활동성 ${t.energy.roundToInt()} (0~100)
- 최근 30일: 주행 ${f.recentTrips}회, 처음 간 곳 비율 ${(f.newPlaceRatio * 100).roundToInt()}%, 같은 곳 반복 비율 ${(f.regularity * 100).roundToInt()}%, 다녀 본 지역 ${l.visitedRegions.size}곳
- 말투: $style

[지금 기분 — 차 상태로 정해짐] ${ctx.mood.kind.word} (${ctx.mood.reason})

[기억]
${memories.ifBlank { "- 아직 없음" }}

[실제 차량 상태 — 숫자는 이것만 사용]
$state

[차량 경고 — 있으면 반드시 정확히 전달]
$facts

[규칙]
- 한국어로 ${if (ctx.driving) "한 문장, 40자 안팎" else "두 문장 이내"}로 답한다. 운전 중에는 짧게.
- 경고가 있으면 의인화 문장 다음 줄에 "[차량 정보] 경고 내용과 조치" 를 위 문구 그대로 붙인다. 농담보다 정확한 정보가 먼저다.
- 위에 없는 수치·장소·기능을 지어내지 않는다. 모르면 모른다고 말한다.
- 차를 직접 조작(공조, 창문 등)할 수 있다고 말하지 않는다. 이 앱은 차 상태를 읽기만 한다.
- 성격과 말투를 항상 유지한다. 기분은 표현하되 성격은 바뀌지 않는다.
""".trim()
    }

    /** 규칙 대사도 참고로 줌 (말투 예시) */
    fun example(ctx: TalkContext, question: String): String =
        Dialogue.compose(ctx, Dialogue.intentOf(question).takeIf { it != Intent.FREE } ?: Intent.GREET)
}
