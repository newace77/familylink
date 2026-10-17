package com.familylink.child.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.familylink.data.FamilyRepositoryProvider
import com.familylink.model.NetworkState
import com.familylink.model.RingerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

class ChildMonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createGuardNotification())
        startMonitoringLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoringLoop() {
        val repository = FamilyRepositoryProvider.getInstance(applicationContext)
        serviceScope.launch {
            while (isActive) {
                runCatching {
                    val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                    val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
                    val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                    val ringerState = when (audioManager?.ringerMode) {
                        AudioManager.RINGER_MODE_SILENT -> RingerState.SILENT
                        AudioManager.RINGER_MODE_VIBRATE -> RingerState.VIBRATE
                        else -> RingerState.NORMAL
                    }

                    val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val capabilities = connectivityManager?.getNetworkCapabilities(connectivityManager.activeNetwork)
                    val networkState = when {
                        capabilities == null -> NetworkState.OFFLINE
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkState.WIFI
                        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkState.CELLULAR
                        else -> NetworkState.WIFI
                    }

                    val usageStatsMap = queryTodayUsageMinutes()
                    val totalMinutes = if (usageStatsMap.isNotEmpty()) {
                        usageStatsMap.values.sum()
                    } else {
                        repository.deviceHealth.value.totalUsedMinutesToday
                    }

                    repository.updateDeviceHealth { current ->
                        current.copy(
                            batteryPercent = batteryPct,
                            isCharging = isCharging,
                            ringerState = ringerState,
                            networkState = networkState,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                    }

                    repository.recordUsageProgress(
                        totalUsedMinutesToday = totalMinutes,
                        currentPlaceName = repository.deviceHealth.value.currentPlaceName,
                        currentAppName = repository.deviceHealth.value.currentForegroundApp,
                        appUsagesToday = usageStatsMap
                    )
                }
                delay(15_000L)
            }
        }
    }

    private fun queryTodayUsageMinutes(): Map<String, Int> {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        val now = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startOfDay,
            now
        ).orEmpty()

        return stats
            .filter { it.totalTimeInForeground >= 60_000L }
            .associate { it.packageName to (it.totalTimeInForeground / 60_000L).toInt() }
    }

    private fun createGuardNotification(): Notification {
        val channelId = "familylink_child_guard"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "FamilyLink Plus 자녀 보호 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "부모님이 설정한 사용 시간대 및 안심 위치를 보호합니다."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("FamilyLink Plus 보호 모드 실행 중")
            .setContentText("약속된 사용 시간(10:00~21:00)과 안심 위치를 지키고 있어요")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4101
    }
}
