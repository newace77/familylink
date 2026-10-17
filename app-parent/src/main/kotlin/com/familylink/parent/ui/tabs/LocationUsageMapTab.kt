package com.familylink.parent.ui.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.model.SafeZone
import com.familylink.model.StayPointSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LocationUsageMapTab(
    staySessions: List<StayPointSession>,
    safeZones: List<SafeZone>,
    selectedSession: StayPointSession?,
    onSelectSession: (StayPointSession) -> Unit
) {
    val activeSession = selectedSession ?: staySessions.firstOrNull()
    val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)

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
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "🗺️ 위치 기록 + 앱 사용 기록 동시 지도",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "체류 지점(반경 50m·3분 이상)별 사용한 앱과 웹페이지를 한눈에 확인합니다",
                                fontSize = 12.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive Stay-Point Map Canvas with route lines, safe zones, and callout badges
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(230.dp)
                            .background(Color(0xFFE0F2FE), RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0xFFBAE6FD), RoundedCornerShape(14.dp))
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            // Road grid background
                            for (i in 1..5) {
                                drawLine(
                                    color = Color.White.copy(alpha = 0.65f),
                                    start = Offset(0f, h * i / 6f),
                                    end = Offset(w, h * i / 6f),
                                    strokeWidth = 6f
                                )
                                drawLine(
                                    color = Color.White.copy(alpha = 0.65f),
                                    start = Offset(w * i / 6f, 0f),
                                    end = Offset(w * i / 6f, h),
                                    strokeWidth = 6f
                                )
                            }

                            val points = listOf(
                                Offset(w * 0.20f, h * 0.72f), // 우리집
                                Offset(w * 0.34f, h * 0.28f), // 역삼초
                                Offset(w * 0.76f, h * 0.62f), // 대치동 수학학원
                                Offset(w * 0.48f, h * 0.42f)  // 역삼초 앞 GS25 편의점 (현재)
                            )

                            // Draw movement route dashed line
                            for (i in 0 until points.size - 1) {
                                drawLine(
                                    color = Color(0xFF2563EB),
                                    start = points[i],
                                    end = points[i + 1],
                                    strokeWidth = 6f,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                                )
                            }

                            // Draw SafeZone circles and StayPoint markers
                            points.forEachIndexed { idx, pt ->
                                val isCurrent = idx == points.lastIndex
                                drawCircle(
                                    color = if (isCurrent) Color(0xFFEF4444).copy(alpha = 0.20f) else Color(0xFF2563EB).copy(alpha = 0.15f),
                                    radius = if (isCurrent) 46f else 34f,
                                    center = pt
                                )
                                drawCircle(
                                    color = if (isCurrent) Color(0xFFDC2626) else Color(0xFF1D4ED8),
                                    radius = if (isCurrent) 14f else 11f,
                                    center = pt
                                )
                            }
                        }

                        // Map Overlay Callout Badges
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MapMarkerBadge(
                                    text = "🏫 역삼초등학교 · 카톡 6분",
                                    isSelected = activeSession?.placeName?.contains("역삼초등학교") == true,
                                    isAlert = false,
                                    onClick = {
                                        staySessions.find { it.placeName.contains("역삼초등학교") }?.let(onSelectSession)
                                    }
                                )
                                MapMarkerBadge(
                                    text = "📍 역삼초 앞 GS25 편의점 · YouTube 30분",
                                    isSelected = activeSession?.placeName?.contains("편의점") == true,
                                    isAlert = true,
                                    onClick = {
                                        staySessions.find { it.placeName.contains("편의점") }?.let(onSelectSession)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                MapMarkerBadge(
                                    text = "🏠 우리집 · 카톡 5분",
                                    isSelected = activeSession?.placeName?.contains("우리집") == true,
                                    isAlert = false,
                                    onClick = {
                                        staySessions.find { it.placeName.contains("우리집") }?.let(onSelectSession)
                                    }
                                )
                                MapMarkerBadge(
                                    text = "📐 대치동 수학학원 · EBS 15분, 크롬 15분",
                                    isSelected = activeSession?.placeName?.contains("수학학원") == true,
                                    isAlert = false,
                                    onClick = {
                                        staySessions.find { it.placeName.contains("수학학원") }?.let(onSelectSession)
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Horizontal Stay-Point Selector Chips
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        staySessions.forEach { session ->
                            val selected = activeSession?.sessionId == session.sessionId
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectSession(session) },
                                label = {
                                    Text(
                                        text = session.headlineBadge(),
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }

        // Selected Stay-Point Detailed Card (Location + App Usage + Web Pages viewed there)
        if (activeSession != null) {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "📍 ${activeSession.placeName}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = activeSession.address,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            Surface(
                                color = Color(0xFFFEE2E2),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "${timeFormat.format(Date(activeSession.arrivedAtMillis))} ~ ${timeFormat.format(Date(activeSession.departedAtMillis))} (${activeSession.dwellMinutes}분 체류)",
                                    color = Color(0xFF991B1B),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "📱 이 장소에서 사용한 앱 (총 ${activeSession.totalScreenMinutes}분)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E293B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        activeSession.appUsages.forEach { usage ->
                            val ratio = (usage.usageMinutes.toFloat() / activeSession.dwellMinutes.coerceAtLeast(1).toFloat())
                                .coerceIn(0.08f, 1f)
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${usage.appName} (${usage.category})",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${usage.usageMinutes}분 사용",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF2563EB)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { ratio },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    color = if (usage.appName.contains("YouTube")) Color(0xFFDC2626) else Color(0xFF2563EB),
                                    trackColor = Color(0xFFE2E8F0)
                                )
                            }
                        }

                        if (activeSession.webLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "🌐 이 장소에서 본 크롬 웹페이지 & 유튜브 영상 (${activeSession.webLogs.size}건)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF1E293B)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            activeSession.webLogs.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color(0xFFF8FAFC), RoundedCornerShape(10.dp))
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "[${log.contentType.label}] ${log.title}",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = log.urlOrQuery,
                                            fontSize = 11.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${(log.durationSeconds / 60).coerceAtLeast(1)}분",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "🕒 오늘 전체 동선 및 장소별 사용 요약 (${staySessions.size}곳)",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF0F172A)
            )
        }

        items(staySessions, key = { it.sessionId }) { session ->
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (activeSession?.sessionId == session.sessionId) Color(0xFFEFF6FF) else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSession(session) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.headlineBadge(), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "${timeFormat.format(Date(session.arrivedAtMillis))} ~ ${timeFormat.format(Date(session.departedAtMillis))} (${session.dwellMinutes}분 머무름 · 웹/영상 ${session.webLogs.size}건)",
                            fontSize = 12.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                    Text(
                        text = "상세보기 ›",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2563EB)
                    )
                }
            }
        }
    }
}

@Composable
private fun MapMarkerBadge(
    text: String,
    isSelected: Boolean,
    isAlert: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected && isAlert -> Color(0xFFDC2626)
        isSelected -> Color(0xFF1D4ED8)
        isAlert -> Color(0xFFFEF2F2)
        else -> Color.White.copy(alpha = 0.94f)
    }
    val fgColor = when {
        isSelected -> Color.White
        isAlert -> Color(0xFFB91C1C)
        else -> Color(0xFF1E293B)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 4.dp,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = fgColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
