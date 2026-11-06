# CAR PERSONA — 차량 인격 AI

> 모든 자동차에는 저마다의 삶이 있다. 우리는 그 삶에 목소리와 인격을 부여한다.

자동차의 **연식·누적 주행거리·운행 빈도·활동 반경·반복 경로**로 차마다 다른 AI 인격을 만들고,
**지금 차 상태(배터리·경고)**로 그날의 기분을 정해, 자동차 자신의 목소리로 이야기하는 Pleos Connect 앱입니다.

## 세 가지 경험
| 경험 | 앱에서 보이는 것 |
|---|---|
| **자동차마다 성격이 다른 재미** | 같은 질문에 1년 차 "호기심 많은 새내기 탐험가"와 10년 차 "동네 터줏대감"이 전혀 다르게 대답 (시연 ①) |
| **내가 차를 쓰는 방식에 따라 인격이 변하는 신기함** | 출퇴근만 하던 차가 한 달 여행 뒤 "전국을 누빈 방랑 시인"으로. 성격 그래프·기억으로 확인 (시연 ③, 성장 기록 탭) |
| **자동차가 살아 있는 존재처럼 느껴지는 경험** | 숨 쉬는 얼굴, 배터리가 줄면 배고파함, 출발·도착·처음 가는 곳에서 차가 먼저 말을 검 (시연 ②, 대화 탭) |
| **함께 나이 드는 기념일** | 1만 km마다 알림음 후 "나 오늘 {n}만 km 넘었어", 출고 기념일마다 "나 벌써 {n}살 됐어" — 알림음과 덧붙이는 말이 성격마다 다름 (시연 ④, persona/Celebrations.kt) |

## 구조 (제안서 7·8장과 같음)
```
Vehicle SDK(조회) ─┐                       ┌─ Personality Engine (persona/PersonaEngine.kt) → 장기적 성격 4축
NaviHelper ────────┼─ CarCompanion ───────┼─ Mood Engine (persona/MoodEngine.kt)          → 일시적 기분
                   │  (life/)              ├─ Memory (persona/CarLife.kt, life/LifeStore.kt) → 기억·성장 기록
                   │                       └─ Brain: Claude API (ai/ClaudeBrain.kt) / 규칙 대사 (persona/Dialogue.kt)
                   └─ Voice (voice/Voice.kt): 안드로이드 기본 TTS·음성 인식 (Pleos STT/TTS 공개 시 교체)
```

| 성격 축 | 계산에 쓰는 데이터 |
|---|---|
| 모험심 | 최근 30일 처음 간 곳 비율, 목적지 다양성, 50km 이상 장거리 비율 |
| 익숙함 사랑 | 상위 2개 목적지 반복 비율, 처음 간 곳이 적은 정도 |
| 노련함 (→ 말투) | 연식(차대번호 10번째 글자) + 누적 주행거리 |
| 활동성 | 주당 주행 횟수, 주당 주행거리 |

- 주행이 끝날 때마다 목표 성격 쪽으로 **15%씩만** 움직여 급변하지 않습니다 (`PersonaEngine.STEP`).
- 기분은 성격을 전혀 보지 않고 차 상태만 봅니다. 성격은 "그 기분을 어떤 말투로 표현할지"에만 쓰입니다.
- 경고가 있으면 의인화 문장과 별도로 `[차량 정보] 정확한 내용과 조치`를 붙입니다.
- 운전 중에는 차의 말을 한 문장으로 줄이고, 경고가 아닌 선제 발화는 5분에 한 번만 합니다.

## 쓰는 Pleos API (조회만, 제어 API 없음)
`Odometer.getCurrentDistanceDriven` · `getTripCurrentDrivingDistance` · `getTripCurrentDrivingTime` ·
`CarInfo.getVINInfo` · `EvBattery.getEvBatteryStateOfCharge` · `Fuel.getFuelPercent` · `Tire.getLowTirePressure` ·
`Safety.hasLowWasherFluidWarning` · `getEngineOilLevelState` · `getBrakeFluidLevelState` · `HVAC.getHvacTemperatureCurrent` ·
`Wiper.getCurrentWiperState` · `DrivingMode.getCurrentDrivingModeState` · `NaviHelper`(목적지·도착·현재 위치 이벤트)

## SDK 공개 시 확장 (지금은 동작하는 척 하지 않음)
- Pleos **TTS/STT**: 지금은 안드로이드 기본 엔진 → `Voice` 인터페이스만 새로 구현하면 교체
- **FusedLocation**: 지금은 내비 목적지 좌표로 지역 판단 → 목적지 없이 달린 길도 지역으로 기록
- **Gleo AI** 연동: 음성 비서에서 "내 차 기분 어때?" 호출

## 실행 방법
[docs/GUIDE.md](docs/GUIDE.md) — Android Studio 설치부터 GitHub 업로드, APK 자동 빌드, Pleos 에뮬레이터 실행, 시연 영상 대본까지.
