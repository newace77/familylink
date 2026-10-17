package com.familylink.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object StayPointClusterer {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun buildSessions(
        childId: String,
        dateKey: String,
        locations: List<LocationPoint>,
        appSlices: List<AppUsageSlice>,
        webLogs: List<WebContentLog>,
        radiusMeters: Double = 50.0,
        minStayMillis: Long = 180_000L
    ): List<StayPointSession> {
        if (locations.isEmpty()) return emptyList()
        val sorted = locations.sortedBy { it.timestampMillis }
        val sessions = mutableListOf<StayPointSession>()

        var clusterStartIdx = 0
        var idx = 1

        while (idx <= sorted.size) {
            val anchor = sorted[clusterStartIdx]
            val withinRadius = if (idx < sorted.size) {
                distanceMeters(
                    anchor.latitude,
                    anchor.longitude,
                    sorted[idx].latitude,
                    sorted[idx].longitude
                ) <= radiusMeters
            } else {
                false
            }

            if (withinRadius) {
                idx++
            } else {
                val clusterPoints = sorted.subList(clusterStartIdx, idx)
                val arrivedAt = clusterPoints.first().timestampMillis
                val departedAt = clusterPoints.last().timestampMillis
                val dwellMillis = departedAt - arrivedAt

                if (dwellMillis >= minStayMillis) {
                    val avgLat = clusterPoints.map { it.latitude }.average()
                    val avgLng = clusterPoints.map { it.longitude }.average()
                    val bestPoint = clusterPoints.firstOrNull { it.placeName.isNotBlank() && it.placeName != "알 수 없는 위치" }
                        ?: clusterPoints.first()

                    val correlatedApps = correlateAppUsages(arrivedAt, departedAt, appSlices)
                    val correlatedWebLogs = webLogs.filter { log ->
                        log.timestampMillis in (arrivedAt - 60_000L)..(departedAt + 60_000L)
                    }.map { log ->
                        if (log.placeName.isNullOrBlank()) log.copy(placeName = bestPoint.placeName) else log
                    }

                    sessions.add(
                        StayPointSession(
                            sessionId = "stay-${childId}-${arrivedAt}",
                            childId = childId,
                            dateKey = dateKey,
                            latitude = avgLat,
                            longitude = avgLng,
                            placeName = bestPoint.placeName,
                            address = bestPoint.address,
                            arrivedAtMillis = arrivedAt,
                            departedAtMillis = departedAt,
                            appUsages = correlatedApps,
                            webLogs = correlatedWebLogs
                        )
                    )
                }
                clusterStartIdx = idx
                idx = clusterStartIdx + 1
            }
        }

        return sessions
    }

    private fun correlateAppUsages(
        sessionStartMillis: Long,
        sessionEndMillis: Long,
        appSlices: List<AppUsageSlice>
    ): List<AppUsageSegment> {
        // Allow up to 2 minutes tolerance around session boundaries for foreground slices
        val windowStart = sessionStartMillis
        val windowEnd = sessionEndMillis + 120_000L

        val groupedSeconds = mutableMapOf<String, Pair<AppUsageSlice, Long>>()
        for (slice in appSlices) {
            val overlapStart = maxOf(windowStart, slice.startMillis)
            val overlapEnd = minOf(windowEnd, slice.endMillis)
            if (overlapEnd > overlapStart) {
                val seconds = (overlapEnd - overlapStart) / 1000L
                val existing = groupedSeconds[slice.packageName]
                groupedSeconds[slice.packageName] = slice to ((existing?.second ?: 0L) + seconds)
            }
        }

        return groupedSeconds.values.mapNotNull { (sampleSlice, totalSec) ->
            val minutes = (totalSec / 60.0).roundToInt()
            if (minutes <= 0 && totalSec < 30L) {
                null
            } else {
                AppUsageSegment(
                    packageName = sampleSlice.packageName,
                    appName = sampleSlice.appName,
                    usageMinutes = minutes.coerceAtLeast(1),
                    usageSeconds = totalSec.toInt(),
                    category = sampleSlice.category
                )
            }
        }.sortedByDescending { it.usageMinutes }
    }

    fun distanceMeters(
        lat1: Double,
        lng1: Double,
        lat2: Double,
        lng2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2.0) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
