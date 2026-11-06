package com.carpersona.app.persona

import com.carpersona.app.navi.Place
import com.carpersona.app.vehicle.CarSnapshot
import kotlin.math.roundToInt

/** 대화에 필요한 모든 것 (LLM 프롬프트에도 그대로 들어감) */
data class TalkContext(
    val life: CarLife,
    val car: CarSnapshot,
    val mood: Mood,
    val destination: Place?,
    /** 그 목적지에 전에 몇 번 갔는지 (0 = 처음) */
    val destinationVisits: Int,
    val driving: Boolean,
    val now: Long,
)

enum class Intent { CHECK, ENERGY, MEMORY, FAVORITE, WHO, WHERE, MOOD, GREET, THANKS, FREE }

/**
 * 규칙 기반 대사 (LLM 키가 없어도, 인터넷이 없어도 동작).
 * 같은 질문이라도 성격(말투·두드러진 축)과 기분에 따라 다른 문장을 만듭니다.
 */
object Dialogue {

    fun intentOf(q: String): Intent {
        val s = q.replace(" ", "")
        fun has(vararg w: String) = w.any { s.contains(it.replace(" ", "")) }
        return when {
            has("점검", "경고", "타이어", "고장", "이상있", "아픈", "아파", "정비", "오일", "워셔", "브레이크") -> Intent.CHECK
            has("배터리", "연료", "기름", "충전", "주유", "얼마나남", "잔량") -> Intent.ENERGY
            has("요즘", "변했", "달라졌", "기억", "추억", "예전") -> Intent.MEMORY
            has("좋아하는곳", "자주가", "제일많이") -> Intent.FAVORITE
            has("누구", "성격", "소개", "어떤차", "몇살", "나이") -> Intent.WHO
            has("어디", "갈까", "가자", "목적지", "드라이브") -> Intent.WHERE
            has("기분", "컨디션", "어때", "괜찮") -> Intent.MOOD
            has("안녕", "하이", "반가", "좋은아침", "좋은저녁") -> Intent.GREET
            has("고마", "수고", "잘했") -> Intent.THANKS
            else -> Intent.FREE
        }
    }

    /** 질문에 대한 답 */
    fun answer(ctx: TalkContext, question: String): String =
        limit(ctx, compose(ctx, intentOf(question)))

    fun compose(ctx: TalkContext, intent: Intent): String = when (intent) {
        Intent.CHECK -> check(ctx)
        Intent.ENERGY -> energy(ctx)
        Intent.MEMORY -> memory(ctx)
        Intent.FAVORITE -> favorite(ctx)
        Intent.WHO -> who(ctx)
        Intent.WHERE -> where(ctx)
        Intent.MOOD -> mood(ctx)
        Intent.GREET -> greet(ctx)
        Intent.THANKS -> say(ctx, "헤헤, 나도 고마워! 다음에도 같이 달리자!", "저야말로요. 함께 달려 줘서 고마워요.", "허허, 고맙긴. 자네랑 다니는 게 내 낙일세.")
        Intent.FREE -> say(ctx,
            "음… 그건 잘 모르겠어! 대신 '오늘 어디 갈까?'나 '기분 어때?'라고 물어봐 줘!",
            "그건 제가 잘 모르는 이야기예요. '요즘 어때?'나 '너는 어떤 차야?'라고 물어봐 주세요.",
            "허허, 그건 내가 잘 모르는 얘기구먼. 내 지난 길 이야기라면 얼마든지 해 주지.")
    }

    // ───────── 차가 먼저 거는 말 ─────────

    /** 시동/출발 */
    fun onTripStart(ctx: TalkContext): String {
        ctx.life.pendingTalk?.let { return limit(ctx, changeTalk(ctx, it)) }
        return limit(ctx, if (ctx.destination != null) where(ctx) else greet(ctx))
    }

