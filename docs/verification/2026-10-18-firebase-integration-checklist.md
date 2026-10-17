# Firebase 통합 실기기 체크리스트 (2026-10-18)

> FamilyLink Plus 브랜치 `feature/firebase-sync` 동기화·웹 열람 기능의 실기기 검증 절차.
> 자동화 통과 기준: `./gradlew testDebugUnitTest assembleDebug` (google-services.json 유무 무관) — ✅ 완료

## 사전 준비 (8~10분)

1. **Firebase 콘솔**: 프로젝트 생성 → **Firestore Database 생성(테스트 모드)**.
2. **Android 앱 2개 등록**: 패키지 `com.familylink.parent` / `com.familylink.child` 각각 SHA-1 불필요.
3. 각 앱의 `google-services.json`을 다운로드해
   `app-parent/google-services.json`, `app-child/google-services.json`로 저장.
4. **Web 앱 등록** → config 복사 → `web-dashboard/app.js`의 `FIREBASE_CONFIG`에 붙여넣기.
5. APK 설치: `./gradlew :app-parent:assembleDebug :app-child:assembleDebug` → 각 `build/outputs/apk/debug/*.apk`.

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

## 참고 (범위 매핑)

- 스펙 P1~P6 → 구현 Task 1~8 매핑은 계획 문서 하단 참고.
- `staySessions`: 위치 추적(로드맵 2번) 미구현으로 자녀 폰에서 실시간 생성되지 않는다.
  체류지 목록이 데모 시드로 보이면 **정상** — 로드맵 2번 구현 후 실데이터로 교체된다.
- 스펙 범위 밖(명시 유지): FCM 백그라운드 푸시, Firestore 규칙 정밀 강화/App Check,
  코드 갱신·만료, 다자녀, 웹 대시보드 제어 기능.
- 웹 대시보드 배포는 `web-dashboard/README.md`의 8단계 가이드.
