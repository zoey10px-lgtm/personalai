package com.carpersona.app.persona

import com.carpersona.app.life.AppClock
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Personality Engine — 차가 살아온 이력 → 장기적 성격.
 *
 * 제안서 8장 알고리즘:
 *  1) 원천 데이터: 연식, 누적 주행거리, 최근 주행(거리·시간·목적지)
 *  2) 특성 추출: 주행 빈도, 경로 다양성, 신규 장소 비율, 누적 경험량, 규칙성
 *  3) 장기적 성격: 네 축 점수를 계산하고, 주행 한 번마다 목표 쪽으로 [STEP] 만큼만 이동 (급변 없음)
 * 연식 하나로 성격을 정하지 않습니다. 같은 10년 차라도 전국을 다닌 차와 동네만 다닌 차는 다르게 나옵니다.
 *
 * 숫자는 프로토타입용 설계값입니다. 아래 상수를 바꾸면 성격이 얼마나 빨리/크게 바뀔지 조절됩니다.
 */
object PersonaEngine {
    /** 최근 며칠 동안의 주행으로 "요즘 습관" 을 볼지 */
    const val WINDOW_DAYS = 30
    /** 주행 한 번마다 목표 성격 쪽으로 움직이는 비율 (0.15 = 15%) */
    const val STEP = 0.15f
    /** 이만큼 변하면 "나 요즘 변한 것 같아" 라고 기억함 */
    const val CHANGE_NOTICE = 8f
    /** 장거리 기준 (km) */
    const val LONG_TRIP_KM = 50f

    /** 2단계: 특성(지표) */
    data class Features(
        val recentTrips: Int,
        val tripsPerWeek: Float,
        val kmPerWeek: Float,
        val newPlaceRatio: Float,
        val diversity: Float,
        val regularity: Float,
        val longTripRatio: Float,
        val ageYears: Int,
        val odometerKm: Float,
    )

    fun features(life: CarLife, now: Long): Features {
        val from = now - WINDOW_DAYS * AppClock.DAY
        val recent = life.trips.filter { it.endAt >= from }
        val known = recent.filter { it.placeKey != null }
        val n = recent.size
        val days = ((now - life.installedAt) / AppClock.DAY.toFloat()).coerceIn(3f, WINDOW_DAYS.toFloat())
        val weeks = days / 7f
        val top2 = known.groupingBy { it.placeKey }.eachCount().values.sortedDescending().take(2).sum()
        return Features(
            recentTrips = n,
            tripsPerWeek = n / weeks,
            kmPerWeek = recent.sumOf { it.km.toDouble() }.toFloat() / weeks,
            newPlaceRatio = if (known.isEmpty()) 0f else known.count { it.isNewPlace } / known.size.toFloat(),
            diversity = if (known.isEmpty()) 0f else known.map { it.placeKey }.distinct().size / known.size.toFloat(),
            regularity = if (known.isEmpty()) 0f else top2 / known.size.toFloat(),
            longTripRatio = if (n == 0) 0f else recent.count { it.km >= LONG_TRIP_KM } / n.toFloat(),
            ageYears = life.ageYears(now),
            odometerKm = life.odometerKm,
        )
    }

    /** 3단계: 지금 이력이 가리키는 "목표" 성격 */
    fun target(life: CarLife, now: Long): Traits {
        val f = features(life, now)
        val seasoned = clamp(f.ageYears / 15f * 60f + f.odometerKm / 200_000f * 40f)
        val p = prior(f)
        if (f.recentTrips == 0) return p.copy(seasoned = seasoned)
        val fromTrips = Traits(
            adventure = clamp(10f + f.newPlaceRatio * 50f + f.diversity * 25f + f.longTripRatio * 25f),
            routine = clamp(10f + f.regularity * 45f + (1f - f.newPlaceRatio) * 45f),
            seasoned = seasoned,
            energy = clamp(f.tripsPerWeek / 14f * 70f + f.kmPerWeek / 1500f * 30f),
        )
        // 주행이 적을 땐 연식·주행거리로 짐작한 값(prior)을 더 믿고, 많을수록 실제 습관을 믿음
        val w = (f.recentTrips / 12f).coerceAtMost(1f)
        return Traits(
            p.adventure * (1 - w) + fromTrips.adventure * w,
            p.routine * (1 - w) + fromTrips.routine * w,
            seasoned,
            p.energy * (1 - w) + fromTrips.energy * w,
        )
    }

    /** 앱 설치 직후처럼 주행 기록이 없을 때: 연식·누적 주행거리로 첫 성격을 짐작 */
    private fun prior(f: Features): Traits {
        val kmPerYear = f.odometerKm / f.ageYears.coerceAtLeast(1)
        return Traits(
            adventure = clamp(30f + (kmPerYear - 12_000f) / 1_000f * 1.5f),
            routine = clamp(35f + f.ageYears * 2.5f),
            seasoned = 0f,
            energy = clamp(25f + kmPerYear / 25_000f * 40f + if (f.ageYears <= 2) 15f else 0f),
        )
    }

    /** 처음 깨어날 때 */
    fun birth(nickname: String, modelYear: Int, odometerKm: Float, now: Long, releaseDate: Long? = null): CarLife {
        val base = CarLife(nickname, modelYear, odometerKm, now, Traits.NEUTRAL, Traits.NEUTRAL, releaseDate = releaseDate)
        // 앱 설치 전에 지난 기념일은 축하하지 않음 (지금부터 새로 넘는 것만)
        val empty = base.copy(celebratedTenK = (odometerKm / 10_000).toInt(), celebratedAge = base.ageYears(now))
        val t = target(empty, now)
        return empty.copy(
            traits = t, notedTraits = t,
            history = listOf(TraitPoint(now, t)),
            memories = listOf(Memory(now, "✨", "앱을 설치한 날, 처음으로 목소리를 얻었다.", Memory.Kind.BIRTH)),
        )
    }