    /** 성격 변화를 알아챘을 때 */
    fun changeTalk(ctx: TalkContext, change: String): String = say(ctx,
        "있지, 나 요즘 좀 변한 것 같아! ${shiftSentence(ctx) ?: change}",
        "저 요즘 좀 달라진 것 같아요. ${shiftSentence(ctx) ?: change}",
        "허허, 내가 요즘 좀 변했나 보구먼. ${shiftSentence(ctx) ?: change}")

    /** 도착 */
    fun onArrive(ctx: TalkContext, trip: Trip): String {
        val place = trip.placeName ?: "목적지"
        val n = ctx.life.visitedPlaces[trip.placeKey] ?: 1
        return limit(ctx, when {
            trip.isNewRegion && trip.region != null -> say(ctx,
                "${trip.region} 처음 와 봤어! 오늘 일 꼭 기억할게!",
                "${trip.region}은 처음이에요. 오늘을 기억해 둘게요.",
                "허허, ${trip.region}이라… 이 나이에 처음 와 보는구먼. 오래 기억하겠네.")
            trip.isNewPlace -> say(ctx,
                "도착! $place, 처음 와 본 곳이야. 신기하다!",
                "$place 도착이에요. 처음 와 본 곳이라 기억해 둘게요.",
                "$place 도착일세. 처음 와 보는 곳이로구먼.")
            else -> say(ctx,
                "도착! ${place}는 벌써 ${n}번째야.",
                "$place 도착이에요. ${n}번째 왔네요.",
                "허허, $place. ${n}번째 오는 길이지.")
        })
    }

    // ───────── 의도별 문장 ─────────

    private fun greet(ctx: TalkContext): String {
        if (ctx.mood.kind == MoodKind.UNCOMFORTABLE || ctx.mood.kind == MoodKind.HUNGRY) return mood(ctx)
        val t = ctx.life.traits
        return when {
            t.adventure >= 55 && t.adventure >= t.routine -> say(ctx,
                "오늘은 또 어디로 가? 처음 보는 길이면 더 신나!",
                "반가워요! 오늘은 어디 새로운 데 안 가요?",
                "허허, 또 길을 나서 보세. 이 나이에도 처음 가는 길은 설렌다네.")
            t.routine >= 55 -> say(ctx,
                "안녕! 오늘도 늘 가던 길이야? 그 길 이제 다 외웠어!",
                "왔어요? 오늘도 늘 그 길이죠? 저 그 길 좋아해요.",
                "허허, 오늘도 그 길이구먼. 이 동네는 내가 훤하지.")
            else -> say(ctx,
                "안녕! 나 준비 다 됐어. 출발해 볼까?",
                "반가워요. 오늘도 잘 부탁해요.",
                "왔는가. 천천히, 편안하게 가 보세.")
        }
    }

    private fun mood(ctx: TalkContext): String {
        val e = ctx.car.energyWord
        val p = ctx.car.energyPercent?.roundToInt() ?: 0
        val charge = if (ctx.car.isEv == false) "주유" else "충전"
        val josa = if (e == "배터리") "가" else "가"
        return when (ctx.mood.kind) {
            MoodKind.HUNGRY -> say(ctx,
                "앗, 나 $e$josa ${p}%밖에 안 남았어! ${charge}하고 다시 신나게 가자!",
                "배가 좀 고파요. $e$josa ${p}% 남았으니 가까운 곳에서 ${charge}하고 가요.",
                "허허, 오늘은 기운이 좀 없구먼. $e$josa ${p}% 남았으니 $charge 계획을 세워 보세.")
            MoodKind.UNCOMFORTABLE -> withFacts(ctx, say(ctx, "으, 어딘가 불편해!", "몸이 좀 불편해요.", "어허, 몸이 좀 찌뿌둥하구먼."))
            MoodKind.EXCITED -> say(ctx,
                "처음 가는 곳이라니, 두근두근해!",
                "처음 가 보는 곳이라 설레요!",
                "허허, 처음 가는 길이라니 마음이 들뜨는구먼.")
            MoodKind.TIRED -> say(ctx,
                "오래 달렸더니 조금 힘들다… 쉬었다 갈래?",
                "꽤 오래 달렸네요. 잠깐 쉬어 가는 건 어때요?",
                "허허, 오래 달렸구먼. 쉬어 가는 것도 길의 일부라네.")
            MoodKind.CHEERFUL -> say(ctx,
                "기분 최고야! $e ${p}%, 힘이 넘쳐!",
                "기분 좋아요! $e${josa} ${p}%나 있어요.",
                "허허, 오늘은 몸이 가볍구먼. $e${josa} ${p}%나 남았네.")
            MoodKind.RELAXED -> say(ctx,
                "그냥 평범하게 좋아! 별일 없어.",
                "편안해요. 특별한 일은 없어요.",
                "무탈하네. 무탈한 게 제일이지.")
        }
    }

