package com.familylink.child.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.familylink.data.FamilyRepositoryProvider
import com.familylink.data.PlaceNameResolver
import com.familylink.data.ResolvedPlace
import com.familylink.data.StaySessionTicker
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
    private var locationTicker: StaySessionTicker? = null

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
        serviceScope.launch(Dispatchers.Main.immediate) {
            // 진행 중 세션 마감 기록: 세션 자체는 생성 시 이미 기록됐으므로 마감본만 남긴다.
            locationTicker?.finalize(System.currentTimeMillis())?.let { completed ->
                repositoryForTicker().recordStayPointSession(completed)
            }
        }.invokeOnCompletion {
            serviceScope.cancel()
        }
        super.onDestroy()
    }

    private fun repositoryForTicker() = FamilyRepositoryProvider.getInstance(applicationContext)

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

                    // ---- 위치 수집 (로드맵 2: 실 GPS 체류 지점) ----
                    val hasLocationPermission = ContextCompat.checkSelfPermission(
                        this@ChildMonitoringForegroundService, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    var resolvedPlace: ResolvedPlace? = null
                    if (hasLocationPermission) {
                        resolvedPlace = runCatching { resolveCurrentPlace() }.getOrNull()
                    }

                    val placeNameForTick = resolvedPlace?.placeName
                        ?: StaySessionTicker.UNRESOLVED_PLACE

                    repository.updateDeviceHealth { current ->
                        current.copy(
                            batteryPercent = batteryPct,
                            isCharging = isCharging,
                            ringerState = ringerState,
                            networkState = networkState,
                            locationPermissionEnabled = hasLocationPermission,
                            currentPlaceName = resolvedPlace?.placeName ?: StaySessionTicker.UNRESOLVED_PLACE,
                            currentLatitude = resolvedPlace?.latitude ?: current.currentLatitude,
                            currentLongitude = resolvedPlace?.longitude ?: current.currentLongitude,
                            totalUsedMinutesToday = totalMinutes,
                            lastUpdatedMillis = System.currentTimeMillis()
                        )
                    }

                    // 체류 세션 갱신 (장소 변화 시 마감 + 신규 개설)
                    resolvedPlace?.let { place ->
                        val ticker = locationTicker ?: StaySessionTicker(
                            childId = repository.deviceHealth.value.childId
                        ).also { locationTicker = it }

                        ticker.onTick(
                            nowMillis = System.currentTimeMillis(),
                            latitude = place.latitude,
                            longitude = place.longitude,
                            placeName = place.placeName,
                            address = place.address
                        ).forEach { session ->
                            repository.recordStayPointSession(session)
                        }
                    }

                    repository.recordUsageProgress(
                        totalUsedMinutesToday = totalMinutes,
                        currentPlaceName = placeNameForTick,
                        currentAppName = repository.deviceHealth.value.currentForegroundApp,
                        appUsagesToday = usageStatsMap
                    )
                }
                delay(15_000L)
            }
        }
    }

    /**
     * 실 GPS 수집 → 장소명 해석.
     * 1) Kakao 키(Local.properties 빌드 유입)가 있으면 카테고리 검색
     * 2) 기기 Geocoder 지원이면 역지오코딩
     * 3) 250m 내 알려진 랜드마크
     * 4) 마지막 폴백: "위치 미확인"
     */
    private fun resolveCurrentPlace(): ResolvedPlace {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val latest: Location? = runCatching {
            listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            ).mapNotNull { provider ->
                if (locationManager?.isProviderEnabled(provider) != true) null
                else runCatching { locationManager?.getLastKnownLocation(provider) }.getOrNull()
            }.maxByOrNull { it.time }
        }.getOrNull()

        if (latest == null) {
            return ResolvedPlace(StaySessionTicker.UNRESOLVED_PLACE, "", 0.0, 0.0)
        }

        val kakaoKey = retrieveKakaoKey()
        val resolved = PlaceNameResolver.resolveKoreanPlaceName(
            latitude = latest.latitude,
            longitude = latest.longitude,
            kakaoRestApiKey = kakaoKey
        )
        return if (resolved.placeName == StaySessionTicker.UNRESOLVED_PLACE) {
            resolveViaDeviceGeocoder(latest) ?: resolved
        } else {
            resolved
        }
    }

    /** 기기 내장 Geocoder 역지오코딩 (Samsung 폰 대부분 동작). 동기 API는 deprecated지만 폰 내 동작 확실. */
    @Suppress("DEPRECATION")
    private fun resolveViaDeviceGeocoder(location: Location): ResolvedPlace? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            Geocoder(this)
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    val feature = listOfNotNull(
                        address.featureName,
                        address.thoroughfare,
                        address.locality
                    ).firstOrNull { !it.isNullOrBlank() }
                        ?: address.getAddressLine(0)?.take(40)
                    ResolvedPlace(
                        placeName = feature?.takeIf { it.isNotBlank() } ?: StaySessionTicker.UNRESOLVED_PLACE,
                        address = address.getAddressLine(0) ?: "",
                        latitude = location.latitude,
                        longitude = location.longitude
                    )
                }
        }.getOrNull()
    }

    private fun retrieveKakaoKey(): String? = null
    // TODO: local.properties kakaoRestApiKey → BuildConfig 주입 시 여기서 반환 (스펙 범위 밖)

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
