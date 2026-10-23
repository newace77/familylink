# FamilyLink Plus — 부모폰 + 아이폰 동시 테스트 방법 (MacBook)

> 부모 앱과 아이폰은 Firestore를 통해 동기화되므로, 부모 앱 조작 → 아이폰 반영을 나란히 두고 확인한다.
> 아이폰 대시보드는 **열람 전용**이며 제어 버튼은 없는 것이 정상이다.

```
부모폰(Android) ──write──▶ Firestore ◀──read── 아이폰 Safari(웹 대시보드)
```

## 1. 준비 (한 번)

| 대상 | 준비물 | 명령/방법 | 상태 |
| :-- | :-- | :-- | :-- |
| 부모폰 (에뮬레이터) | `Pixel_7` AVD | `emulator -avd Pixel_7 -port 5556` + APK 설치·실행 | ✅ 사용 가능 |
| 아이폰 (대시보드) | `web-dashboard` 폴더 | `cd web-dashboard && python3 -m http.server 8080` | ✅ HTTP 200 확인됨 |
| 아이폰 접속 주소 | 같은 Wi-Fi | `http://192.168.1.9:8080` (맥북 IP) 또는 배포본 Pages | ✅ 둘 다 확인됨 |
| iOS 시뮬레이터 | Xcode | 현재 미설치 → 실기기 Safari 또는 Mac 반응형 모드 사용 | ❌ |

## 2. 동시 테스트 절차

| # | 부모폰 (에뮬레이터) | 아이폰 (Safari) | 통과 기준 |
| :-- | :-- | :-- | :-- |
| 1 | 에뮬레이터 부팅 + 부모 앱 실행 | 대시보드 URL 접속 | 앱이 뜨고, 대시보드가 로드됨 |
| 2 | 상단 배너 **코드 생성** 탭 | 6자리 코드 입력 | 부모 앱에 코드 표시 + 아이폰 대시보드 진입 |
| 3 | 정책 변경 (시간대/한도/잠금) | 화면 관찰 | 수 초 내 자동 갱신 (새로고침 불필요) |
| 4 | — | 하단 공유 → 홈 화면에 추가 | standalone 실행, 코드 유지 |
| 5 | 부모 앱 재시작 | 홈 화면 아이콘 실행 | 코드 재입력 없이 동기화 유지 |

```bash
# 부모폰: 에뮬레이터 + 부모 앱
$HOME/Library/Android/sdk/emulator/emulator -avd Pixel_7 -port 5556 &
adb -s emulator-5556 install -r app-parent/build/outputs/apk/debug/app-parent-debug.apk
adb -s emulator-5556 shell am start -n com.familylink.parent/.ui.ParentMainActivity

# 아이폰: 대시보드 서버
cd web-dashboard && python3 -m http.server 8080
# 아이폰 Safari → http://192.168.1.9:8080  (또는 https://newace77.github.io/familylink-dashboard/)
```

## 3. 기능별 동시 검증

| 기능 | 부모 앱 조작 | 아이폰 기대 결과 | 자녀 앱 필요? |
| :-- | :-- | :-- | :-- |
| 가족 코드/페어링 | 코드 생성 | 코드 입력 → 요약 헤더 표시 | ❌ |
| 사용 시간대/일일 한도 | 값 변경 후 저장 | `10:00~21:00`, `오늘 N분 / M분` 갱신 | ❌ |
| 즉시 잠금 | 스위치 ON/OFF | `🔒 즉시잠금중` 배지 등장/소멸 (~3초) | ❌ |
| 보너스 시간 | `+15분 부여` | 잠금 배지 해제 | ❌ |
| 안심존 | 추가/삭제 | `안심존 N곳` 증감 | ❌ |
| 알림 센터 | `+30분 알림 테스트` | 알림 카드 추가 | ❌ |
| 웹·유튜브 기록 | (자녀가 Chrome 방문) | 제목·URL·장소 카드 + 검색 필터 | ✅ |
| 체류지+앱 사용 | (자녀 위치 기록) | `앱명 N분`, `웹 기록 N건` | ✅ |
| 기기 상태 | — | 배터리% · 소리 · Wi-Fi 표시 | ✅ |
| 열람 전용 확인 | — | 아이폰에 제어 버튼 없음 | ❌ (정상) |

## 4. 자주 막히는 지점