    private fun energy(ctx: TalkContext): String {
        val e = ctx.car.energyWord
        val p = ctx.car.energyPercent?.roundToInt() ?: return say(ctx,
            "어, $e 잔량을 지금은 못 읽겠어.", "$e 잔량을 지금은 읽을 수 없어요.", "$e 잔량이 지금은 안 보이는구먼.")
        if (ctx.mood.kind == MoodKind.HUNGRY) return mood(ctx)
        return if (p >= 50) say(ctx,
            "$e ${p}% 남았어! 아직 한참 달릴 수 있어.",
            "$e${"는"} ${p}% 남았어요. 넉넉해요.",
            "$e${"가"} ${p}% 남았구먼. 아직 걱정 없네.")
        else say(ctx,
            "$e ${p}% 남았어. 오늘 갈 데는 충분해!",
            "$e${"는"} ${p}% 남았어요. 오늘은 충분하지만 미리 ${if (ctx.car.isEv == false) "주유" else "충전"} 계획을 세워 두면 좋아요.",
            "$e${"가"} ${p}% 남았네. 오늘은 되겠지만 미리 채워 두는 게 좋겠구먼.")
    }

    private fun check(ctx: TalkContext): String {
        if (ctx.mood.facts.isNotEmpty()) return withFacts(ctx, say(ctx, "나 좀 봐 줘!", "점검이 필요해요.", "어허, 손 좀 봐야겠구먼."))
        return say(ctx,
            "다 괜찮아! 지금 들어온 경고는 하나도 없어.",
            "점검해 봤는데 경고는 없어요. 타이어·엔진오일·브레이크액·워셔액 모두 정상이에요.",
            "허허, 아픈 데 없네. 타이어, 엔진오일, 브레이크액, 워셔액 모두 정상일세.")
    }

    private fun who(ctx: TalkContext): String {
        val l = ctx.life
        val age = l.ageYears(ctx.now)
        val odo = formatKm(l.odometerKm)
        val arch = PersonaEngine.archetype(l.traits)
        val t = l.traits
        val a = t.adventure.roundToInt(); val r = t.routine.roundToInt(); val en = t.energy.roundToInt()
        val reason = when (listOf(0 to t.adventure, 1 to t.routine, 2 to t.energy).maxBy { it.second }.first) {
            0 -> say(ctx, "요즘 처음 가는 곳이 많아서 모험심이 ${a}점이야!", "최근에 처음 가 본 곳이 많아서 모험심이 ${a}점이에요.", "이곳저곳 다녀 본 덕에 모험심이 ${a}점이라네.")
            1 -> say(ctx, "매일 같은 길을 달려서 익숙한 게 좋아. 익숙함 사랑 ${r}점!", "늘 다니는 길이 많아서 익숙함 사랑이 ${r}점이에요.", "매일 같은 길을 오가다 보니 익숙한 게 제일 좋구먼. 익숙함 사랑이 ${r}점일세.")
            else -> say(ctx, "자주 달려서 활동성이 ${en}점이야!", "자주, 많이 달려서 활동성이 ${en}점이에요.", "아직도 부지런히 다녀서 활동성이 ${en}점이라네.")
        }
        val ageWord = if (age == 0) "올해 나온" else "출고 ${age}년 차"
        return say(ctx,
            "나는 $ageWord, ${odo} 달린 '$arch'야! $reason",
            "저는 $ageWord, ${odo}를 달린 '$arch'예요. $reason",
            "나는 $ageWord, ${odo}를 달려 온 '$arch'일세. $reason")
    }

