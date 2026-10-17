package com.familylink.data

import com.familylink.model.StayPointClusterer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ResolvedPlace(
    val placeName: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

object PlaceNameResolver {
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .build()
    }

    private data class KnownLandmark(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double
    )

    private val KNOWN_KOREAN_LANDMARKS = listOf(
        KnownLandmark("역삼초 앞 GS25 편의점", "서울 강남구 역삼로 123", 37.4994, 127.0334),
        KnownLandmark("역삼초등학교", "서울 강남구 역삼로 120", 37.5000, 127.0330),
        KnownLandmark("대치동 최상위 수학학원", "서울 강남구 도곡로 415", 37.4981, 127.0562),
        KnownLandmark("역삼래미안 아파트 (우리집)", "서울 강남구 언주로 310", 37.4968, 127.0389),
        KnownLandmark("한티역 교보문고 스터디카페", "서울 강남구 선릉로 320", 37.4962, 127.0521)
    )

    fun resolveKoreanPlaceName(
        latitude: Double,
        longitude: Double,
        kakaoRestApiKey: String? = null
    ): ResolvedPlace {
        if (!kakaoRestApiKey.isNullOrBlank()) {
            runCatching {
                val url = "https://dapi.kakao.com/v2/local/search/category.json?category_group_code=CS2&x=$longitude&y=$latitude&radius=60&sort=distance"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "KakaoAK $kakaoRestApiKey")
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            return parseKakaoDocumentsJson(body, latitude, longitude)
                        }
                    }
                }
            }
        }

        val nearest = KNOWN_KOREAN_LANDMARKS.minByOrNull {
            StayPointClusterer.distanceMeters(latitude, longitude, it.lat, it.lng)
        }
        if (nearest != null && StayPointClusterer.distanceMeters(latitude, longitude, nearest.lat, nearest.lng) <= 250.0) {
            return ResolvedPlace(
                placeName = nearest.name,
                address = nearest.address,
                latitude = latitude,
                longitude = longitude
            )
        }

        return ResolvedPlace(
            placeName = String.format("체류 지점 (%.4f, %.4f)", latitude, longitude),
            address = "위치 좌표 기반 기록",
            latitude = latitude,
            longitude = longitude
        )
    }

    fun parseKakaoDocumentsJson(
        json: String,
        fallbackLat: Double,
        fallbackLng: Double
    ): ResolvedPlace {
        val root = JSONObject(json)
        val docs = root.optJSONArray("documents")
        if (docs == null || docs.length() == 0) {
            return resolveKoreanPlaceName(fallbackLat, fallbackLng, kakaoRestApiKey = null)
        }

        var primaryName = ""
        var primaryAddress = ""
        var schoolLandmarkName: String? = null

        for (i in 0 until docs.length()) {
            val doc = docs.getJSONObject(i)
            val placeName = doc.optString("place_name").trim()
            val category = doc.optString("category_group_name").trim()
            val roadAddr = doc.optString("road_address_name").trim()
            val jibunAddr = doc.optString("address_name").trim()

            if (i == 0) {
                primaryName = placeName
                primaryAddress = roadAddr.ifBlank { jibunAddr }
            }
            if (category.contains("학교") || placeName.endsWith("초등학교") || placeName.endsWith("중학교") || placeName.endsWith("고등학교")) {
                schoolLandmarkName = placeName
            }
        }

        val combinedName = if (!schoolLandmarkName.isNullOrBlank() && schoolLandmarkName != primaryName) {
            "$schoolLandmarkName 앞 $primaryName"
        } else {
            primaryName.ifBlank { "확인된 장소" }
        }

        return ResolvedPlace(
            placeName = combinedName,
            address = primaryAddress,
            latitude = fallbackLat,
            longitude = fallbackLng
        )
    }
}
