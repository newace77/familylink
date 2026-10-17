package com.familylink.model

object SafeZoneEvaluator {
    fun evaluateTransitions(
        childId: String,
        previousLat: Double?,
        previousLng: Double?,
        currentLat: Double,
        currentLng: Double,
        zones: List<SafeZone>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ParentAlert> {
        if (previousLat == null || previousLng == null) return emptyList()
        val alerts = mutableListOf<ParentAlert>()

        for (zone in zones) {
            val wasInside = StayPointClusterer.distanceMeters(
                previousLat,
                previousLng,
                zone.latitude,
                zone.longitude
            ) <= zone.radiusMeters

            val isInside = StayPointClusterer.distanceMeters(
                currentLat,
                currentLng,
                zone.latitude,
                zone.longitude
            ) <= zone.radiusMeters

            if (!wasInside && isInside && zone.notifyOnEnter) {
                alerts.add(
                    ParentAlert(
                        alertId = "zone-enter-${zone.zoneId}-${nowMillis}",
                        childId = childId,
                        type = AlertType.SAFE_ZONE_ENTER,
                        title = "📍 안심존 진입: ${zone.name}",
                        message = "자녀가 '${zone.name}' 안심존(반경 ${zone.radiusMeters.toInt()}m)에 도착했습니다.",
                        timestampMillis = nowMillis,
                        placeName = zone.name
                    )
                )
            } else if (wasInside && !isInside && zone.notifyOnExit) {
                alerts.add(
                    ParentAlert(
                        alertId = "zone-exit-${zone.zoneId}-${nowMillis}",
                        childId = childId,
                        type = AlertType.SAFE_ZONE_EXIT,
                        title = "🚶 안심존 이탈: ${zone.name}",
                        message = "자녀가 '${zone.name}' 안심존에서 이동했습니다.",
                        timestampMillis = nowMillis,
                        placeName = zone.name
                    )
                )
            }
        }

        return alerts
    }
}
