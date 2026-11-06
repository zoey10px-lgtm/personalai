package com.carpersona.app.persona

import com.carpersona.app.vehicle.CarSnapshot
import kotlin.math.roundToInt

/** 일시적 기분 */
enum class MoodKind(val emoji: String, val word: String) {
    UNCOMFORTABLE("😣", "불편해요"),
    HUNGRY("🥱", "기운이 없어요"),
    EXCITED("🤩", "설레요"),
    TIRED("😮‍💨", "조금 지쳤어요"),
    CHEERFUL("😄", "활기차요"),
    RELAXED("😌", "편안해요"),
}

/**
 * @param facts 실제 진단 정보 (의인화와 따로, 정확한 내용과 조치). 경고가 없으면 빈 목록
 */
data class Mood(val kind: MoodKind, val reason: String, val facts: List<String>)

/**
 * Mood Engine — 지금 차 상태 → 일시적 기분.
 * 장기적 성격(Traits)은 전혀 보지 않습니다. 그래서 배터리가 변해도 성격은 그대로, 기분만 바뀝니다.
 * 성격은 "그 기분을 어떤 말투로 표현할지" 에만 쓰입니다 (Dialogue).
 */
object MoodEngine {
    const val LOW_ENERGY = 25f

    fun of(car: CarSnapshot, newDestination: Boolean, driving: Boolean): Mood {
        val facts = warnings(car)
        val energy = car.energyPercent
        return when {
            facts.isNotEmpty() -> Mood(MoodKind.UNCOMFORTABLE, "차량 경고 ${facts.size}건", facts)
            energy != null && energy <= LOW_ENERGY ->
                Mood(MoodKind.HUNGRY, "${car.energyWord} ${energy.roundToInt()}%", facts)
            newDestination -> Mood(MoodKind.EXCITED, "처음 가 보는 목적지", facts)
            driving && (car.tripTimeMin ?: 0f) >= 120f ->
                Mood(MoodKind.TIRED, "${(car.tripTimeMin!! / 60).toInt()}시간 넘게 달리는 중", facts)
            energy != null && energy >= 60f -> Mood(MoodKind.CHEERFUL, "${car.energyWord} 넉넉 (${energy.roundToInt()}%)", facts)
            else -> Mood(MoodKind.RELAXED, "특별한 일 없음", facts)
        }
    }

    /** 차량 경고 → 정확한 상태 + 조치 (캐릭터 유머보다 먼저 전달) */
    fun warnings(car: CarSnapshot): List<String> = buildList {
        if (car.lowTirePressure == true) add("타이어 공기압 낮음 — 안전한 곳에 정차해 공기압을 점검하세요.")
        if (car.brakeFluidLow == true) add("브레이크액 부족 — 운행을 줄이고 정비소 점검을 받으세요.")
        if (car.engineOilLow == true) add("엔진오일 부족 — 가까운 정비소에서 오일을 점검·보충하세요.")
        if (car.lowWasherFluid == true) add("워셔액 부족 — 워셔액을 보충하세요.")
    }
}
