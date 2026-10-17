# FamilyLink Plus — Firebase 실시간 동기화 + 웹 대시보드 설계 (Spec)

- 작성일: 2026-10-18
- 상태: 설계 승인 완료 (Section 1–4 사용자 승인)
- 범위: 부모↔자녀 안드로이드 앱 실시간 동기화 + 아이폰/GitHub Pages 열람 대시보드

---

## 1. 문제 정의

현재 부모앱(`app-parent`)과 자녀앱(`app-child`)은 서로 다른 프로세스에서
`LocalFamilySyncRepository`(메모리 StateFlow)만 사용한다. 두 앱 사이에 전달 경로가
전혀 없어 다음이 동작하지 않는다.

- 부모 앱에서 "즉시 잠금" 설정 → 자녀 폰에 아무 영향 없음
- 자녀 폰의 우회 감지·마일스톤·보너스 요청 알림 → 부모에게 전달 불가
- 웹·유튜브 기록, 체류 지점 → 부모 앱 지도/타임라인에 실제 데이터 못 반영

추가 제약: **어머니 폰은 아이폰**이므로 안드로이드 부모 앱을 쓸 수 없고,
부모님 두 분 모두 웹페이지(GitHub)에서 자녀 사용 현황을 확인할 수 있어야 한다.

## 2. 목표 / 비목표

### 목표 (In Scope)

1. 부모 앱 정책 변경(즉시 잠금·시간대·일일 한도·앱 규칙·보너스)이 자녀 폰에 실시간 적용
2. 자녀 폰 이벤트(우회 감지·마일스톤 알림·안심존 진출입·보너스 요청)가 부모 앱에 실시간 도착 + 시스템 알림 표시
3. 자녀의 웹·유튜브 기록과 체류 지점이 부모 앱 지도·타임라인에 실제 데이터로 표시
4. GitHub Pages 웹 대시보드에서 아이폰으로 자녀 현황 **열람** (읽기 전용)
5. 6자리 코드로 두 안드로이드 기기 페어링 + 웹 대시보드 접근 인증

### 비목표 (Out of Scope — 명시적으로 연기)

- FCM 백그라운드 푸시 (앱이 완전히 종료된 상태의 알림 수신)
- Firestore 보안 규칙 정밀 강화 / App Check
- 6자리 코드 주기적 갱신·만료
- 다자녀 지원 (1가족=1자녀 가정)
- 웹 대시보드에서의 제어 기능 (열람 전용으로 확정)

## 3. 확정된 설계 결정

| 항목 | 결정 |
| :--- | :--- |
| 동기화 방식 | Firebase 클라우드 (Firestore, 무료 Spark 플랜) |
| 페어링 | 6자리 코드 (부모 앱 생성 → 자녀 앱 입력) |
| 동기화 범위 | 전체 (정책 + 알림 + 웹기록 + 체류지점) |
| 통합 방식 | 기존 `FamilySyncRepository` 인터페이스 뒤에 Firebase 구현체 교체 (접근 A) |
| 웹 대시보드 역할 | 열람 전용 (읽기만, 제어는 안드로이드 부모 앱 전용) |
| 웹 호스팅/데이터 | GitHub Pages 정적 호스팅 + Firestore JS SDK 직접 구독 |

## 4. Firestore 데이터 모델

데이터의 진실원(Source of Truth)은 Firestore 하나. 모든 모델은
`core-data`의 `ModelJson.kt`에서 `org.json.JSONObject`로 직렬화
(기존 `persistPolicySnapshot` 코드의 일반화)하여 **JSON 문자열/오브젝트로 저장**.

```
families/{familyCode}          ← familyCode = 6자리 숫자 문자열
├── policy:        { ControlPolicy 전체 JSON }   부모 쓰기 / 자녀 읽기
├── deviceHealth:  { DeviceHealth 전체 JSON }    자녀 쓰기 / 부모 읽기
├── safeZones:     [ SafeZone JSON 배열 ]        부모 쓰기 / 자녀 읽기
├── paired:        { childLinked, atMillis }     자녀 쓰기 / 부모 읽기
├── alerts/{alertId}             자녀 생성 (isAcknowledged만 부모 업데이트), 부모 읽음
├── webLogs/{logId}              자녀 생성(불변 append), 부모 읽음
└── staySessions/{sessionId}     자녀 생성(불변 append), 부모 읽음
```

