package com.familylink.parent.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.familylink.model.ParentAlert

object ParentNotificationHelper {
    private const val CHANNEL_ID = "familylink_parent_alerts"

    fun showNotification(context: Context, alert: ParentAlert) {
        runCatching {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "FamilyLink Plus 자녀 사용/위치 알림",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "자녀 스마트폰 30분 주기 사용 알림, 안심존 진·출입, 설정 우회 시도를 알립니다."
                }
                manager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(alert.title)
                .setContentText(alert.message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(alert.message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            manager.notify(alert.alertId.hashCode(), notification)
        }
    }
}