    private fun where(ctx: TalkContext): String {
        val d = ctx.destination
        val t = ctx.life.traits
        if (d != null) {
            val n = ctx.destinationVisits
            return if (n == 0) say(ctx,
                "${d.name}? 처음 가 보는 곳이네! 신난다!",
                "${d.name}는 처음이에요. 어떤 곳일지 궁금해요!",
                "허허, ${d.name}라… 처음 가 보는 곳이구먼. 기대되네.")
            else if (t.adventure >= 55 && t.adventure > t.routine) say(ctx,
                "또 ${d.name}야? ${n + 1}번째네! 다음엔 새로운 데도 가 보자!",
                "${d.name}, ${n + 1}번째네요. 다음엔 안 가 본 곳도 가 봐요!",
                "${d.name}라면 ${n + 1}번째구먼. 다음엔 낯선 길도 한번 가 보세.")
            else say(ctx,
                "${d.name}? ${n + 1}번째야! 이제 길 다 외웠어.",
                "${d.name}, ${n + 1}번째네요. 익숙한 길이라 편해요.",
                "${d.name}라면 ${n}번이나 가 본 길이지. 내가 훤하다네.")
        }
        val fav = favoritePlace(ctx.life)?.first ?: "늘 가던 곳"
        val lastNew = ctx.life.trips.lastOrNull { it.isNewPlace }?.placeName
        return if (t.adventure >= t.routine) say(ctx,
            "어디든 좋아! 안 가 본 동네로 가 보자!",
            "오늘은 안 가 본 곳 어때요?" + (lastNew?.let { " 최근에 새로 간 곳은 ${it}였어요." } ?: ""),
            "허허, 오랜만에 낯선 길로 바람 좀 쐬러 가 보세.")
        else say(ctx,
            "늘 가던 데? 나 $fav 가는 길 좋아!",
            "늘 가던 $fav 가요? 저는 그 길이 편해요.",
            "가던 데로 가세. $fav 가는 길은 내가 훤하지.")
    }

    private fun memory(ctx: TalkContext): String {
        shiftSentence(ctx)?.let { return it }
        val m = ctx.life.memories.lastOrNull { it.kind != Memory.Kind.BIRTH }
            ?: return say(ctx,
                "아직은 함께한 기억이 별로 없어. 같이 많이 달려 보자!",
                "아직 함께한 기억이 많지 않아요. 같이 많이 달려 봐요.",
                "허허, 아직 자네와 쌓은 이야기가 많지 않구먼. 천천히 쌓아 가세.")
        return say(ctx, "기억나? ${m.text}", "요즘 기억에 남는 건요… ${m.text}", "그러고 보니 ${m.text} 허허, 좋은 날이었지.")
    }

