package com.example.infrastructure.routing

import com.example.domain.model.GeoPoint
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pure-Kotlin OSRM client — restores the pre-redesign `OsrmClient` (commit a34333a).
 *
 * Uses the public OSRM demo server (`https://router.project-osrm.org/route/v1/driving/`).
 * In production, deploy a self-hosted OSRM instance for reliability + privacy.
 *
 * Returns null on ANY failure — the caller is expected to fall back to
 * straight-line haversine via [TspSolver.polylineDistanceKm].
 *
 * Wire format: `polyline6` encoding (1e-6 precision).
 */
class OsrmClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_OSRM_BASE_URL,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /**
     * Request a driving route through the given waypoints.
     *
     * @param points Ordered list of [GeoPoint]s (start, intermediate, end).
     * @return The decoded polyline + total distance (meters) + total duration (seconds), or null on failure.
     */
    suspend fun route(points: List<GeoPoint>): OsrmRoute? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        try {
            val coords = points.joinToString(";") { "${it.lng},${it.lat}" }
            val url = "$baseUrl$routePath/$coords?overview=full&geometries=polyline6"
            val response: HttpResponse = httpClient.get(url)
            if (response.status.value != 200) return@withContext null
            val raw: String = response.body()
            val dto = json.decodeFromString(OsrmResponseDto.serializer(), raw)
            if (dto.code != "Ok" || dto.routes.isNullOrEmpty()) return@withContext null
            val first = dto.routes.first()
            val geometry = decodePolyline6(first.geometry ?: "")
            OsrmRoute(
                geometry = geometry,
                distanceMeters = first.distance ?: 0.0,
                durationSeconds = first.duration ?: 0.0,
            )
        } catch (_: Throwable) {
            null
        }
    }

    @Serializable
    private data class OsrmResponseDto(
        val code: String? = null,
        val routes: List<OsrmRouteDto>? = null,
    )

    @Serializable
    private data class OsrmRouteDto(
        val geometry: String? = null,
        val distance: Double? = null,
        val duration: Double? = null,
    )

    companion object {
        const val DEFAULT_OSRM_BASE_URL = "https://router.project-osrm.org"
        private const val routePath = "/route/v1/driving"

        /**
         * Decode a polyline6-encoded geometry (precision 1e-6) into a list of [GeoPoint]s.
         *
         * Standard algorithm — multi-byte varint encoding with the 6th bit as continuation flag.
         */
        fun decodePolyline6(encoded: String): List<GeoPoint> {
            val result = mutableListOf<GeoPoint>()
            var index = 0
            var lat = 0
            var lng = 0
            while (index < encoded.length) {
                val dLat = decodeNext(encoded, index)
                index = dLat.nextIndex
                lat += dLat.value
                val dLng = decodeNext(encoded, index)
                index = dLng.nextIndex
                lng += dLng.value
                result.add(GeoPoint(lat / 1e6, lng / 1e6))
            }
            return result
        }

        private data class DecodedValue(val value: Int, val nextIndex: Int)

        private fun decodeNext(encoded: String, startIndex: Int): DecodedValue {
            var shift = 0
            var result = 0
            var byte: Int
            var index = startIndex
            do {
                if (index >= encoded.length) return DecodedValue(0, index)
                byte = encoded[index].code - 63
                result = result or ((byte and 0x1f) shl shift)
                shift += 5
                index++
            } while (byte >= 0x20)
            val value = if ((result and 1) != 0) (result shr 1).inv() else result shr 1
            return DecodedValue(value, index)
        }
    }
}

/**
 * Decoded OSRM route.
 */
data class OsrmRoute(
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
)
