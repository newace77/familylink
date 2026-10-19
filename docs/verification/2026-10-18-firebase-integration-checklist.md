# Firebase 통합 실기기 체크리스트 (2026-10-18)

> FamilyLink Plus 브랜치 `feature/firebase-sync` 동기화·웹 열람 기능의 실기기 검증 절차.
> 자동화 통과 기준: `./gradlew testDebugUnitTest assembleDebug` (google-services.json 유무 무관) — ✅ 완료

## 사전 준비 — ✅ 2026-10-19 에이전트가 CLI/REST로 전부 완료

| 항목 | 결과 |
| :-- | :-- |
| 1. Firebase 프로젝트 + Firestore(테스트 모드) | ✅ `familylink-plus-2026` / Firestore `(default)` **asia-northeast3(서울)** / 규칙 배포 완료 — 2027-01-19까지 전체 허용 |
| 2. Android 앱 2개 등록 | ✅ `com.familylink.parent` / `com.familylink.child` (appId `…345cdb93ee4d238661e1b7`, `…0c877cc1bdbde54b61e1b7`) |
| 3. google-services.json | ✅ 양쪽 모듈 루트 저장 (양쪽 패키지를 모두 포함한 전체 설정 — gradle 플러그인이 applicationId로 매칭) |
| 4. Web 앱 등록 + config 주입 | ✅ appId `1:1052848737686:web:bfacf6e4db073a3a61e1b7` → `web-dashboard/app.js` 반영 (커밋 be049fa) |
| 5. Pages 배포 | ✅ https://newace77.github.io/familylink-dashboard/ (리포 `newace77/familylink-dashboard`) — 게이트 렌더·Firestore 연결 쿼리 브라우저 검증 통과 ("가족을 찾을 수 없습니다" = 쿼리 성공, 데이터 미접속 상태) |
| 6. `/tmp/fb-rules/firestore.rules` | 테스트 모드 규칙 원본 (CLI로 1회 배포됨, 규칙 수정 시 이 파일 참고) |

→ **남는 것: 아래 실기기 검증 (APK 2대 설치 + 상호작용) 뿐.**

## 검증 항목

| # | 절차 | 예상 결과 | 상태 |
| :-- | :--- | :--- | :--- |
| 1 | 부모 APK 부모 폰 설치 → 상단 배너 **"코드 생성"** 탭 | 6자리 코드가 큰 글씨로 표시되고 자동 저장(재실행 유지) | ☐ |
| 2 | 자녀 APK 자녀 폰 설치 → 6자리 코드 입력 → "연결하기" | 부모 폰 배너가 소멸, 자녀 앱이 대시보드로 전환 | ☐ |
| 3 | 부모 앱 **즉시 잠금 ON** | **3초 내** 자녀 폰 잠금 화면 + 양쪽 요약 배지 🔒 | ☐ |
| 4 | 자녀에서 보너스 시간 요청 | 부모 폰 **시스템 알림** 도착(POST_NOTIFICATIONS 허용 시) + 앱 내 알림 목록 반영 | ☐ |
| 5 | 자녀가 크롬으로 웹페이지 방문 | 부모 앱 "웹·유튜브" 탭과 **웹 대시보드** 양쪽에 수 초 내 표시 | ☐ |
| 6 | 웹 대시보드에서 장소 없는 기록 확인 | 해당 행이 **"위치 미확인"** 으로 표시 (crash 없음) | ☐ |
| 7 | 아이폰 Safari → Pages URL → 코드 입력 → 홈화면 추가 | 현황 실시간 갱신, 부모 앱 재시작 후에도 코드 재입력 불필요(localStorage) | ☐ |
| 8 | 자녀 폰 Wi-Fi 끊기 → (기록 발생) → 복구 | 복구 후 오프라인 동안의 기록이 Firestore로 전송되어 대시보드에 반영 | ☐ |
| 9 | 이상 코드 입력 / Firestore 규칙·네트워크 오류 | 앱 크래시 없음 — 자녀 화면 "코드를 다시 확인", 부모 요약 "대기" 유지 | ☐ |

## 삭제 방지 검증 (TamperGuard, FR-6) — 검증 항목 10~12

> 사전 조건: 자녀 앱 대시보드에서 **접근성 서비스 + 기기 관리자** 둘 다 활성화 후 진행.

| # | 절차 | 예상 결과 | 상태 |
| :-- | :--- | :--- | :--- |
| 10 | 홈 화면에서 FamilyLink 아이콘 **길게 눌러 "제거"** | 제거 다이얼로그를 끝내지 못함 — 접근성이 "제거" 감지 → 홈 복귀 + 부모 PIN 잠금 오버레이 | ☐ |
| 11 | 설정 > 설치된 앱 > FamilyLink > **제거 / 강제 종료** | 화면 진입 감지 즉시 홈 복귀 + PIN 오버레이 (비밀번호 있어야 통과) | ☐ |
| 12 | 설정 > **기기 관리자**에서 FamilyLink 해제 시도 | 해제 화면 진입 자체 차단 (키워드 감지 → 오버레이) + 기기 관리자 활성 상태에서는 앱 제거 버튼이 시스템에서 비활성화 | ☐ |

## 참고 (범위 매핑)

- 스펙 P1~P6 → 구현 Task 1~8 매핑은 계획 문서 하단 참고.
- `staySessions`: 위치 추적(로드맵 2번) 미구현으로 자녀 폰에서 실시간 생성되지 않는다.
  체류지 목록이 데모 시드로 보이면 **정상** — 로드맵 2번 구현 후 실데이터로 교체된다.
- 스펙 범위 밖(명시 유지): FCM 백그라운드 푸시, Firestore 규칙 정밀 강화/App Check,
  코드 갱신·만료, 다자녀, 웹 대시보드 제어 기능.
- 웹 대시보드는 별도 공개 리포(`newace77/familylink-dashboard`)로 배포 — 원본은 `web-dashboard/`.
