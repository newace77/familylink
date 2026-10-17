package com.familylink.parent.ui.tabs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import com.familylink.model.ContentType
import com.familylink.model.WebContentLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WebHistoryTimelineTab(
    webLogs: List<WebContentLog>
) {
    var selectedType by remember { mutableStateOf<ContentType?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.KOREA) }

    val filteredLogs = webLogs.filter { log ->
        val matchesType = selectedType == null || log.contentType == selectedType
        val q = searchQuery.trim()
        val matchesQuery = q.isBlank() ||
            log.title.contains(q, ignoreCase = true) ||
            log.urlOrQuery.contains(q, ignoreCase = true) ||
            (log.placeName?.contains(q, ignoreCase = true) == true)
        matchesType && matchesQuery
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "🌐 브라우저 조회 웹페이지 & 유튜브 시청 내역",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "크롬·삼성인터넷·웨일에서 본 URL/검색어와 유튜브 시청 영상 제목 및 조회 장소를 기록합니다.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("웹페이지 제목, URL, 검색어 또는 장소명(예: 편의점) 검색") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedType == null,
                            onClick = { selectedType = null },
                            label = { Text("전체 (${webLogs.size})") }
                        )
                        ContentType.entries.forEach { type ->
                            val count = webLogs.count { it.contentType == type }
                            FilterChip(
                                selected = selectedType == type,
                                onClick = { selectedType = if (selectedType == type) null else type },
                                label = { Text("${type.label} ($count)") }
                            )
                        }
                    }
                }
            }
        }

        items(filteredLogs, key = { it.id }) { log ->
            val (badgeBg, badgeFg) = when (log.contentType) {
                ContentType.WEB_PAGE -> Color(0xFFDBEAFE) to Color(0xFF1D4ED8)
                ContentType.SEARCH_QUERY -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                ContentType.YOUTUBE_VIDEO -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                ContentType.YOUTUBE_SHORTS -> Color(0xFFFCE7F3) to Color(0xFFBE185D)
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(badgeBg, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "${log.browserOrAppName} · ${log.contentType.label}",
                                    color = badgeFg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!log.placeName.isNullOrBlank()) {
                                Text(
                                    text = "📍 ${log.placeName}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F766E)
                                )
                            }
                        }
                        Text(
                            text = timeFormatter.format(Date(log.timestampMillis)),
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = log.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = log.urlOrQuery,
                        fontSize = 12.sp,
                        color = Color(0xFF2563EB)
                    )
                    if (log.durationSeconds > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "체류/시청 시간: 약 ${(log.durationSeconds / 60).coerceAtLeast(1)}분 (${log.durationSeconds}초)",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}
