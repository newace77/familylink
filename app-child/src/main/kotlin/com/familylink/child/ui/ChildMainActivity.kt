package com.familylink.child.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.familylink.child.ChildMonitorController
import com.familylink.child.service.ChildDeviceAdminReceiver
import com.familylink.child.service.ChildMonitoringForegroundService
import com.familylink.data.FamilyRepositoryProvider
import com.familylink.data.PairingState
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class ChildMainActivity : ComponentActivity() {

    private val repository by lazy { FamilyRepositoryProvider.getInstance(applicationContext) }
    private val controller by lazy { ChildMonitorController(repository) }

    private var forceLockScreen by mutableStateOf(false)
    private var lockReasonTitle by mutableStateOf("사용 제한")
    private var lockReasonMessage by mutableStateOf("부모님이 설정한 정책에 의해 현재 화면이 잠겼습니다.")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        startGuardServiceSafely()

        setContent {
            val policy by repository.policy.collectAsState()
            val health by repository.deviceHealth.collectAsState()
            val pairingState by repository.pairingState.collectAsState()
            val lockState by controller.activeLockState.collectAsState()

            val showLock = forceLockScreen || lockState.isBlocked || policy.isImmediateLocked

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF2563EB),
                    secondary = Color(0xFF0D9488),
                    background = Color(0xFFF8FAFC),
                    surface = Color.White
                )
            ) {
                when {
                    // 페어링 전: 6자리 가족 코드 입력 화면이 최상단 (LINKED까지 유지)
                    pairingState != PairingState.LINKED -> FamilyPairingScreen(
                        onConfirm = { code ->
                            val formatValid = code.length == 6 && code.all { it.isDigit() }
                            if (formatValid) {
                                // 실존 확인은 백그라운드 코루틴: gms Task 콜백이 Main Looper에
                                // 게시되므로 runBlocking으로 기다리면 교착 위험이 있다 (Task 6 판정).
                                // 잘못된 코드면 상태가 NOT_PAIRED/WAITING으로 유지되어 화면이 그대로 뜬다.
                                lifecycleScope.launch { repository.linkFamilyCode(code) }
                            }
                            formatValid
                        }
                    )
                    showLock -> LockOverlayScreen(
                        childName = policy.childName,
                        reasonTitle = if (policy.isImmediateLocked) "부모님 즉시 잠금" else if (lockState.isBlocked) lockState.reason.displayTitle else lockReasonTitle,
                        reasonMessage = if (policy.isImmediateLocked) "부모님이 원격으로 스마트폰을 잠갔습니다." else if (lockState.isBlocked) lockState.message else lockReasonMessage,
                        allowedWindowText = policy.formattedAllowedWindow(),
                        onVerifyParentPin = { pin ->
                            val unlocked = controller.verifyParentPinAndUnlock(pin)
                            if (unlocked) {
                                forceLockScreen = false
                            }
                            unlocked
                        },
                        onRequestBonusTime = { message ->
                            controller.requestBonusTimeFromParent(message)
                        },
                        onEmergencyDial = {
                            startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")))
                        }
                    )
                    else -> ChildDashboardScreen(
                        policy = policy,
                        health = health,
                        onOpenAccessibilitySettings = {
                            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                        },
                        onOpenUsageStatsSettings = {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        onOpenOverlaySettings = {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        },
                        onEnableDeviceAdmin = {
                            val adminComponent = ComponentName(this, ChildDeviceAdminReceiver::class.java)
                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                putExtra(
                                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                                    "자녀 보호 앱이 임의로 삭제되지 않도록 기기 관리자를 활성화해 주세요."
                                )
                            }
                            startActivity(intent)
                        },
                        onRequestBonusTime = { reason ->
                            controller.requestBonusTimeFromParent(reason)
                        },
                        onSimulateLockPreview = {
                            forceLockScreen = true
                            lockReasonTitle = "사용 가능 시간 외 (미리보기)"
                            lockReasonMessage = "설정된 사용 가능 시간대(${policy.formattedAllowedWindow()}) 외에는 스마트폰이 잠깁니다."
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_FORCE_LOCK_SCREEN, false)) {
            forceLockScreen = true
            lockReasonTitle = intent.getStringExtra(EXTRA_LOCK_REASON_TITLE) ?: "사용 제한"
            lockReasonMessage = intent.getStringExtra(EXTRA_LOCK_REASON_MESSAGE) ?: "부모님이 설정한 정책에 의해 제한되었습니다."
        }
    }

    private fun startGuardServiceSafely() {
        runCatching {
            val serviceIntent = Intent(this, ChildMonitoringForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        }
    }

    companion object {
        const val EXTRA_FORCE_LOCK_SCREEN = "extra_force_lock_screen"
        const val EXTRA_LOCK_REASON_TITLE = "extra_lock_reason_title"
        const val EXTRA_LOCK_REASON_MESSAGE = "extra_lock_reason_message"
    }
}