- webLogs/staySessions의 id는 `UUID.randomUUID()` 사용 — 기존 `web-${nowMillis}`
  밀리초 충돌 문제도 이번에 함께 정정한다 (업스트림 기록 파이프라인에 포함).

## 5. 페어링 흐름

```
[부모 앱 최초 실행]                     [자녀 앱 최초 실행]
 "가족 연결 시작" 버튼                  "연결 코드 입력" 화면
   ↓                                      ↓
 6자리 코드 랜덤 생성(중복 재시도)        코드 입력
   ↓                                      ↓
 families/{code} 문서 생성               Firestore에서 코드 존재 확인
   ↓                                      ↓
 화면에 코드 표시                        families/{code}/paired childLinked=true
   ↓                                      ↓
 대시보드 "자녀 연결됨 ✅" 표시           대시보드 전환 + 코드 로컬 영구 저장
```

- 페어링 전: 기존 `DemoScenarioSeed` 데모 동작 그대로 유지 (에이전트 데모 보존)
- 페어링 후: 코드가 양쪽 기기에 로컬 영구 저장 (접속 정보로 앱 재실행 시 자동 재연결)

## 6. 실시간 동기화 메커니즘

- 양쪽 앱 모두 `addSnapshotListener`로 필요한 경로 구독 → 기존 `MutableStateFlow`에
  반영 → UI는 기존대로 StateFlow 구독 (UI 코드 수정 최소)
- 자녀: `policy` 리스너 → 변경 시 `PolicyEvaluator` 재평가로 즉시 잠금 적용
- 부모: `deviceHealth·alerts·webLogs·staySessions·paired` 리스너 → UI 갱신,
  새 alert 도착 시 기존 `ParentNotificationHelper`로 시스템 알림 표시

### 쓰기 권한 매트릭스 (모두 단일 작성자 → 별도 충돌 해소 불필요)

| 데이터 | 쓰기 | 읽기 |
| :--- | :--- | :--- |
| policy, safeZones | 부모만 | 자녀 |
| deviceHealth | 자녀만 | 부모 |
| alerts | 자녀 생성 / 부모는 acknowledged 업데이트만 | 양쪽 |
| webLogs, staySessions | 자녀만(불변 append) | 부모 |

### 오프라인/장애 처리

- Firestore SDK 오프라인 캐시: 끊기면 큐잉 → 재접속 시 자동 전송
- 보조 로컬 폴백: 기존 JSON 정책 저장 유지. Firebase 연결 실패 시 마지막 정책으로
  동작 (자녀 폰이 "잠금 안 풀림" 방향이 안전)
- 모든 리스너/네트워크 호출은 `runCatching`으로 감싸 크래시 방지

### 알림 경로

- 앱 프로세스 생존 중: Firestore 리스너 → 즉시 시스템 알림 (MVP)
- 앱 완전 종료 후: FCM 필요 → 본 스펙 범위 밖

## 7. 인터페이스 변경

```kotlin
interface FamilySyncRepository {
    // 기존 메서드 전부 유지
    suspend fun createFamilyCode(): String            // 부모
    suspend fun linkFamilyCode(code: String): Boolean // 자녀
    val pairingState: StateFlow<PairingState>         // NOT_PAIRED / WAITING / LINKED
}
```

- `LocalFamilySyncRepository`는 페어링 stub만 추가, 데모 동작 무손상
- `FamilyRepositoryProvider`: Firebase 초기화 가능 && 페어링 저장됨 → Firebase
  구현체 선택, 아니면 Local (데모/오프라인)

## 8. 코드 구조

