# FamilyLink Plus 웹 대시보드 (열람 전용)

어머니의 **아이폰 Safari**에서 아이 사용현황을 실시간으로 보는 GitHub Pages 정적 페이지입니다.
**제어 기능은 전혀 없습니다** — 잠금·보너스 같은 제어는 FamilyLink Plus 부모 앱에서만 가능합니다.

## 준비물 (8단계 배포 가이드)

1. **Firebase 콘솔 → 프로젝트 설정 → 내 앱 → Web 앱 추가** (`</>` 아이콘) → 표시되는
   `firebaseConfig` 객체를 복사합니다.

2. **`app.js` 파일을 열고** 상단의 `FIREBASE_CONFIG = null;`을 복사한 값으로 교체:

   ```js
   const FIREBASE_CONFIG = {
     apiKey: "AIza...",
     authDomain: "your-app.firebaseapp.com",
     projectId: "your-app",
     storageBucket: "...",
     messagingSenderId: "...",
     appId: "..."
   };
   ```

3. **GitHub에 새 공개 리포를 만듭니다** (예: `familylink-dashboard`). ⚠️
   가족 코드가 아니며 데이터도 이 리포에 없지만, 리포 자체는 공개여야 Pages를 무료로
   쓸 수 있으므로 리포 이름만 공개되면 됩니다.

4. **이 폴더(`web-dashboard`) 안의 파일 3개** (`index.html`, `app.js`, `manifest.json`)을
   그 리포 루트에 push 합니다.

   ```bash
   git clone https://github.com/<계정>/familylink-dashboard
   cd familylink-dashboard
   cp ~/Documents/Code/familylink/web-dashboard/* .
   git add . && git commit -m "deploy" && git push origin main
   ```

5. 리포 **Settings → Pages → Source에서 Branch `main` + `(root)` 선택 → Save**.

6. 1~2분 후 `https://<계정>.github.io/familylink-dashboard/` 주소가 발급됩니다.

7. **아이폰 Safari**로 그 주소 접속 → 부모 앱에서 만든 **6자리 가족 코드** 입력 →
   하단 공유 버튼 → **"홈 화면에 추가"** → 앱처럼 사용.

8. 부모 앱(안드로이드)·자녀 앱·아이폰 웹 화면 3곳에서 같은 정책/기록이 보이는지 확인.

## 자주 겪는 문제

| 증상 | 원인/해결 |
| :--- | :--- |
| "설정 누락" 경고 | `app.js`의 `FIREBASE_CONFIG`를 아직 안 넣음 |
| "가족을 찾을 수 없음" | 코드 오타, 또는 부모 앱에서 코드 생성(코드 생성 버튼) 전 |
| 기록이 안 보임 | 부모 앱 상단에 연결 배너(코드 생성)가 남아 있는지, 자녀 앱 페어링 완료 여부 확인 |
| Firestore 오류 | Firebase 콘솔에서 Firestore가 생성되어 있고 테스트 모드(읽기 허용)인지 확인 |

## 보안 노트

- 현재는 스펙 확정 범위: **코드를 아는 사람만 읽을 수 있는 테스트 모드 규칙**.
  규칙 강화·App Check는 스펙 범위 밖(향후 과제)입니다.