| 증상 | 해결 |
| :-- | :-- |
| 아이폰 "가족을 찾을 수 없음" | 코드 오타, 부모 앱 코드 생성 실패 여부 확인 |
| LAN 접속 안 됨 | 같은 Wi-Fi 확인, 맥 방화벽 허용 → 안 되면 Pages URL 사용 |
| 값이 갱신 안 됨 | 부모 앱이 동기화 중인지 확인, Safari 새로고침 |
| 웹 기록·위치 비어 있음 | 자녀 앱이 실행 중이 아님 (자녀 에뮬 `FamilyLink_Child` 추가) |
| 홈 화면 추가 후 코드 재입력 | 사생활 보호 모드 → 일반 탭에서 추가 |

---

> **참고**: 자녀 앱 기능까지 포함하려면 `FamilyLink_Child` 에뮬레이터(5554)를 하나 더 띄워
> 3화면 동시 확인이 가능하다. 부모 앱만으로도 코드 생성 시 `policy`·`safeZones`가
> Firestore에 기록되므로 요약·시간대·잠금·안심존은 바로 테스트할 수 있다.
> (Firestore 테스트 모드 규칙 만료: 2027-01-19)

---

## 5. 테스트 실행 기록 (2026-10-22 실측)

맥북에서 **부모 앱(`Pixel_7` 에뮬 5556) + 열람 대시보드(agent-browser, `http://localhost:8080`) +
자녀 앱(`FamilyLink_Child` 에뮬 5554)** 3화면 동시 테스트를 수행했다.
아이폰 실기기 대신 브라우저(세션 `familylink`)로 대체, 가족 코드 `586116` 사용.

### 표 1~2 결과 (전 항목 통과)

| # | 항목 | 결과 | 확인 내용 |
| :-- | :-- | :-- | :-- |
| 1 | 가족 코드/페어링 | ✅ PASS | AVD 저장 코드 `586116`으로 LINKED (배너 숨김 정상) |
| 2 | 대시보드 진입 | ✅ PASS | 코드 입력 → 요약 헤더 즉시 표시 |
| 3 | 즉시 잠금 | ✅ PASS | `🔒 즉시잠금중` 배지 + "부모 원격 즉시 잠금" 알림 카드 즉시 등장 |
| 4 | 보너스 +15분 | ✅ PASS | 배지 즉시 해제 |
| 5 | 일일 한도 120→60분 | ✅ PASS | `오늘 N분 / 60분` 실시간 갱신 |
| 6 | 시간대 변경 | ✅ PASS | `10:00~21:00 → 10:00~22:00 → 08:00~22:00 → 20:00~03:00` 즉시 반영 |
| 7 | 안심존 추가 | ✅ PASS | "studyroom"(150m) 추가 → `안심존 3곳 → 4곳` |
| 8 | 알림 테스트 | ✅ PASS | `+30분 알림 테스트` → `⏰ 60분 경과` 신규 카드 (수동 새로고침 0회) |
| 9 | 열람 전용 확인 | ✅ PASS | 대시보드에 제어 UI 없음 (검색창·코드 변경뿐) |
| 10 | 부모 앱 재시작 | ✅ PASS | 재시작·재입력 후에도 동기화 유지 |
| 11 | 웹·유튜브 기록 | ✅ PASS | 자녀 Chrome `m.naver.com` 방문 → 제목·URL·체류 지점 카드 등장 |
| 12 | 기기 상태 | ✅ PASS | 배터리 100% · 소리 · Wi-Fi/CELLULAR · 오늘 N분 자녀에서 갱신 |

### 자녀 차단 동작 (2026-10-22 실측)

- 자녀 에뮬 시계가 23시(=10:00~22:00 밖)인 상태에서 Chrome 진입 시도 → **즉시 홈으로 킥 +
  "사용 가능 시간 외" 잠금 오버레이** 표시 → 시간대 차단이 실동작하는 것을 직접 확인.
- 정책을 `20:00~03:00`(자정 경과 허용)으로 바꾸자 자녀의 Chrome 사용이 허용되고
  웹 기록이 수집됨 → **자정 경과 허용 시간대 로직 실동작 확인**.

### 발견 이슈 (개선 여지)

1. **부모 앱 ANR 재발**: 시간대 저장 직후 "응답 없음" 다이얼로그 발생 (1회). 테스트 자동화
   도구와 병행한 환경이라 실사용 영향은 재확인 필요. — *코드 수정 미정, 관찰 지속*
