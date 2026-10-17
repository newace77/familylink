package com.familylink.parent.ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.model.AppAccessMode
import com.familylink.model.ControlPolicy
import com.familylink.model.DeviceHealth

@Composable
fun PolicyControlTab(
    policy: ControlPolicy,
    health: DeviceHealth,
    onToggleInstantLock: (Boolean) -> Unit,
    onGrantBonusTime: (Int) -> Unit,
    onUpdateTimeWindow: (Int, Int, Int, Int) -> Unit,
    onUpdateDailyLimit: (Int?) -> Unit,
    onUpdateAlertInterval: (Int) -> Unit,
    onUpdateAppMode: (String, AppAccessMode, Int?) -> Unit
) {
    var startHourText by remember(policy.allowedStartHour) { mutableStateOf(policy.allowedStartHour.toString()) }
    var startMinText by remember(policy.allowedStartMinute) { mutableStateOf(policy.allowedStartMinute.toString()) }
    var endHourText by remember(policy.allowedEndHour) { mutableStateOf(policy.allowedEndHour.toString()) }
    var endMinText by remember(policy.allowedEndMinute) { mutableStateOf(policy.allowedEndMinute.toString()) }
    var customAlertIntervalText by remember(policy.alertIntervalMinutes) { mutableStateOf(policy.alertIntervalMinutes.toString()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Instant Remote Lock & Bonus Time Grant
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
                                text = "🔒 원격 즉시 잠금 / 보너스 시간 부여",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = if (policy.isImmediateLocked) {
                                    "현재 자녀 스마트폰이 즉시 잠금 상태입니다 (전화/문자만 가능)"
                                } else {
                                    "버튼 하나로 지금 바로 잠그거나 보너스 시간을 부여합니다"
                                },
                                fontSize = 12.sp,
                                color = if (policy.isImmediateLocked) Color(0xFFDC2626) else Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = policy.isImmediateLocked,
                            onCheckedChange = onToggleInstantLock
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onGrantBonusTime(15) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                        ) {
                            Text("+15분 부여", fontSize = 12.sp)
                        }
                        Button(
                            onClick = { onGrantBonusTime(30) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Text("+30분 부여", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { onGrantBonusTime(-policy.bonusMinutesToday) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("보너스 초기화", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 2. Feature 4.3: Allowed Usage Time Window (Default 10:00 ~ 21:00) & Daily Quota
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⏰ 사용 가능 시간대 정의 (현재: ${policy.formattedAllowedWindow()})",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "지정된 시간대(예: 오전 10:00 ~ 오후 21:00) 외에는 자동으로 잠금 화면이 작동합니다.",
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
                            value = startHourText,
                            onValueChange = { startHourText = it.take(2) },
                            label = { Text("시작 시(0~23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = startMinText,
                            onValueChange = { startMinText = it.take(2) },
                            label = { Text("시작 분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endHourText,
                            onValueChange = { endHourText = it.take(2) },
                            label = { Text("종료 시(0~23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = endMinText,
                            onValueChange = { endMinText = it.take(2) },
                            label = { Text("종료 분") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                startHourText = "10"
                                startMinText = "0"
                                endHourText = "21"
                                endMinText = "0"
                                onUpdateTimeWindow(10, 0, 21, 0)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("기본값 (10:00 ~ 21:00)")
                        }
                        Button(
                            onClick = {
                                onUpdateTimeWindow(
                                    startHourText.toIntOrNull() ?: 10,
                                    startMinText.toIntOrNull() ?: 0,
                                    endHourText.toIntOrNull() ?: 21,
                                    endMinText.toIntOrNull() ?: 0
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("시간대 저장")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "일일 총 사용시간 제한: ${policy.dailyLimitMinutes?.let { "${it}분" } ?: "제한 없음"} (보너스 +${policy.bonusMinutesToday}분)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(60, 90, 120, 180, 240).forEach { limit ->
                            FilterChip(
                                selected = policy.dailyLimitMinutes == limit,
                                onClick = { onUpdateDailyLimit(limit) },
                                label = { Text("하루 ${limit}분") }
                            )
                        }
                    }
                }
            }
        }

        // 3. Feature 4.5: Periodic Usage Alert Every 30 Minutes (Configurable)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔔 사용 시간 주기별 부모 알림 설정 (현재: ${policy.alertIntervalMinutes}분마다)",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "설정한 분(기본 30분, 자유 변경 가능)마다 자녀의 누적 사용 시간·현재 위치·주요 사용 앱을 부모 폰으로 알립니다.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(10, 15, 30, 45, 60, 90).forEach { minutes ->
                            FilterChip(
                                selected = policy.alertIntervalMinutes == minutes,
                                onClick = {
                                    customAlertIntervalText = minutes.toString()
                                    onUpdateAlertInterval(minutes)
                                },
                                label = { Text("${minutes}분마다 알림") }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customAlertIntervalText,
                            onValueChange = { customAlertIntervalText = it.take(3) },
                            label = { Text("직접 입력 (분 단위, 5~180분)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                val parsed = customAlertIntervalText.toIntOrNull() ?: 30
                                onUpdateAlertInterval(parsed)
                            }
                        ) {
                            Text("주기 적용")
                        }
                    }
                }
            }
        }

        // 4. Feature 4.4: Allowed Apps Definition
        item {
            Text(
                text = "📲 사용 가능한 앱 정의 (상시 허용 / 시간 내 허용 / 차단)",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                color = Color(0xFF0F172A)
            )
        }

        items(policy.appRules.values.toList(), key = { it.packageName }) { rule ->
            val usedToday = health.appUsedMinutesToday[rule.packageName] ?: 0
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
                        Column {
                            Text(rule.appName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                text = "${rule.category} · 오늘 ${usedToday}분 사용" +
                                    (rule.dailyQuotaMinutes?.let { " / 일일 한도 ${it}분" } ?: ""),
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = rule.accessMode == AppAccessMode.ALWAYS_ALLOWED,
                            onClick = { onUpdateAppMode(rule.packageName, AppAccessMode.ALWAYS_ALLOWED, null) },
                            label = { Text("상시 허용", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = rule.accessMode == AppAccessMode.ALLOWED,
                            onClick = { onUpdateAppMode(rule.packageName, AppAccessMode.ALLOWED, rule.dailyQuotaMinutes ?: 45) },
                            label = { Text("시간 내 허용", fontSize = 12.sp) }
                        )
                        FilterChip(
                            selected = rule.accessMode == AppAccessMode.BLOCKED,
                            onClick = { onUpdateAppMode(rule.packageName, AppAccessMode.BLOCKED, 0) },
                            label = { Text("차단", fontSize = 12.sp) }
                        )
                    }
                }
            }
        }
    }
}
