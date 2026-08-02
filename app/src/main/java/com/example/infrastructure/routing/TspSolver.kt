package com.example.infrastructure.routing

import com.example.domain.model.GeoPoint
import com.example.domain.model.RoutingStop
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin TSP solver — restores the pre-redesign `TspSolver` (commit a34333a).
 *
 * Two-stage pipeline:
 *  1. Greedy nearest-neighbor construction (O(n²)) from the Oran anchor.
 *  2. 2-opt local-search refinement (max 50 iterations).
 *
 * Open-path (no return-to-anchor) — the driver ends at the last stop.
 *
 * Used as a fallback when the `optimize-route` Edge Function is unavailable
 * (offline mode, dev environment, etc.).
 */
object TspSolver {

    /** Oran city center — the anchor point for the nearest-neighbor search. */
    val OranAnchor = GeoPoint(35.6911, -0.6417)

    const val TWO_OPT_MAX_ITERATIONS = 50

    /**
     * Greedy nearest-neighbor construction.
     *
     * @param stops Unordered stops. The order will be ignored.
     * @param start Anchor point (defaults to Oran).
     * @return Stops re-ordered by NN heuristic, with `orderInRoute` set to 1..n.
     */
    fun solveNearestNeighbor(
        stops: List<RoutingStop>,
        start: GeoPoint = OranAnchor,
    ): List<RoutingStop> {
        if (stops.isEmpty()) return emptyList()
        val remaining = stops.toMutableList()
        val result = mutableListOf<RoutingStop>()
        var current = start
        while (remaining.isNotEmpty()) {
            val nearestIdx = remaining.indices.minByOrNull { i ->
                haversineKm(current, GeoPoint(remaining[i].lat, remaining[i].lng))
            } ?: 0
            val next = remaining.removeAt(nearestIdx)
            current = GeoPoint(next.lat, next.lng)
            result.add(next)
        }
        return result.mapIndexed { idx, s -> s.copy(orderInRoute = idx + 1) }
    }

    /**
     * 2-opt refinement.
     *
     * Reverses sub-segments of the route whenever doing so reduces the total
     * haversine distance. Stops after [TWO_OPT_MAX_ITERATIONS] iterations
     * without improvement.
     */
    fun twoOptImprove(route: List<RoutingStop>): List<RoutingStop> {
        if (route.size < 4) return route
        var best = route
        var improved = true
        var iterations = 0
        while (improved && iterations < TWO_OPT_MAX_ITERATIONS) {
            improved = false
            iterations++
            for (i in 0 until best.size - 1) {
                for (j in i + 1 until best.size) {
                    if (j - i < 2) continue
                    val candidate = best.toMutableList()
                    val sub = candidate.subList(i, j + 1).reversed()
                    for (k in sub.indices) candidate[i + k] = sub[k]
                    if (totalDistance(candidate) < totalDistance(best) - 0.0001) {
                        best = candidate
                        improved = true
                    }
                }
            }
        }
        // Re-number orderInRoute
        return best.mapIndexed { idx, s -> s.copy(orderInRoute = idx + 1) }
    }

    /** Total haversine distance across an ordered list of stops (km). */
    fun totalDistance(stops: List<RoutingStop>): Double {
        if (stops.isEmpty()) return 0.0
        var total = 0.0
        var prev = OranAnchor
        for (s in stops) {
            val cur = GeoPoint(s.lat, s.lng)
            total += haversineKm(prev, cur)
            prev = cur
        }
        return total
    }

    /** Sum of haversine distances between consecutive points (km). */
    fun polylineDistanceKm(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineKm(points[i - 1], points[i])
        }
        return total
    }

    /**
     * Haversine distance between two points (km).
     *
     * Earth radius = 6371 km. Uses the standard formula:
     *   a = sin²(Δφ/2) + cos(φ1)·cos(φ2)·sin²(Δλ/2)
     *   c = 2·atan2(√a, √(1−a))
     *   d = R·c
     */
    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val sinDLat = sin(dLat / 2)
        val sinDLng = sin(dLng / 2)
        val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLng * sinDLng
        return r * 2 * atan2(sqrt(h), sqrt(1 - h))
    }
}