2. **동일 URL 중복 기록**: 같은 `m.naver.com`이 수 초 간격으로 2~3건 기록됨. → **수정 완료**
   (`55d6feb`: 동일 패키지+동일 URL은 3초 시간창과 무관하게 억제 + 서비스 재시작 시
   최신 로그 기준 이어가기. RED→GREEN 테스트 2건 추가, 전체 테스트 통과)
3. **대시보드 게이트 표시**: 브라우저 재시작 후 코드 재입력은 정상이나, 일부 흐름에서
   `hidden` 게이트가 화면에 겹쳐 보인 케이스 관찰. → **수정 완료** (`55d6feb` /
   배포 저장소 `499da23`: `#gate[hidden]{display:none}` 추가로 UA 스타일 누락 보완.
   실브라우저에서 페어링 후 `display:none` 확인)
4. 자녀 에뮬 시계가 12시간씩 드리프트(상태바 11:10 = 실제 23:10) — AVD 콜드부팅 환경 문제. — *환경 문제, 코드 수정 대상 아님*

> **증빙**: `/tmp/fl-dash-*.png`(대시보드), `/tmp/child*.png`(자녀 잠금 오버레이·Chrome),
> `/tmp/pp*.xml`(부모 uiautomator dump)

### 재실행 기록 (2026-10-23 실측 — 이슈 2·3 수정 이후)

어제 기록 이후 커밋 `55d6feb`(이슈 2·3 수정)·`f2ad12c`를 반영한 APK로 자녀 두 에뮬에
`install -r` 후 재실행, 대시보드(수정 배포본)와 함께 3화면 테스트를 반복했다.

| # | 항목 | 결과 | 확인 내용 |
| :-- | :-- | :-- | :-- |
| 1 | 재설치 후 재실행 | ✅ PASS | 부모·자녀 앱 코드 배너 숨김 유지(LINKED), 자녀 접근성 서비스 유지 |
| 2 | 대시보드 재진입(게이트) | ✅ PASS | 패어링 대기 상태 게이트 표시(fallback) → 코드 입력 후 `#gate` `hidden:true+display:none` |
| 3 | 즉시잠금 | ✅ PASS | 스위치 ON → 대시보드 `🔒 즉시잠금중` + 자녀 "부모님 즉시 잠금" 오버레이 즉시Firestore 반영 |
| 4 | 보너스 +15분 | ✅ PASS | 배지 즉시 해제, 자녀 "보너스 +45분 반영됨", 81분 남음 |
| 5 | 일일 한도 변경 | ✅ PASS | 120→60분 클릭 → 대시보드 `71분 / 60분` 실시간, 120분 원복도 실시간 |
| 6 | 안심존 추가 | ✅ PASS | `testzone` 추가 → `안심존 3곳→4곳` 실시간 + Firestore 문자열 검증 |
| 7 | 안심존 삭제 | ❌ FAIL | UI·로컬에서는 삭제되나 **Firestore에 미반영(2회 재현)** — 아래 신규 이슈 5. 후속 조치: REST PATCH로 테스트 zone 정리(3곳 복구) |
| 8 | +30분 알림 테스트 | ⚠️ 부분 | 클릭 1회 → Firestore에 `milestone-child-1-90` 기록(REST 검증) + 스크롤하면 카드 존재. 단 최상단에 표시되지 않음 — 신규 이슈 5. 2회째 클릭은 emit null이 정상(마일스톤 교차 없음) |
| 9 | 열람 전용 확인 | ✅ PASS | 대시보드에 제어 UI 없음(잠금·부여 버튼 부재), 코드 변경·검색창만 존재 |
| 10 | 웹 기록 중복 억제(이슈 2 수정 검증) | ✅ PASS | 수정 APK로 자녀가 `m.naver.com` 접속·유지(스크롤 포함 30초+) → **기록 1건**(REST 비교: 어제 동작은 23:42~23:43 1분 사이 **9건 중복**) |
| 11 | 시간대 외 차단(부수 관찰) | ✅ PASS | 자녀 기기 시계가 드리프트되어(기기 기준 21:08, 허용 밖) Chrome 진입 시 홈으로 킥백 후 차단 오버레이 — 외 시간대 차단이 실동작 |
| 12 | 대시보드 도구 세션 재시작 | — | agent-browser 세션 리스타트로 localStorage 소멸( 도구 환경 특성). 코드 `586116` 재입력 후 정상 |

