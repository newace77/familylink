#!/usr/bin/env bash
set -euo pipefail

CHILD_PKG="com.familylink.child"
ACCESSIBILITY_SERVICE="${CHILD_PKG}/.service.ChildAccessibilityService"
DEVICE_ADMIN_RECEIVER="${CHILD_PKG}/.service.ChildDeviceAdminReceiver"

echo "========================================================"
echo " FamilyLink Plus - 자녀 스마트폰 원클릭 보호 권한 설정 (ADB)"
echo "========================================================"

if ! command -v adb >/dev/null 2>&1; then
  echo "[오류] adb 명령어를 찾을 수 없습니다. Android SDK Platform-Tools를 확인해주세요."
  exit 1
fi

echo "[1/5] 사용정보 접근 권한(PACKAGE_USAGE_STATS) 및 오버레이 권한(SYSTEM_ALERT_WINDOW) 부여..."
adb shell appops set "${CHILD_PKG}" GET_USAGE_STATS allow || true
adb shell appops set "${CHILD_PKG}" SYSTEM_ALERT_WINDOW allow || true

echo "[2/5] 백그라운드 위치 및 알림 런타임 권한 부여..."
adb shell pm grant "${CHILD_PKG}" android.permission.ACCESS_FINE_LOCATION || true
adb shell pm grant "${CHILD_PKG}" android.permission.ACCESS_COARSE_LOCATION || true
adb shell pm grant "${CHILD_PKG}" android.permission.ACCESS_BACKGROUND_LOCATION || true
adb shell pm grant "${CHILD_PKG}" android.permission.POST_NOTIFICATIONS || true

echo "[3/5] 접근성 서비스(ChildAccessibilityService) 자동 활성화..."
CURRENT_SERVICES=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
if [[ "${CURRENT_SERVICES}" == "null" || -z "${CURRENT_SERVICES}" ]]; then
  adb shell settings put secure enabled_accessibility_services "${ACCESSIBILITY_SERVICE}"
elif [[ "${CURRENT_SERVICES}" != *"${ACCESSIBILITY_SERVICE}"* ]]; then
  adb shell settings put secure enabled_accessibility_services "${CURRENT_SERVICES}:${ACCESSIBILITY_SERVICE}"
fi
adb shell settings put secure accessibility_enabled 1

echo "[4/5] 기기 관리자(Device Admin) 및 삭제 방지(Device Owner) 활성화 시도..."
adb shell dpm set-active-admin "${DEVICE_ADMIN_RECEIVER}" || true
adb shell dpm set-device-owner "${DEVICE_ADMIN_RECEIVER}" || echo "  (참고: 이미 계정이 등록된 단말에서는 일반 기기 관리자 + 접근성 TamperGuard로 삭제를 차단합니다.)"

echo "[5/5] 배터리 최적화 예외 등록..."
adb shell dumpsys deviceidle whitelist "+${CHILD_PKG}" || true

echo "✅ 자녀 스마트폰 보호 설정이 완료되었습니다!"
