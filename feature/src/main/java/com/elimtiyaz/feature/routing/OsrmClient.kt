package com.elimtiyaz.feature.routing

import co.touchlab.kermit.Logger
import com.elimtiyaz.domain.model.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal client for the public OSRM router (`router.project-osrm.org`).
 */
@Singleton
class OsrmClient @Inject constructor() {

    private val baseUrl: String = DEFAULT_OSRM_BASE_URL

    private val log = Logger.withTag("OsrmClient")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Fetch a driving route through every point in [points] (in order). Returns
     * null on any failure — callers must treat null as "use the fallback".
     */
    suspend fun route(points: List<GeoPoint>): OsrmRoute? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        val coords = points.joinToString(";") { "${it.lng},${it.lat}" }
        val urlString = "$baseUrl/route/v1/driving/$coords?overview=full&geometries=polyline6"
        val response = runCatching {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            if (conn.responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        }.getOrElse {
            log.w { "OSRM request failed: ${it.message}" }
            return@withContext null
        } ?: return@withContext null

        val parsed = runCatching { json.decodeFromString<OsrmResponse>(response) }.getOrElse {
            log.w { "OSRM response parse failed: ${it.message}" }
            return@withContext null
        }
        val first = parsed.routes.firstOrNull() ?: return@withContext null
        val geometry = runCatching { decodePolyline6(first.geometry) }.getOrElse {
            log.w { "OSRM polyline decode failed: ${it.message}" }
            return@withContext null
        }
        OsrmRoute(
            geometry = geometry,
            distanceMeters = first.distance,
            durationSeconds = first.duration,
        )
    }

    companion object {
        /** Default public OSRM instance. */
        const val DEFAULT_OSRM_BASE_URL = "https://router.project-osrm.org"

        /**
         * Decode a polyline6-encoded string (1e-6 precision, signed delta
         * varints). Ported from the official OSRM polyline utility — kept
         * package-private so callers use [route].
         */
        @Suppress("NestedBlockDepth")
        fun decodePolyline6(encoded: String): List<GeoPoint> {
            val result = ArrayList<GeoPoint>(encoded.length / 4)
            var index = 0
            var lat = 0
            var lng = 0
            while (index < encoded.length) {
                var b: Int
                var shift = 0
                var resultLat = 0
                do {
                    b = encoded[index].code - 63
                    resultLat = resultLat or ((b and 0x1f) shl shift)
                    shift += 5
                    index++
                } while (b >= 0x20 && index < encoded.length)
                val dLat = if ((resultLat and 1) != 0) (resultLat shr 1).inv() else (resultLat shr 1)
                lat += dLat

                shift = 0
                var resultLng = 0
                do {
                    b = encoded[index].code - 63
                    resultLng = resultLng or ((b and 0x1f) shl shift)
                    shift += 5
                    index++
                } while (b >= 0x20 && index < encoded.length)
                val dLng = if ((resultLng and 1) != 0) (resultLng shr 1).inv() else (resultLng shr 1)
                lng += dLng

                result.add(GeoPoint(lat = lat / 1e6, lng = lng / 1e6))
            }
            return result
        }
    }
}

/** Decoded OSRM route. */
data class OsrmRoute(
    val geometry: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
) {
    /** Distance in kilometres (1 dp precision is enough for ETA display). */
    val distanceKm: Double get() = distanceMeters / 1000.0

    /** Duration in minutes (driving time, excludes stops). */
    val durationMin: Double get() = durationSeconds / 60.0
}

/** OSRM `/route/v1/driving` response payload — only the fields we use. */
@Serializable
private data class OsrmResponse(
    val code: String? = null,
    val routes: List<OsrmRouteDto> = emptyList(),
)

@Serializable
private data class OsrmRouteDto(
    val geometry: String = "",
    val distance: Double = 0.0,
    val duration: Double = 0.0,
)
