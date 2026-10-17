package com.familylink.child.service

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import com.familylink.data.FamilyRepositoryProvider
import com.familylink.model.AlertType
import com.familylink.model.ParentAlert

class ChildDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        FamilyRepositoryProvider.getInstance(context).updateDeviceHealth {
            it.copy(deviceAdminEnabled = true, lastUpdatedMillis = System.currentTimeMillis())
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        FamilyRepositoryProvider.getInstance(context).emitAlert(
            ParentAlert(
                alertId = "admin-disable-${System.currentTimeMillis()}",
                type = AlertType.TAMPER_ATTEMPT,
                title = "🚨 기기 관리자 해제 시도 감지",
                message = "자녀 폰에서 FamilyLink Plus 삭제 방지(기기 관리자) 권한 해제가 시도되었습니다."
            )
        )
        return "부모님 승인 없이 FamilyLink Plus 기기 관리자를 해제할 수 없습니다."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        FamilyRepositoryProvider.getInstance(context).updateDeviceHealth {
            it.copy(deviceAdminEnabled = false, lastUpdatedMillis = System.currentTimeMillis())
        }
    }
}
