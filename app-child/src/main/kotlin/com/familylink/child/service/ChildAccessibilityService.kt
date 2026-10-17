package com.familylink.child.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.familylink.child.ChildMonitorController
import com.familylink.child.ui.ChildMainActivity
import com.familylink.data.FamilyRepositoryProvider

class ChildAccessibilityService : AccessibilityService() {

    private val controller by lazy {
        ChildMonitorController(FamilyRepositoryProvider.getInstance(applicationContext))
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FamilyRepositoryProvider.getInstance(applicationContext).updateDeviceHealth {
            it.copy(accessibilityEnabled = true, lastUpdatedMillis = System.currentTimeMillis())
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val pkgName = event?.packageName?.toString() ?: rootNode.packageName?.toString() ?: return
        if (pkgName == packageName) return

        val className = event?.className?.toString()
        val extracted = inspectNodeTree(rootNode, pkgName)

        val decision = controller.onForegroundWindowEvaluated(
            packageName = pkgName,
            className = className,
            visibleTexts = extracted.visibleTexts,
            urlCandidate = extracted.urlCandidate,
            titleCandidate = extracted.titleCandidate,
            isUrlBarFocused = extracted.isUrlBarFocused
        )

        if (decision.isBlocked) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            val lockIntent = Intent(this, ChildMainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(ChildMainActivity.EXTRA_FORCE_LOCK_SCREEN, true)
                putExtra(ChildMainActivity.EXTRA_LOCK_REASON_TITLE, decision.reason.displayTitle)
                putExtra(ChildMainActivity.EXTRA_LOCK_REASON_MESSAGE, decision.message)
            }
            startActivity(lockIntent)
        }
    }

    override fun onInterrupt() {
        FamilyRepositoryProvider.getInstance(applicationContext).updateDeviceHealth {
            it.copy(accessibilityEnabled = false, lastUpdatedMillis = System.currentTimeMillis())
        }
    }

    private data class NodeInspectionResult(
        val urlCandidate: String?,
        val titleCandidate: String?,
        val isUrlBarFocused: Boolean,
        val visibleTexts: List<String>
    )

    private fun inspectNodeTree(root: AccessibilityNodeInfo, packageName: String): NodeInspectionResult {
        var urlCandidate: String? = null
        var titleCandidate: String? = null
        var isUrlBarFocused = false
        val visibleTexts = mutableListOf<String>()

        val urlBarViewIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/search_box_text",
            "com.sec.android.app.sbrowser:id/location_bar_edit_text",
            "com.nhn.android.search:id/url_bar",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "com.microsoft.emmx:id/url_bar"
        )

        for (viewId in urlBarViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            val first = nodes?.firstOrNull()
            if (first != null) {
                urlCandidate = first.text?.toString()
                isUrlBarFocused = first.isFocused
                break
            }
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visitedCount = 0

        while (queue.isNotEmpty() && visitedCount < 120) {
            val node = queue.removeFirst()
            visitedCount++

            val text = node.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                if (visibleTexts.size < 30) {
                    visibleTexts.add(text)
                }
                if (titleCandidate == null && text.length in 4..100 && text != urlCandidate) {
                    val resName = node.viewIdResourceName.orEmpty()
                    if (packageName == "com.google.android.youtube" ||
                        resName.contains("title", ignoreCase = true) ||
                        node.className?.toString()?.contains("TextView") == true
                    ) {
                        titleCandidate = text
                    }
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return NodeInspectionResult(
            urlCandidate = urlCandidate,
            titleCandidate = titleCandidate,
            isUrlBarFocused = isUrlBarFocused,
            visibleTexts = visibleTexts
        )
    }
}