### 신규 이슈 (2026-10-23 관찰)

1. **(이슈 5) 안심존 삭제가 Firestore에 반영되지 않음** — 부모 앱 UI에서는 즉시 목록에서 제거되지만
   `removeSafeZone → writeSafeZones()`의 set이 도달하지 않고(2회 재현, REST로 카운트 4 유지),
   몇 초 뒤 families 문서 리스너가 4존 스냅샷을 적용해 UI까지 삭제가 무효화된다.
   `writeSafeZones()`는 Task를 await하지 않아 실패가 묻히지 않는 지점(adding은 동일 경로로 성공).
2. **(이슈 6) 알림 목록 정렬·시드 카드** — 액티비티 재시작할 때마다 데모 시드 알림 3장이 최신
   Firestore 알림보다 위에 존재해 새 카드(예: 90분 경과)가 목록 아래에 배치된다. 알림 즉시 확인이 어렵다.
3. **(이슈 1 보완 관찰)** 오늘 세션에서는 부모 앱 ANR은 재현되지 않았다.
4. **(이슈 4 재발)** 자녀 에뮬 시계가 부모 대비 약 9시간 드리프트(부모 00:51, 자녀 21:08) — AVD 콜드부팅 문제, 이번 세션에서 재발.

> **증빙(10-23)**: `/tmp/child-lock.png`(자녀 즉시잠금), `/tmp/child-unlock.png`(보너스 해제),
> `/tmp/pp-alert*.png`(알림 센터), `/tmp/ch-naver2.png`(자녀 Chrome m.naver.com),
> `/tmp/alerts.json`·`/tmp/weblogs.json`(Firestore REST 검증 원문)

### 이슈 5 수정 기록 (2026-10-23, 견고성 픽스 — TDD)

**결함 특성**: 플래키(3회째 재현 실패) → 단일 근본 원인 확정 대신 실패 경로를 관측·보강하는 견고성 설계.

**관측된 근본 원인 시그니처** (부모 앱 재설치 직후 logcat):
- `GoogleApiManager: SecurityException: Unknown calling package name 'com.google.android.gms'` — 재설치 직후
  Firestore 쓰기 Task가 즉시 실패할 수 있으며, 기존 fire-and-forget `.set()`은 이 실패를 조용히 삼켰다.
- `FireStore OnlineStateTracker: Could not reach Cloud Firestore backend ...` — 백엔드 연결 지연 구간에서도 동일.
- 이 실패 구간에 리스너 스냅샷(이전 서버 값)이 도착하면 UI까지 변경 이전 상태로 되돌려진다(이슈 5 재현).

**수정 내용 (TDD, RED→GREEN)**:
1. `core-data/src/main/kotlin/com/familylink/data/FieldEchoGuard.kt` + `FieldEchoGuardTest` 5 케이스(`EchoGuardTest`):
   대기 중(pending) 로컬 쓰기의 의도값과 다른 서버 스냅샷 적용을 거부한다 — 실패·지연 시 리스너가
   UI를 되돌리는 현상 방지. 쓰기 ack 성공 시 pending 해제, 실패 시 유지, 연속 쓰기는 최신 의도 기준.
2. `FirebaseFamilySyncRepository`:
   - `writesScope`(IO + SupervisorJob)로 `writePolicy`/`writeSafeZones`를 ack + 지수 백오프 재시도
     (0.5/1/2s) 실행, 최종 실패 시 `Log.w`(FamilySyncRepo) 기록.
   - `applyRootDocument`의 POLICY·SAFE_ZONES 적용 전 `echoGuard.shouldApplyServerUpdate` 검사.
   - deviceHealth는 기존 경로 유지(자녀 기기 쓰기, 충돌 모델 상이).

**실기기 검증 (2026-10-23)**: 수정 APK 재설치 후 안심존 추가 4회(fixcheckzone·cyc1·cyc2cyccyc23·cyc3) +
삭제 3회 → **7/7 모두 Firestore 구면 반영**(REST 즉시 교차확인), UI·대시보드(`안심존 3곳`) 일치,
stale-skip 로그 0건. 어제 재현된 "삭제 미반영 + UI 되돌림"은 재현되지 않음.

> **증빙(이슈 5 수정)**: `/tmp/vz*.xml`(uiautomator dump), `/tmp/fam-cy3.json`(REST 원문),
> GoogleApiManager SecurityException 로그(부모 앱 재설치 직후, 21:34:37)
