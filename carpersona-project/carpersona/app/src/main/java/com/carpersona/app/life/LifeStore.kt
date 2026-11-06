package com.carpersona.app.life

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.carpersona.app.persona.CarLife
import com.carpersona.app.persona.Memory
import com.carpersona.app.persona.TraitPoint
import com.carpersona.app.persona.Traits
import com.carpersona.app.persona.Trip
import org.json.JSONArray
import org.json.JSONObject

/**
 * 내 차의 삶(성격·기억·주행 요약)과 설정을 차량(폰) 안에만 저장합니다.
 * 서버로 보내지 않습니다. (AI 대화를 켜면 질문할 때 요약만 Claude API로 전송)
 * 설정 탭에서 언제든 전부 지울 수 있습니다.
 */
class LifeStore(context: Context) {
    private val prefs = context.getSharedPreferences("carpersona", Context.MODE_PRIVATE)

    /** 화면이 자동으로 다시 그려지는 내 차의 삶 (온보딩 전이면 null) */
    val life = mutableStateOf<CarLife?>(prefs.getString("life", null)?.let { runCatching { fromJson(JSONObject(it)) }.getOrNull() })

    var locationConsent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(v) = prefs.edit().putBoolean("consent", v).apply()
    var voiceOn: Boolean
        get() = prefs.getBoolean("voice", true)
        set(v) = prefs.edit().putBoolean("voice", v).apply()
    var aiOn: Boolean
        get() = prefs.getBoolean("ai", true)
        set(v) = prefs.edit().putBoolean("ai", v).apply()
    /** Claude API 키. 코드·GitHub에는 절대 넣지 말고, 앱 설정 화면에서만 입력 */
    var apiKey: String
        get() = prefs.getString("apiKey", "") ?: ""
        set(v) = prefs.edit().putString("apiKey", v.trim()).apply()

    fun save(l: CarLife) {
        life.value = l
        prefs.edit().putString("life", toJson(l).toString()).apply()
    }

    /** 기억·성격 모두 지우기 (온보딩부터 다시) */
    fun wipe() {
        life.value = null
        prefs.edit().remove("life").apply()
    }

    // ───────── JSON ─────────

    private fun Traits.json() = JSONArray().put(adventure.toDouble()).put(routine.toDouble()).put(seasoned.toDouble()).put(energy.toDouble())
    private fun traits(a: JSONArray) = Traits(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat(), a.getDouble(3).toFloat())

    private fun toJson(l: CarLife) = JSONObject().apply {
        put("nickname", l.nickname); put("modelYear", l.modelYear); put("odometerKm", l.odometerKm.toDouble())
        put("installedAt", l.installedAt); put("traits", l.traits.json()); put("notedTraits", l.notedTraits.json())
        put("pendingTalk", l.pendingTalk ?: JSONObject.NULL)
        put("releaseDate", l.releaseDate ?: JSONObject.NULL)
        put("celebratedTenK", l.celebratedTenK); put("celebratedAge", l.celebratedAge)
        put("lastTenKAt", l.lastTenKAt ?: JSONObject.NULL)
        put("trips", JSONArray().apply {
            l.trips.forEach { t ->
                put(JSONObject().put("endAt", t.endAt).put("km", t.km.toDouble()).put("min", t.minutes.toDouble())
                    .put("key", t.placeKey ?: JSONObject.NULL).put("name", t.placeName ?: JSONObject.NULL)
                    .put("region", t.region ?: JSONObject.NULL).put("newPlace", t.isNewPlace).put("newRegion", t.isNewRegion))
            }
        })
        put("memories", JSONArray().apply {
            l.memories.forEach { m -> put(JSONObject().put("at", m.at).put("icon", m.icon).put("text", m.text).put("kind", m.kind.name)) }
        })
        put("history", JSONArray().apply { l.history.forEach { p -> put(JSONObject().put("at", p.at).put("t", p.traits.json())) } })
        put("places", JSONObject().apply { l.visitedPlaces.forEach { (k, v) -> put(k, v) } })
        put("regions", JSONArray(l.visitedRegions.toList()))
    }

    private fun JSONObject.str(k: String): String? = if (isNull(k)) null else optString(k)

    private fun fromJson(o: JSONObject): CarLife {
        val trips = o.getJSONArray("trips")
        val mems = o.getJSONArray("memories")
        val hist = o.getJSONArray("history")
        val places = o.getJSONObject("places")
        val regions = o.getJSONArray("regions")
        return CarLife(
            nickname = o.getString("nickname"),
            modelYear = o.getInt("modelYear"),
            odometerKm = o.getDouble("odometerKm").toFloat(),
            installedAt = o.getLong("installedAt"),
            traits = traits(o.getJSONArray("traits")),
            notedTraits = traits(o.getJSONArray("notedTraits")),
            pendingTalk = o.str("pendingTalk"),
            releaseDate = if (o.isNull("releaseDate")) null else o.optLong("releaseDate"),
            celebratedTenK = o.optInt("celebratedTenK", (o.getDouble("odometerKm") / 10_000).toInt()),
            celebratedAge = o.optInt("celebratedAge", 0),
            lastTenKAt = if (o.isNull("lastTenKAt")) null else o.optLong("lastTenKAt"),
            trips = List(trips.length()) {
                val t = trips.getJSONObject(it)
                Trip(t.getLong("endAt"), t.getDouble("km").toFloat(), t.getDouble("min").toFloat(), t.str("key"),
                    t.str("name"), t.str("region"), t.getBoolean("newPlace"), t.getBoolean("newRegion"))
            },
            memories = List(mems.length()) {
                val m = mems.getJSONObject(it)
                Memory(m.getLong("at"), m.getString("icon"), m.getString("text"), Memory.Kind.valueOf(m.getString("kind")))
            },
            history = List(hist.length()) { val p = hist.getJSONObject(it); TraitPoint(p.getLong("at"), traits(p.getJSONArray("t"))) },
            visitedPlaces = places.keys().asSequence().associateWith { places.getInt(it) },
            visitedRegions = List(regions.length()) { regions.getString(it) }.toSet(),
        )
    }
}
