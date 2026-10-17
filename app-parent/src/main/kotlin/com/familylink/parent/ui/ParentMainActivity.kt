package com.familylink.parent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.data.FamilyRepositoryProvider
import com.familylink.model.ControlPolicy
import com.familylink.model.DeviceHealth
import com.familylink.parent.notification.ParentNotificationHelper
import com.familylink.parent.ui.tabs.AlertsAndSafeZonesTab
import com.familylink.parent.ui.tabs.LocationUsageMapTab
import com.familylink.parent.ui.tabs.PolicyControlTab
import com.familylink.parent.ui.tabs.WebHistoryTimelineTab

class ParentMainActivity : ComponentActivity() {

    private val controller by lazy {
        ParentDashboardController(FamilyRepositoryProvider.getInstance(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1D4ED8),
                    secondary = Color(0xFF0F766E),
                    background = Color(0xFFF1F5F9),
                    surface = Color.White
                )
            ) {
                ParentMainScreen(
                    controller = controller,
                    onPostSystemNotification = { alert ->
                        ParentNotificationHelper.showNotification(this, alert)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentMainScreen(
    controller: ParentDashboardController,
    onPostSystemNotification: (com.familylink.model.ParentAlert) -> Unit
) {
    val policy by controller.policy.collectAsState()
    val health by controller.deviceHealth.collectAsState()
    val staySessions by controller.staySessions.collectAsState()
    val selectedSession by controller.selectedStaySession.collectAsState()
    val webLogs by controller.webLogs.collectAsState()
    val alerts by controller.alerts.collectAsState()
    val safeZones by controller.safeZones.collectAsState()

    var currentTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "FamilyLink Plus · 부모 통합 제어",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "${policy.childName} · 사용시간대 ${policy.formattedAllowedWindow()} · ${policy.alertIntervalMinutes}분 알림",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.88f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF1E3A8A),
                        titleContentColor = Color.White
                    )
                )
                ChildDeviceStatusBanner(policy = policy, health = health)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val tabs = listOf(
                    "🗺️ 위치+사용지도",
                    "🌐 웹·유튜브",
                    "⏰ 시간·앱 제어",
                    "🔔 30분알림·안심존"
                )
                tabs.forEachIndexed { index, label ->
                    NavigationBarItem(
                        selected = currentTabIndex == index,
                        onClick = { currentTabIndex = index },
                        icon = { Text(label.take(2), fontSize = 16.sp) },
                        label = { Text(label.drop(3), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTabIndex) {
                0 -> LocationUsageMapTab(
                    staySessions = staySessions,
                    safeZones = safeZones,
                    selectedSession = selectedSession,
                    onSelectSession = controller::selectStaySession
                )
                1 -> WebHistoryTimelineTab(
                    webLogs = webLogs
                )
                2 -> PolicyControlTab(
                    policy = policy,
                    health = health,
                    onToggleInstantLock = controller::toggleInstantLock,
                    onGrantBonusTime = controller::grantBonusTime,
                    onUpdateTimeWindow = controller::updateAllowedTimeWindow,
                    onUpdateDailyLimit = controller::updateDailyLimitMinutes,
                    onUpdateAlertInterval = controller::updateAlertIntervalMinutes,
                    onUpdateAppMode = controller::setAppAccessMode
                )
                3 -> AlertsAndSafeZonesTab(
                    alerts = alerts,
                    safeZones = safeZones,
                    alertIntervalMinutes = policy.alertIntervalMinutes,
                    onSimulateMilestoneAlert = {
                        val emitted = controller.simulateNextUsageMilestone()
                        if (emitted != null) {
                            onPostSystemNotification(emitted)
                        }
                    },
                    onAcknowledgeAlert = controller::acknowledgeAlert,
                    onAddSafeZone = controller::addSafeZone,
                    onRemoveSafeZone = controller::removeSafeZone
                )
            }
        }
    }
}

@Composable
private fun ChildDeviceStatusBanner(
    policy: ControlPolicy,
    health: DeviceHealth
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "📍 ${health.currentPlaceName} · 실행 중: ${health.currentForegroundApp}",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "🔋 ${health.batteryPercent}% · 🔊 ${health.ringerState.label} · 📡 ${health.networkState.label}",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
        Surface(
            color = if (policy.isImmediateLocked) Color(0xFFDC2626) else Color(0xFF2563EB),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = if (policy.isImmediateLocked) "🔒 즉시잠금중" else "오늘 ${health.totalUsedMinutesToday}분 사용",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
