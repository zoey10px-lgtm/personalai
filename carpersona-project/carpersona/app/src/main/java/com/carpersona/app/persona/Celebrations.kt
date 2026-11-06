package com.carpersona.app.persona

import com.carpersona.app.life.AppClock
import java.util.Calendar

/**
 * 기념일: 차가 알림음을 울린 뒤 먼저 말을 거는 순간.
 *  - 누적 주행거리가 1만 km 단위를 넘은 날 → "나 오늘 {n}만 km 넘었어"
 *  - 출고 기념일 (출고 후 1년마다)     → "나 벌써 {n}살 됐어"
 * 같은 기념일이라도 말투(연식·주행거리)와 대표 성격(모험심·익숙함·활동성)에 따라 다르게 말합니다.
 */
object Celebrations {

    enum class Kind { TEN_K, BIRTHDAY }

    /** @param speak false 면 알림음·말 없이 기억에만 남김 (지난 기념일 등) */
    data class Event(val kind: Kind, val n: Int, val speak: Boolean)

    /** 기념일이 지나고 이 기간 안에 앱을 켜면 늦게라도 축하함 */
    private const val LATE_DAYS = 7

    /**
     * 지금 차 상태로 새로 맞은 기념일을 찾고, 축하했다고 기록한 삶을 돌려줍니다.
     * 1초마다 불러도 같은 기념일은 한 번만 나옵니다.
     */
    fun check(life: CarLife, odometerKm: Float?, now: Long): Pair<CarLife, List<Event>> {
        var next = life
        val events = mutableListOf<Event>()

        // ── 1만 km
        val odo = odometerKm ?: life.odometerKm
        val tenK = (odo / 10_000).toInt()
        if (tenK > life.celebratedTenK) {
            // 한 번에 2만 km 이상 뛰면 (처음 차 값을 받았을 때 등) 축하하지 않고 맞춰 두기만
            val real = tenK - life.celebratedTenK == 1
            if (real) {
                events += Event(Kind.TEN_K, tenK, speak = true)
                next = next.copy(memories = next.memories + Memory(now, "🎉", "누적 ${tenK}만 km를 넘었다.", Memory.Kind.MILESTONE))
            }
            next = next.copy(celebratedTenK = tenK, lastTenKAt = if (real) now else next.lastTenKAt)
        }
        if (odometerKm != null && odometerKm > next.odometerKm) next = next.copy(odometerKm = odometerKm)

        // ── 출고 기념일
        if (life.releaseDate != null) {
            val age = life.ageYears(now)
            if (age > life.celebratedAge) {
                val sinceBirthday = now - anniversary(life.releaseDate, age)
                val onTime = sinceBirthday in 0..LATE_DAYS * AppClock.DAY && age - life.celebratedAge == 1
                events += Event(Kind.BIRTHDAY, age, speak = onTime)
                next = next.copy(
                    celebratedAge = age,
                    memories = next.memories + Memory(now, "🎂", "출고 ${age}주년. ${age}살이 됐다.", Memory.Kind.MILESTONE),
                )
            }
        }
        return next to events
    }

