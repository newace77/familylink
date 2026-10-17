package com.familylink.child.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.model.AppAccessMode
import com.familylink.model.ControlPolicy
import com.familylink.model.DeviceHealth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChildDashboardScreen(
    policy: ControlPolicy,
    health: DeviceHealth,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenUsageStatsSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onEnableDeviceAdmin: () -> Unit,
    onRequestBonusTime: (String) -> Unit,
    onSimulateLockPreview: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val effectiveLimit = policy.effectiveDailyLimitMinutes() ?: 180
    val remainingMinutes = (effectiveLimit - health.totalUsedMinutesToday).coerceAtLeast(0)
    val progress = (health.totalUsedMinutesToday.toFloat() / effectiveLimit.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("FamilyLink Plus · 자녀 보호 에이전트", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "${policy.childName} · 현재 위치: ${health.currentPlaceName}",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E40AF),
                    titleContentColor = Color.White
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF1F5F9))
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("오늘 남은 스마트폰 사용 시간", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Surface(
                                color = Color(0xFFDBEAFE),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text(
                                    text = "사용 가능: ${policy.formattedAllowedWindow()}",
                                    color = Color(0xFF1D4ED8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "${remainingMinutes}분 남음 (오늘 ${health.totalUsedMinutesToday}분 / 총 ${effectiveLimit}분)",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        if (policy.bonusMinutesToday > 0) {
                            Text(
                                text = "🎁 부모님 보너스 시간 +${policy.bonusMinutesToday}분 반영됨",
                                fontSize = 13.sp,
                                color = Color(0xFF059669),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp),
                            color = if (remainingMinutes < 20) Color(0xFFDC2626) else Color(0xFF2563EB),
                            trackColor = Color(0xFFE2E8F0)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "🔔 ${policy.alertIntervalMinutes}분 사용마다 부모님께 위치·사용 요약 알림이 전송됩니다.",
                            fontSize = 12.sp,
                            color = Color(0xFF475569)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    onRequestBonusTime("숙제 및 필요한 확인을 위해 15분 더 사용하고 싶어요!")
                                    scope.launch {
                                        snackbarHostState.showSnackbar("부모님께 +15분 보너스 시간 요청 알림을 보냈습니다.")
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("부모님께 +15분 요청")
                            }
                            OutlinedButton(
                                onClick = onSimulateLockPreview,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("잠금 화면 테스트")
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🛡️ 필수 보호 권한 및 삭제 방지 설정", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            "크롬 웹페이지 확인, 위치+앱 사용 기록 연동, 임의 삭제 방지를 위해 아래 4가지 권한을 활성화합니다.",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                        PermissionActionRow(
                            title = "1. 접근성 서비스 (크롬 URL·유튜브 제목·설정 변경 차단)",
                            enabled = health.accessibilityEnabled,
                            onClick = onOpenAccessibilitySettings
                        )
                        PermissionActionRow(
                            title = "2. 사용정보 접근 권한 (정밀 앱 사용시간 및 30분 알림)",
                            enabled = health.usageStatsEnabled,
                            onClick = onOpenUsageStatsSettings
                        )
                        PermissionActionRow(
                            title = "3. 다른 앱 위에 표시 (시간 외/차단 앱 잠금 오버레이)",
                            enabled = health.overlayPermissionEnabled,
                            onClick = onOpenOverlaySettings
                        )
                        PermissionActionRow(
                            title = "4. 기기 관리자 활성화 (자녀 임의 앱 삭제 방지)",
                            enabled = health.deviceAdminEnabled,
                            onClick = onEnableDeviceAdmin
                        )
                    }
                }
            }

            item {
                Text(
                    "📱 내 스마트폰 앱별 허용 규칙",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            items(policy.appRules.values.toList(), key = { it.packageName }) { rule ->
                val usedMin = health.appUsedMinutesToday[rule.packageName] ?: 0
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(rule.appName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            val quotaInfo = rule.dailyQuotaMinutes?.let { "일일 한도 ${it}분 (오늘 ${usedMin}분 사용)" }
                                ?: "오늘 ${usedMin}분 사용"
                            Text(quotaInfo, fontSize = 12.sp, color = Color(0xFF64748B))
                        }
                        val (badgeBg, badgeFg, badgeLabel) = when (rule.accessMode) {
                            AppAccessMode.ALWAYS_ALLOWED -> Triple(Color(0xFFD1FAE5), Color(0xFF065F46), "상시 허용")
                            AppAccessMode.ALLOWED -> Triple(Color(0xFFDBEAFE), Color(0xFF1E40AF), "시간 내 허용")
                            AppAccessMode.BLOCKED -> Triple(Color(0xFFFEE2E2), Color(0xFF991B1B), "차단됨")
                        }
                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(badgeLabel, color = badgeFg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionActionRow(
    title: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(
                if (enabled) "✅ 정상 작동 중" else "⚠️ 설정 필요",
                fontSize = 11.sp,
                color = if (enabled) Color(0xFF059669) else Color(0xFFD97706)
            )
        }
        OutlinedButton(onClick = onClick) {
            Text("설정 열기", fontSize = 12.sp)
        }
    }
}