```
core-data/src/main/kotlin/com/familylink/data/
├── FamilySyncRepository.kt            [수정] 페어링 메서드 3개 + PairingState
├── ModelJson.kt                       [신규] 5종 모델 ↔ JSON 직렬화 일반화
├── FirestoreSchema.kt                 [신규] 컬렉션/필드 상수 + 매핑 헬퍼
├── FirebaseFamilySyncRepository.kt    [신규] 리스너 기반 실시간 구현체
└── LocalFamilySyncRepository.kt       [유지] 페어링 stub

app-parent/
├── [신규] FamilyPairingCard.kt        (코드 생성/표시 UI)
└── [수정] ParentMainActivity          페어링 화면 분기 + alerts 리스너→알림 연동

app-child/
├── [신규] FamilyPairingScreen.kt      (코드 입력 UI)
└── [수정] ChildMainActivity           페어링 화면 분기

[별도 공개 리포] familylink-dashboard   (GitHub Pages)
├── index.html    대시보드 화면 (열람 전용)
├── app.js        Firestore JS SDK onSnapshot 구독 (CDN)
└── manifest.json 홈 화면 추가용 최소 PWA 메타
```

Gradle: `firebase-bom` + `firebase-firestore-ktx` 추가.
`com.google.gms.google-services` 플러그인은 `google-services.json`이 존재할 때만
조건부 적용 → 파일 없으면 기존 데모 빌드 그대로 성립.

## 9. 웹 대시보드 (GitHub Pages, 열람 전용)

- 첫 접속: 6자리 코드 입력 → `localStorage` 저장 → 이후 자동 입장
- 구성: 요약 헤더(오늘 사용시간·잠금 배지·배터리/소리/네트워크), 현재·최근 위치,
  웹·유튜브 최근 기록(간단 검색), 실시간 알림 히스토리
- 갱신: `onSnapshot` 실시간 (새로고침 불필요)
- 데이터는 전부 Firestore에만 존재; 공개 리포에는 코드만 커밋
- Firebase 웹 config는 공개 전제 설계; 접근 키는 6자리 코드 (규칙 강화는 이후 단계)

## 10. 테스트 전략

| 대상 | 방법 |
| :--- | :--- |
| JSON 직렬화 5종 모델 | JVM round-trip 테스트 신규 (핵심 방어선) |
| 기존 repository/로직 테스트 | 전부 유지 — Local 구현 무손상 확인 |
| 페어링 코드 생성/연결 로직 | JVM 테스트 (문서 생성 규칙, 재시도) |
| 실기기 통합 | 2대 시나리오 체크리스트 (부모 잠금→자녀 잠깐 / 자녀 우회→부모 알림 / 기록 업스트림 / 웹 열람) |

## 11. 구현 순서 (단계마다 커밋)

- P1: Gradle 의존성 + `ModelJson`/`FirestoreSchema` (스키마·직렬화 먼저)
- P2: 페어링 (부모 생성 ↔ 자녀 연결 + UI)
- P3: 정책 다운스트림 (부모→자녀 실시간 잠금/규칙)
- P4: 이벤트·기록 업스트림 (자녀→부모 알림 + 웹/유튜브/체류지점 기록) — 이 단계에
  웹기록 id를 UUID로 정정 포함
- P5: 웹 대시보드 (별도 리포 + GitHub Pages 배포 가이드)
- P6: 통합 검증 (실기기 시나리오 체크리스트 실행)

Firebase 초기화 실패 시에도 P1이 데모 빌드를 보호하므로 순서가 중요.

## 12. 성공 기준

1. 부모 폰에서 "즉시 잠금" ON → 3초 내 자녀 폰 잠금 화면
2. 자녀가 설정 변경 시도 → 부모 폰 시스템 알림 도착
3. 자녀가 크롬에서 웹페이지 열람 → 부모 앱 타임라인과 웹 대시보드에 표시
4. 아이폰 사파리에서 웹 대시보드 URL 접속 + 코드 입력 → 현황 실시간 확인
5. `./gradlew testDebugUnitTest assembleDebug` 전부 통과 (json 파일 유무 무관)

## 13. 리스크

| 리스크 | 완화 |
| :--- | :--- |
| 6자리 코드 열거 시도 (10^6) | MVP 수용 — 규칙 강화 이후 단계 (App Check 등) |
| Firebase 지역 지연/연결 끊김 | 오프라인 캐시 + 로컬 폴백 (안전 방향 우선) |
| 알림이 앱 종료 시 유실 | FCM 2단계 연기 승인됨 |
| google-services.json 미설치로 빌드 실패 | 조건부 플러그인 적용으로 방어 |
