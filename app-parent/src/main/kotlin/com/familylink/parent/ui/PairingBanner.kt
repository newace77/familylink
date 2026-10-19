package com.familylink.parent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.familylink.data.PairingState

/**
 * 부모 앱 페어링 배너: LINKED가 아니면 TopAppBar 바로 아래에 뜬다.
 * NOT_PAIRED → 코드 생성 버튼, WAITING → 자녀에게 알려줄 6자리 코드 표시.
 */
@Composable
fun PairingBanner(
    state: PairingState,
    onCreateCode: () -> Unit,
    lastCreatedCode: String?
) {
    if (state == PairingState.LINKED) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF7C2D12))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (state) {
            PairingState.NOT_PAIRED -> {
                Column {
                    Text(
                        text = "가족 코드가 없습니다 — 자녀 앱과 아직 연동되지 않았어요",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "버튼을 누르면 6자리 코드를 만들 수 있어요",
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp
                    )
                }
                FilledTonalButton(onClick = onCreateCode) {
                    Text("코드 생성", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            PairingState.WAITING -> {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "자녀 앱에 아래 코드를 입력해 주세요",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = lastCreatedCode?.takeIf { it.isNotBlank() } ?: "생성 실패 — 다시 눌러 주세요",
                        color = if (lastCreatedCode.isNullOrBlank()) Color(0xFFFCA5A5) else Color(0xFFFDE68A),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp
                    )
                }
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color(0xFFFDE68A),
                    strokeWidth = 2.dp
                )
            }
            PairingState.LINKED -> Unit
        }
    }
}
