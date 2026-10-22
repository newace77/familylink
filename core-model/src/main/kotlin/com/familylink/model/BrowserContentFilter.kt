package com.familylink.model

import java.util.UUID

object BrowserContentFilter {

    val SUPPORTED_BROWSERS = mapOf(
        "com.android.chrome" to "Chrome",
        "com.sec.android.app.sbrowser" to "삼성 인터넷",
        "com.nhn.android.search" to "네이버 웨일/앱",
        "org.mozilla.firefox" to "Firefox",
        "com.microsoft.emmx" to "Edge"
    )

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    fun extractContent(
        packageName: String,
        urlCandidate: String?,
        titleCandidate: String?,
        isUrlBarFocused: Boolean,
        nowMillis: Long,
        lastLog: WebContentLog?,
        placeName: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): WebContentLog? {
        if (isUrlBarFocused) {
            return null
        }

        if (packageName == YOUTUBE_PACKAGE) {
            val cleanTitle = titleCandidate?.trim().orEmpty()
            if (cleanTitle.length < 2 || isGenericYoutubeUiText(cleanTitle)) {
                return null
            }
            val type = if (cleanTitle.contains("Shorts", ignoreCase = true) || cleanTitle.contains("쇼츠")) {
                ContentType.YOUTUBE_SHORTS
            } else {
                ContentType.YOUTUBE_VIDEO
            }
            if (lastLog != null &&
                lastLog.sourcePackage == packageName &&
                lastLog.title == cleanTitle
            ) {
                // 같은 영상이 계속 열려 있는 동안에는 (시간과 무관하게) 새 기록을 만들지 않는다.
                // 다른 콘텐츠를 거쳐 다시 열면 lastLog가 바뀌므로 그때 새로 기록된다.
                return null
            }
            return WebContentLog(
                id = UUID.randomUUID().toString(),
                timestampMillis = nowMillis,
                sourcePackage = packageName,
                browserOrAppName = "YouTube",
                contentType = type,
                title = cleanTitle,
                urlOrQuery = "youtube://watch?title=$cleanTitle",
                durationSeconds = 0,
                placeName = placeName,
                latitude = latitude,
                longitude = longitude
            )
        }

        val browserName = SUPPORTED_BROWSERS[packageName] ?: return null
        val rawUrl = urlCandidate?.trim().orEmpty()
        if (rawUrl.isBlank() || isBrowserPlaceholderText(rawUrl)) {
            return null
        }

        val isLikelyUrl = rawUrl.startsWith("http://") ||
            rawUrl.startsWith("https://") ||
            (rawUrl.contains(".") && !rawUrl.contains(" "))

        val normalizedUrl = when {
            rawUrl.startsWith("http://") || rawUrl.startsWith("https://") -> rawUrl
            isLikelyUrl -> "https://$rawUrl"
            else -> rawUrl
        }

        val contentType = if (isLikelyUrl) ContentType.WEB_PAGE else ContentType.SEARCH_QUERY
        val resolvedTitle = titleCandidate?.trim()?.takeIf { it.isNotBlank() } ?: normalizedUrl

        if (lastLog != null &&
            lastLog.sourcePackage == packageName &&
            lastLog.urlOrQuery == normalizedUrl
        ) {
            // 같은 페이지가 계속 열려 있는 동안에는 (시간과 무관하게) 새 기록을 만들지 않는다.
            // 창 콘텐츠 변경 이벤트가 수 초마다 반복돼도 동일 URL은 한 번만 기록된다.
            return null
        }

        return WebContentLog(
            id = UUID.randomUUID().toString(),
            timestampMillis = nowMillis,
            sourcePackage = packageName,
            browserOrAppName = browserName,
            contentType = contentType,
            title = resolvedTitle,
            urlOrQuery = normalizedUrl,
            durationSeconds = 0,
            placeName = placeName,
            latitude = latitude,
            longitude = longitude
        )
    }

    private fun isBrowserPlaceholderText(text: String): Boolean {
        val lower = text.lowercase()
        return lower == "검색 또는 웹 주소 입력" ||
            lower == "search or type web address" ||
            lower == "url 입력" ||
            lower == "about:blank" ||
            lower == "chrome://newtab"
    }

    private fun isGenericYoutubeUiText(text: String): Boolean {
        return text in setOf("YouTube", "홈", "Shorts", "구독", "보관함", "검색", "알림")
    }
}