    /**
     * 주행 한 번이 끝났을 때. 기억을 남기고 성격을 조금 움직입니다.
     * @param placeKey 목적지 열쇠 (모르면 null)
     */
    fun afterTrip(
        life: CarLife, now: Long, km: Float, minutes: Float,
        placeKey: String?, placeName: String?, region: String?, odometerFromCar: Float?,
    ): CarLife {
        val visits = placeKey?.let { life.visitedPlaces[it] } ?: 0
        val isNewPlace = placeKey != null && visits == 0
        val isNewRegion = region != null && region !in life.visitedRegions
        val trip = Trip(now, km, minutes, placeKey, placeName, region, isNewPlace, isNewRegion)
        val odo = odometerFromCar ?: (life.odometerKm + km)

        val memories = life.memories.toMutableList()
        when {
            isNewRegion && life.visitedRegions.isNotEmpty() ->
                memories += Memory(now, "🗺️", "처음으로 ${region}에 가 봤다. (${placeName ?: region})", Memory.Kind.FIRST_REGION)
            isNewPlace -> memories += Memory(now, "📍", "처음 가 본 곳: ${placeName ?: "새로운 곳"}", Memory.Kind.TRIP)
            visits + 1 in listOf(10, 30, 100) ->
                memories += Memory(now, "🔁", "${placeName ?: "그곳"}에 ${visits + 1}번째 왔다. 이제 눈 감고도 가겠다.", Memory.Kind.MILESTONE)
        }
        // 1만 km 기념은 Celebrations 가 알림음과 함께 따로 챙김

        var next = life.copy(
            odometerKm = odo,
            trips = (life.trips + trip).takeLast(400),
            visitedPlaces = if (placeKey == null) life.visitedPlaces else life.visitedPlaces + (placeKey to visits + 1),
            visitedRegions = if (region == null) life.visitedRegions else life.visitedRegions + region,
        )
        val moved = next.traits.moveToward(target(next, now), STEP)
        next = next.copy(traits = moved, history = (next.history + TraitPoint(now, moved)).takeLast(200))

        // 성격이 눈에 띄게 변했으면 기억하고, 다음 대화에서 먼저 말함
        val change = describeChange(life.notedTraits, moved)
        if (change != null) {
            memories += Memory(now, "🌱", change, Memory.Kind.CHANGE)
            next = next.copy(notedTraits = moved, pendingTalk = change)
        }
        return next.copy(memories = memories.takeLast(150))
    }

    /** 이전과 비교해 크게 변한 축을 문장으로 (없으면 null) */
    fun describeChange(before: Traits, after: Traits): String? {
        val diffs = before.toList().zip(after.toList()).mapIndexed { i, (b, a) -> Triple(i, b, a) }
            .filter { (_, b, a) -> abs(a - b) >= CHANGE_NOTICE }
        if (diffs.isEmpty()) return null
        return diffs.joinToString(", ") { (i, b, a) ->
            "${Traits.NAMES[i]} ${b.roundToInt()}→${a.roundToInt()}"
        } + " 로 성격이 변했다."
    }

    /** 성격 이름 (제일 두드러진 축 + 말투) */
    fun archetype(t: Traits): String {
        val r = Register.of(t.seasoned)
        // 활동성은 다른 두 축보다 10점 이상 높아야 대표 성격이 됨
        val top = listOf(0 to t.adventure, 1 to t.routine, 3 to t.energy - 10f).maxBy { it.second }
        if (top.second < 45) return when (r) {
            Register.ROOKIE -> "느긋한 새내기"
            Register.BUDDY -> "여유로운 산책가"
            Register.ELDER -> "유유자적 어르신 차"
        }
        return when (r to top.first) {
            Register.ROOKIE to 0 -> "호기심 많은 새내기 탐험가"
            Register.ROOKIE to 1 -> "동네가 좋은 새내기"
            Register.ROOKIE to 3 -> "에너지 넘치는 막내"
            Register.BUDDY to 0 -> "길 위의 모험가"
            Register.BUDDY to 1 -> "성실한 출퇴근 단짝"
            Register.BUDDY to 3 -> "부지런한 마당발"
            Register.ELDER to 0 -> "전국을 누빈 방랑 시인"
            Register.ELDER to 1 -> "동네 터줏대감"
            else -> "아직 쌩쌩한 베테랑"
        }
    }

    /** 30일 전쯤의 성격 (그래프 기록 기준) */
    fun pastTraits(life: CarLife, now: Long): Traits? {
        val from = now - WINDOW_DAYS * AppClock.DAY
        return life.history.firstOrNull { it.at >= from }?.traits
    }

    /** 최근 30일 동안 성격이 얼마나 변했는지 */
    fun recentShift(life: CarLife, now: Long): Traits? {
        val old = TraitPoint(0, pastTraits(life, now) ?: return null)
        val t = life.traits
        return Traits(t.adventure - old.traits.adventure, t.routine - old.traits.routine,
            t.seasoned - old.traits.seasoned, t.energy - old.traits.energy)
    }

    private fun clamp(v: Float) = v.coerceIn(0f, 100f)
}
