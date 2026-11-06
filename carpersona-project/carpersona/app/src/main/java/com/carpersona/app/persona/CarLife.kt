package com.carpersona.app.persona

/**
 * 장기적 성격의 네 축 (0~100).
 *  - adventure 모험심 : 새로운 곳·다양한 길·장거리
 *  - routine   익숙함 사랑(안정성) : 같은 길을 반복하는 정도
 *  - seasoned  노련함 : 연식 + 누적 주행거리 → 말투(새내기/친구/연륜)
 *  - energy    활동성 : 얼마나 자주, 많이 달리는지
 */
data class Traits(val adventure: Float, val routine: Float, val seasoned: Float, val energy: Float) {
    fun toList() = listOf(adventure, routine, seasoned, energy)

    /** 조금씩만 움직이기: 지금 값에서 목표 쪽으로 rate 만큼 */
    fun moveToward(t: Traits, rate: Float) = Traits(
        adventure + (t.adventure - adventure) * rate,
        routine + (t.routine - routine) * rate,
        seasoned + (t.seasoned - seasoned) * rate,
        energy + (t.energy - energy) * rate,
    )

    companion object {
        val NAMES = listOf("모험심", "익숙함 사랑", "노련함", "활동성")
        val NEUTRAL = Traits(40f, 40f, 30f, 40f)
    }
}

/** 말투 (노련함으로 정해짐) */
enum class Register(val label: String) {
    ROOKIE("풋풋한 새내기 말투"),
    BUDDY("친근한 친구 말투"),
    ELDER("연륜 있는 어르신 말투");

    companion object {
        fun of(seasoned: Float) = when {
            seasoned < 35 -> ROOKIE
            seasoned < 62 -> BUDDY
            else -> ELDER
        }
    }
}

/** 주행 한 번 */
data class Trip(
    val endAt: Long,
    val km: Float,
    val minutes: Float,
    /** 같은 장소 판단용 열쇠 (위치 저장 동의를 안 했으면 null) */
    val placeKey: String?,
    val placeName: String?,
    val region: String?,
    val isNewPlace: Boolean,
    val isNewRegion: Boolean,
)

/** 기억 한 줄 */
data class Memory(val at: Long, val icon: String, val text: String, val kind: Kind) {
    enum class Kind { TRIP, FIRST_REGION, MILESTONE, CHANGE, BIRTH }
}

/** 성격 변화 기록 (그래프용) */
data class TraitPoint(val at: Long, val traits: Traits)

/**
 * 차 한 대의 "삶" 전체. 이 값만 있으면 인격·기억이 다시 계산됩니다.
 * 내 차는 LifeStore 에 저장되고, 시연용 A차·B차는 메모리에만 있습니다.
 */
data class CarLife(
    val nickname: String,
    /** 연식(모델 연도). VIN 10번째 글자 또는 사용자 입력 */
    val modelYear: Int,
    /** 누적 주행거리. Odometer API가 있으면 그 값, 없으면 앱이 더해 감 */
    val odometerKm: Float,
    val installedAt: Long,
    val traits: Traits,
    /** 마지막으로 "성격이 변했다" 고 기억한 시점의 성격 */
    val notedTraits: Traits,
    val trips: List<Trip> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val history: List<TraitPoint> = emptyList(),
    val visitedPlaces: Map<String, Int> = emptyMap(),
    val visitedRegions: Set<String> = emptySet(),
    /** 차가 다음에 먼저 꺼낼 이야기 (성격 변화 등) */
    val pendingTalk: String? = null,
    /** 출고일 (자동차 등록증의 최초 등록일). 모르면 null → 출고 기념일 알림 없음 */
    val releaseDate: Long? = null,
    /** 이미 축하한 주행거리 (1만 km 단위 개수) */
    val celebratedTenK: Int = 0,
    /** 이미 축하한 나이 */
    val celebratedAge: Int = 0,
    /** 마지막으로 1만 km를 넘은 날 (다음 1만 km까지 며칠 걸렸는지 말하려고) */
    val lastTenKAt: Long? = null,
) {
    val register: Register get() = Register.of(traits.seasoned)
    /** 나이: 출고일을 알면 만 나이(기념일 기준), 모르면 연식 기준 */
    fun ageYears(now: Long): Int {
        val c = java.util.Calendar.getInstance().apply { timeInMillis = now }
        val r = releaseDate ?: return (c.get(java.util.Calendar.YEAR) - modelYear).coerceAtLeast(0)
        val b = java.util.Calendar.getInstance().apply { timeInMillis = r }
        var age = c.get(java.util.Calendar.YEAR) - b.get(java.util.Calendar.YEAR)
        val beforeBirthday = c.get(java.util.Calendar.MONTH) < b.get(java.util.Calendar.MONTH) ||
            (c.get(java.util.Calendar.MONTH) == b.get(java.util.Calendar.MONTH) &&
                c.get(java.util.Calendar.DAY_OF_MONTH) < b.get(java.util.Calendar.DAY_OF_MONTH))
        if (beforeBirthday) age--
        return age.coerceAtLeast(0)
    }
}
