package com.familylink.child.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 자녀 앱 페어링 화면: LINKED가 될 때까지 full-screen으로 표시한다.
 * 6자리 숫자 코드 입력 → onConfirm의 결과(false)가 나오면 안내를 보여준다.
 */
@Composable
fun FamilyPairingScreen(onConfirm: (String) -> Boolean) {
    var code by remember { mutableStateOf("") }
    var lastAttemptFailed by remember { mutableStateOf(false) }

    val validInput = code.length == 6 && code.all { it.isDigit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically)
    ) {
        Text("👨‍👩‍👧", fontSize = 48.sp)
        Text(
            "FamilyLink Plus와 연결하기",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            "부모 앱에서 만든 6자리 가족 코드를\n입력해 주세요",
            fontSize = 14.sp,
            color = Color(0xFF94A3B8),
            lineHeight = 22.sp
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        code = input.filter { it.isDigit() }.take(6)
                        lastAttemptFailed = false
                    },
                    modifier = Modifier.width(220.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                        color = Color(0xFF0F172A)
                    ),
                    placeholder = {
                        Text("000000", fontSize = 28.sp, fontFamily = FontFamily.Monospace, color = Color(0xFFCBD5E1))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (lastAttemptFailed) {
                    Text(
                        "코드를 다시 확인해 주세요",
                        fontSize = 12.sp,
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = { lastAttemptFailed = !onConfirm(code) },
                    enabled = validInput,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("연결하기", fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            "연결되면 안심존·웹기록·사용시간이 부모 앱과 같아집니다",
            fontSize = 11.sp,
            color = Color(0xFF64748B)
        )
    }
}