    /** age 번째 출고 기념일 0시 */
    fun anniversary(releaseDate: Long, age: Int): Long = Calendar.getInstance().apply {
        timeInMillis = releaseDate
        add(Calendar.YEAR, age)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** 다음 출고 기념일 0시 (시뮬레이터용) */
    fun nextAnniversary(life: CarLife, now: Long): Long? =
        life.releaseDate?.let { anniversary(it, life.ageYears(now) + 1) }

    // ───────── 성격별 대사 ─────────

    private enum class Flavor { ADVENTURE, ROUTINE, ENERGY, CALM }

    private fun flavor(t: Traits): Flavor {
        val top = listOf(Flavor.ADVENTURE to t.adventure, Flavor.ROUTINE to t.routine, Flavor.ENERGY to t.energy - 10f)
            .maxBy { it.second }
        return if (top.second < 45) Flavor.CALM else top.first
    }

    /** @param driving true 면 운전 중이라 성격 문장을 한 문장으로 줄임 */
    fun line(life: CarLife, e: Event, now: Long, driving: Boolean = false): String {
        val full = if (e.kind == Kind.TEN_K) tenK(life, e.n, now) else birthday(life, e.n, now)
        if (!driving) return full
        // "기념 문장" + 성격 문장 첫 문장만
        val parts = Regex("(?<=[.!?])\\s+").split(full)
        return parts.take(2).joinToString(" ")
    }

    private fun tenK(life: CarLife, n: Int, now: Long): String {
        val r = life.register
        val regions = life.visitedRegions.size
        val fav = life.visitedPlaces.maxByOrNull { it.value }?.let { best ->
            life.trips.lastOrNull { it.placeKey == best.key }?.placeName
        }
        // 직전 1만 km를 넘은 날부터 며칠 걸렸는지 (이번 기념일 직전 값 기준)
        val days = life.lastTenKAt?.takeIf { it < now - AppClock.DAY }?.let { ((now - it) / AppClock.DAY).toInt() }
        val base = when (r) {
            Register.ROOKIE -> "나 오늘 ${n}만 km 넘었어!"
            Register.BUDDY -> "저 오늘 ${n}만 km 넘었어요!"
            Register.ELDER -> "허허, 나 오늘 ${n}만 km를 넘었구먼."
        }
        val more = when (flavor(life.traits)) {
            Flavor.ADVENTURE -> when (r) {
                Register.ROOKIE -> if (regions > 0) "그동안 ${regions}곳이나 가 봤지! 다음 1만 km는 더 멀리 가 보자!" else "다음 1만 km는 안 가 본 데로 잔뜩 가 보자!"
                Register.BUDDY -> if (regions > 0) "그동안 ${regions}곳의 지역을 함께 다녔어요. 다음엔 어디로 가 볼까요?" else "다음 1만 km는 어디로 가 볼까요?"
                Register.ELDER -> if (regions > 0) "${regions}곳이나 돌아다녔으니 이만하면 방랑객이지. 아직 못 가 본 길이 남았으려나?" else "아직 못 가 본 길이 많이 남았겠지?"
            }
            Flavor.ROUTINE -> when (r) {
                Register.ROOKIE -> if (fav != null) "$fav 가는 길이 제일 많았어. 이제 눈 감고도 갈 수 있을 것 같아!" else "늘 다니던 길 덕분이야. 그 길 고마워!"
                Register.BUDDY -> if (fav != null) "그중 제일 많이 간 곳은 ${fav}예요. 늘 같이 다녀 줘서 고마워요." else "늘 같이 다녀 줘서 고마워요."
                Register.ELDER -> if (fav != null) "그 가운데 $fav 가는 길이 제일 많았지. 그 길은 이제 내 손금 같다네." else "매일 다니던 그 길이 쌓여 여기까지 왔구먼."
            }
            Flavor.ENERGY -> when (r) {
                Register.ROOKIE -> if (days != null) "이번 1만 km는 ${days}일 만에 달렸어. 우리 진짜 부지런하다!" else "우리 진짜 부지런히 달렸다!"
                Register.BUDDY -> if (days != null) "이번 1만 km는 ${days}일 걸렸어요. 정말 부지런히 달렸네요." else "정말 부지런히 달렸네요."
                Register.ELDER -> if (days != null) "이번 1만 km는 ${days}일 만이구먼. 이 나이에도 아직 쌩쌩하지." else "이 나이에도 아직 쌩쌩하지."
            }
            Flavor.CALM -> when (r) {
                Register.ROOKIE -> "천천히 왔는데 벌써 여기까지 왔네! 헤헤."
                Register.BUDDY -> "천천히, 꾸준히 여기까지 왔네요."
                Register.ELDER -> "서두르지 않고 걸어온 길이 어느새 이만큼이구먼."
            }
        }
        return "$base $more"
    }

    private fun birthday(life: CarLife, n: Int, now: Long): String {
        val r = life.register
        val yearAgo = now - 365 * AppClock.DAY
        val trips = life.trips.count { it.endAt >= yearAgo }
        val newPlaces = life.trips.count { it.endAt >= yearAgo && it.isNewPlace }
        val base = when (r) {
            Register.ROOKIE -> "나 벌써 ${n}살 됐어!"
            Register.BUDDY -> "저 벌써 ${n}살이 됐어요."
            Register.ELDER -> "허허, 나 벌써 ${n}살이 됐구먼."
        }
        val more = when (flavor(life.traits)) {
            Flavor.ADVENTURE -> when (r) {
                Register.ROOKIE -> if (newPlaces > 0) "지난 1년 동안 ${newPlaces}곳을 처음 가 봤어. 올해도 새로운 데 많이 가자!" else "올해는 새로운 데 많이 가 보자!"
                Register.BUDDY -> if (newPlaces > 0) "지난 1년 동안 처음 가 본 곳이 ${newPlaces}곳이에요. 올해는 어디를 가 볼까요?" else "올해는 어디를 가 볼까요?"
                Register.ELDER -> if (newPlaces > 0) "지난 한 해 처음 가 본 곳이 ${newPlaces}곳이나 되네. 나이는 먹어도 길 욕심은 줄지 않는구먼." else "나이는 먹어도 길 욕심은 줄지 않는구먼."
            }
            Flavor.ROUTINE -> when (r) {
                Register.ROOKIE -> "올해도 늘 가던 길 같이 다니자! 그 길 너무 좋아."
                Register.BUDDY -> "올해도 늘 다니던 길, 함께 잘 다녀요."
                Register.ELDER -> "올해도 다니던 그 길, 잘 부탁하네."
            }
            Flavor.ENERGY -> when (r) {
                Register.ROOKIE -> if (trips > 0) "지난 1년 동안 ${trips}번이나 달렸어! 올해도 신나게 달리자!" else "올해도 신나게 달리자!"
                Register.BUDDY -> if (trips > 0) "지난 1년 동안 ${trips}번 함께 달렸어요. 올해도 잘 부탁해요." else "올해도 잘 부탁해요."
                Register.ELDER -> if (trips > 0) "지난 한 해 ${trips}번이나 달렸으니, 아직 한창이지." else "아직 한창이지."
            }
            Flavor.CALM -> when (r) {
                Register.ROOKIE -> "생일 축하해 줘! 헤헤."
                Register.BUDDY -> "한 해 동안 고마웠어요."
                Register.ELDER -> "한 해 무탈하게 보냈으니 그걸로 됐네."
            }
        }
        return "$base $more"
    }
}
