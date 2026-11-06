package com.carpersona.app.vehicle

/**
 * 차대번호(VIN) 10번째 글자 → 연식(모델 연도).
 * Vehicle SDK에 "연식" API는 없지만 CarInfo.getVINInfo 로 차대번호를 읽을 수 있습니다.
 * 에뮬레이터·일부 차에서는 값이 없거나 가짜일 수 있어서, 시작 화면에서 사용자가 고칠 수 있게 합니다.
 */
object Vin {
    private const val LETTERS = "ABCDEFGHJKLMNPRSTVWXY"   // 2010 ~ 2030 (I, O, Q, U, Z 는 안 씀)

    fun modelYear(vin: String?, currentYear: Int): Int? {
        val v = vin?.trim()?.uppercase() ?: return null
        if (v.length != 17) return null
        val c = v[9]
        val year = when {
            c in LETTERS -> 2010 + LETTERS.indexOf(c)
            c in '1'..'9' -> 2000 + (c - '0')
            else -> return null
        }
        return year.takeIf { it <= currentYear + 1 }
    }
}
