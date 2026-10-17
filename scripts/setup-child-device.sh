#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
APK_PATH="${PROJECT_ROOT}/app-child/build/outputs/apk/debug/app-child-debug.apk"

CHILD_PKG="com.familylink.child"
ACCESSIBILITY_SERVICE="${CHILD_PKG}/.service.ChildAccessibilityService"
DEVICE_ADMIN_RECEIVER="${CHILD_PKG}/.service.ChildDeviceAdminReceiver"

ADB_BIN=""
if command -v adb >/dev/null 2>&1; then
  ADB_BIN="$(command -v adb)"
elif [[ -x "${HOME}/Library/Android/sdk/platform-tools/adb" ]]; then
  ADB_BIN="${HOME}/Library/Android/sdk/platform-tools/adb"
else
  echo "[오류] adb 명령어를 찾을 수 없습니다. Android SDK Platform-Tools를 확인해주세요."
  exit 1
fi

echo "========================================================"
echo " FamilyLink Plus - 자녀 스마트폰 원클릭 설치 및 권한 설정 (ADB)"
echo "========================================================"

echo "[0/5] 연결된 안드로이드 기기 확인 중..."
"${ADB_BIN}" devices

if [[ -f "${APK_PATH}" ]]; then
  echo "[설치] 자녀용 APK(${APK_PATH})를 기기에 자동 설치합니다..."
  "${ADB_BIN}" install -r "${APK_PATH}"
fi

echo "[1/5] 사용정보 접근 권한(PACKAGE_USAGE_STATS) 및 오버레이 권한(SYSTEM_ALERT_WINDOW) 부여..."
"${ADB_BIN}" shell appops set "${CHILD_PKG}" GET_USAGE_STATS allow || true
"${ADB_BIN}" shell appops set "${CHILD_PKG}" SYSTEM_ALERT_WINDOW allow || true

echo "[2/5] 백그라운드 위치 및 알림 런타임 권한 부여..."
"${ADB_BIN}" shell pm grant "${CHILD_PKG}" android.permission.ACCESS_FINE_LOCATION || true
"${ADB_BIN}" shell pm grant "${CHILD_PKG}" android.permission.ACCESS_COARSE_LOCATION || true
"${ADB_BIN}" shell pm grant "${CHILD_PKG}" android.permission.ACCESS_BACKGROUND_LOCATION || true
"${ADB_BIN}" shell pm grant "${CHILD_PKG}" android.permission.POST_NOTIFICATIONS || true

echo "[3/5] 접근성 서비스(ChildAccessibilityService) 자동 활성화..."
CURRENT_SERVICES=$("${ADB_BIN}" shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ "${CURRENT_SERVICES}" == "null" || -z "${CURRENT_SERVICES}" ]]; then
  "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${ACCESSIBILITY_SERVICE}"
elif [[ "${CURRENT_SERVICES}" != *"${ACCESSIBILITY_SERVICE}"* ]]; then
  "${ADB_BIN}" shell settings put secure enabled_accessibility_services "${CURRENT_SERVICES}:${ACCESSIBILITY_SERVICE}"
fi
"${ADB_BIN}" shell settings put secure accessibility_enabled 1

echo "[4/5] 기기 관리자(Device Admin) 및 삭제 방지(Device Owner) 활성화 시도..."
"${ADB_BIN}" shell dpm set-active-admin "${DEVICE_ADMIN_RECEIVER}" || true
"${ADB_BIN}" shell dpm set-device-owner "${DEVICE_ADMIN_RECEIVER}" || echo "  (참고: 이미 구글 계정이 로그인된 단말에서는 일반 기기 관리자 + 접근성 TamperGuard로 삭제를 차단합니다.)"

echo "[5/5] 배터리 최적화 예외 등록..."
"${ADB_BIN}" shell dumpsys deviceidle whitelist "+${CHILD_PKG}" || true

echo "✅ 자녀 스마트폰 앱 설치 및 보호 권한 설정이 모두 완료되었습니다!"
