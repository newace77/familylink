package com.familylink.parent.ui.tabs

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.model.AlertType
import com.familylink.model.ParentAlert
import com.familylink.model.SafeZone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsAndSafeZonesTab(
    alerts: List<ParentAlert>,
    safeZones: List<SafeZone>,
    alertIntervalMinutes: Int,
    onSimulateMilestoneAlert: () -> Unit,
    onAcknowledgeAlert: (String) -> Unit,
    onAddSafeZone: (String, Double, Double, Double) -> Unit,
    onRemoveSafeZone: (String) -> Unit
) {
    var newZoneName by remember { mutableStateOf("") }
    var newZoneRadius by remember { mutableStateOf("150") }
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🔔 실시간 부모 알림 센터 (${alertIntervalMinutes}분 주기)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "30분 주기 누적 사용 알림, 안심존 진·출입, 설정 변경 차단 내역입니다.",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Button(onClick = onSimulateMilestoneAlert) {
                            Text("+${alertIntervalMinutes}분 알림 테스트", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        items(alerts, key = { it.alertId }) { alert ->
            val (badgeBg, badgeFg) = when (alert.type) {
                AlertType.USAGE_MILESTONE -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
                AlertType.SAFE_ZONE_ENTER -> Color(0xFFD1FAE5) to Color(0xFF065F46)
                AlertType.SAFE_ZONE_EXIT -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                AlertType.TAMPER_ATTEMPT -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
                AlertType.BONUS_TIME_REQUEST -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
                AlertType.POLICY_LOCK_TRIGGERED -> Color(0xFFF1F5F9) to Color(0xFF334155)
            }

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .background(badgeBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = alert.type.badgeText,
                                color = badgeFg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = timeFormat.format(Date(alert.timestampMillis)),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(alert.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(alert.message, fontSize = 13.sp, color = Color(0xFF334155))
                }
            }
        }

        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📍 안심존(Geofencing) 진·출입 알림 관리", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text(
                        "집·학교·학원 등 지정 반경에 도착하거나 이탈할 때 부모 폰으로 즉시 알립니다.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newZoneName,
                            onValueChange = { newZoneName = it },
                            label = { Text("장소 이름 (예: 영어학원)") },
                            singleLine = true,
                            modifier = Modifier.weight(1.4f)
                        )
                        OutlinedTextField(
                            value = newZoneRadius,
                            onValueChange = { newZoneRadius = it.take(3) },
                            label = { Text("반경(m)") },
                            singleLine = true,
                            modifier = Modifier.weight(0.8f)
                        )
                        Button(
                            onClick = {
                                if (newZoneName.isNotBlank()) {
                                    onAddSafeZone(
                                        newZoneName.trim(),
                                        37.4990,
                                        127.0400,
                                        newZoneRadius.toDoubleOrNull() ?: 150.0
                                    )
                                    newZoneName = ""
                                }
                            }
                        ) {
                            Text("추가")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    safeZones.forEach { zone ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(zone.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("반경 ${zone.radiusMeters.toInt()}m · 진입/이탈 알림 활성", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                            OutlinedButton(onClick = { onRemoveSafeZone(zone.zoneId) }) {
                                Text("삭제", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