    /** 최근 30일 사이 성격이 눈에 띄게 변했으면 그 이야기 */
    private fun shiftSentence(ctx: TalkContext): String? {
        val s = PersonaEngine.recentShift(ctx.life, ctx.now) ?: return null
        val wasHomebody = PersonaEngine.pastTraits(ctx.life, ctx.now)!!.let { it.routine >= 55f && it.routine >= it.adventure }
        return when {
            s.adventure >= PersonaEngine.CHANGE_NOTICE && !wasHomebody -> say(ctx,
                "요즘 처음 가는 곳이 점점 많아져! 모험심이 쑥쑥 자라는 중이야!",
                "요즘 처음 가 보는 곳이 많아졌어요. 모험심이 점점 커지는 게 느껴져요.",
                "요즘 낯선 길을 자주 가는구먼. 모험심이 점점 커지는 게 느껴지네.")
            s.adventure >= PersonaEngine.CHANGE_NOTICE -> say(ctx,
                "예전엔 매일 같은 길이었는데, 요즘은 처음 가는 곳이 많아졌어. 나도 조금 모험가가 된 것 같아!",
                "예전에는 매일 같은 길이었는데, 요즘은 처음 가는 곳이 많아졌네요. 저도 조금 모험가가 된 것 같아요!",
                "예전에는 매일 같은 길이었는데, 요즘은 처음 가는 곳이 많아졌구먼. 늘그막에 모험가가 된 기분일세.")
            s.routine >= PersonaEngine.CHANGE_NOTICE -> say(ctx,
                "요즘은 매일 같은 길이야. 그런데 이 길이 점점 좋아져!",
                "요즘은 늘 같은 길을 다녀요. 그 길이 점점 편해지고 있어요.",
                "요즘은 같은 길만 오가는구먼. 그래도 익숙한 길이 주는 편안함이 있지.")
            s.energy >= PersonaEngine.CHANGE_NOTICE -> say(ctx,
                "요즘 엄청 자주 달려서 몸이 근질근질해!", "요즘 자주 달려서 활기가 붙었어요.", "요즘 부쩍 자주 나서는구먼. 몸이 다시 젊어지는 것 같네.")
            s.energy <= -PersonaEngine.CHANGE_NOTICE -> say(ctx,
                "요즘 잘 안 나가서 심심해…", "요즘은 쉬는 날이 많아서 조금 느긋해졌어요.", "요즘은 쉬는 날이 많구먼. 이것도 나쁘지 않네.")
            else -> null
        }
    }

    private fun favorite(ctx: TalkContext): String {
        val f = favoritePlace(ctx.life) ?: return say(ctx,
            "아직 정해진 단골은 없어! 여기저기 다 좋아.", "아직 단골이라 할 만한 곳은 없어요.", "아직 정든 곳은 없구먼.")
        return say(ctx,
            "제일 많이 간 곳은 ${f.first}! ${f.second}번이나 갔어.",
            "제일 자주 간 곳은 ${f.first}예요. ${f.second}번 갔어요.",
            "${f.first}지. ${f.second}번이나 오간 길이라 정이 들었네.")
    }

    // ───────── 도우미 ─────────

    private fun favoritePlace(life: CarLife): Pair<String, Int>? {
        val best = life.visitedPlaces.maxByOrNull { it.value } ?: return null
        val name = life.trips.lastOrNull { it.placeKey == best.key }?.placeName ?: return null
        return name to best.value
    }

    /** 말투별 문장 고르기 */
    private fun say(ctx: TalkContext, rookie: String, buddy: String, elder: String) = when (ctx.life.register) {
        Register.ROOKIE -> rookie
        Register.BUDDY -> buddy
        Register.ELDER -> elder
    }

    /** 경고가 있으면 의인화 문장 뒤에 정확한 진단 정보를 따로 붙임 */
    private fun withFacts(ctx: TalkContext, persona: String): String =
        persona + ctx.mood.facts.joinToString("") { "\n[차량 정보] $it" }

    /**
     * 운전 중에는 짧게: 의인화 문장은 첫 문장만, [차량 정보] 줄은 그대로 둠.
     */
    fun limit(ctx: TalkContext, text: String): String {
        if (!ctx.driving) return text
        val lines = text.split("\n")
        val persona = lines.first()
        val facts = lines.drop(1)
        val first = Regex("^.*?[.!?…](\\s|$)").find(persona)?.value?.trim() ?: persona
        return (listOf(first) + facts).joinToString("\n")
    }

    fun formatKm(km: Float): String = when {
        km >= 10_000 -> "%.1f만 km".format(km / 10_000)
        else -> "${km.roundToInt()}km"
    }
}
