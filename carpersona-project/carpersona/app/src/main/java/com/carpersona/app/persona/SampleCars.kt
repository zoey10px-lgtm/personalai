package com.carpersona.app.persona

import com.carpersona.app.life.AppClock
import com.carpersona.app.navi.Place
import com.carpersona.app.navi.Regions
import com.carpersona.app.vehicle.CarSnapshot
import java.util.Calendar
import kotlin.random.Random

/**
 * 시연용 차 두 대 (같은 모델, 다른 삶).
 * 실제로 PersonaEngine.afterTrip 을 주행 수만큼 돌려서 만들기 때문에,
 * 화면에 보이는 성격 점수는 "손으로 넣은 값" 이 아니라 이력에서 계산된 값입니다.
 */
object SampleCars {
    val HOME = Place("우리 집", 37.5563, 126.9100)
    val WORK = Place("회사", 37.4980, 127.0276)

    /** 여행지 (좌표는 근사값) */
    val TRAVEL = listOf(
        Place("강릉 경포해변", 37.8055, 128.9083), Place("전주 한옥마을", 35.8150, 127.1530),
        Place("경주 불국사", 35.7900, 129.3320), Place("부산 해운대", 35.1587, 129.1604),
        Place("여수 밤바다", 34.7380, 127.7460), Place("가평 남이섬", 37.7906, 127.5256),
        Place("속초 영금정", 38.2130, 128.6000), Place("안동 하회마을", 36.5390, 128.5180),
        Place("태안 꽃지해변", 36.4960, 126.3350), Place("통영 동피랑", 34.8450, 128.4270),
        Place("춘천 소양강", 37.8960, 127.7230), Place("순천만 습지", 34.8830, 127.5090),
        Place("공주 공산성", 36.4640, 127.1260), Place("평창 대관령", 37.6870, 128.7590),
        Place("포항 영일대", 36.0560, 129.3780), Place("군산 근대거리", 35.9870, 126.7110),
    )

    private fun year(now: Long) = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)

    fun drive(life: CarLife, at: Long, to: Place, km: Float): CarLife =
        PersonaEngine.afterTrip(
            life, at, km, km /* 60km/h 가정: 분 = km */, Regions.placeKey(to), to.name,
            Regions.nameOf(to.lat!!, to.lon!!), null,
        )

    /** A차: 출고 1년, 9,800km, 최근 한 달 동안 처음 가는 곳을 많이 다님 */
    fun carA(now: Long = System.currentTimeMillis()): CarLife {
        val start = now - 40 * AppClock.DAY
        var life = PersonaEngine.birth("A차", year(now) - 1, 8_200f, start)
        val rnd = Random(7)
        var next = 0
        for (d in 0 until 30) {
            val at = now - (30 - d) * AppClock.DAY
            // 거의 매일 처음 가는 곳, 사흘에 한 번만 집으로
            life = if (d % 3 != 2) drive(life, at, TRAVEL[next++ % TRAVEL.size], 60f + rnd.nextInt(140))
            else drive(life, at, HOME, 15f + rnd.nextInt(30))
        }
        return life.copy(pendingTalk = null)
    }

    /** B차: 출고 10년, 16.8만 km, 매일 같은 출퇴근 길 */
    fun carB(now: Long = System.currentTimeMillis()): CarLife {
        val start = now - 45 * AppClock.DAY
        var life = PersonaEngine.birth("B차", year(now) - 10, 167_000f, start)
        for (d in 0 until 30) {
            val at = now - (30 - d) * AppClock.DAY
            if (d % 7 == 5 || d % 7 == 6) continue    // 주말엔 쉼
            life = drive(life, at, WORK, 18f)
            life = drive(life, at + 9 * 3_600_000L, HOME, 18f)
        }
        return life.copy(pendingTalk = null)
    }

    /**
     * 시연 3: B차가 한 달 동안 여행을 다니기 시작함. day(1~30)번째 날의 주행을 반영한 삶.
     * 처음 이틀은 평소처럼 출퇴근, 그 뒤로는 거의 매일 처음 가는 지역 (보름에 한 번 출근).
     */
    fun monthDay(life: CarLife, day: Int, t0: Long): CarLife {
        val at = t0 + day * AppClock.DAY
        return if (day <= 2 || day % 15 == 0) {
            drive(drive(life, at, WORK, 18f), at + 9 * 3_600_000L, HOME, 18f)
        } else {
            val place = TRAVEL[(day * 5) % TRAVEL.size]
            drive(life, at, place, 120f + (day * 37) % 160)
        }
    }

    /** 시연용 차 상태 (배터리·경고만 바꿔 가며 씀) */
    fun snapshot(energy: Float, tireWarning: Boolean, odometerKm: Float) = CarSnapshot(
        odometerKm = odometerKm, energyPercent = energy, isEv = true,
        lowTirePressure = tireWarning, lowWasherFluid = false, engineOilLow = false, brakeFluidLow = false,
    )
}
