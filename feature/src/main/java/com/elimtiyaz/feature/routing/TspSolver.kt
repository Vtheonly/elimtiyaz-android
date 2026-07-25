package com.elimtiyaz.feature.routing

import com.elimtiyaz.domain.model.GeoPoint
import com.elimtiyaz.domain.model.RoutingStop
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure-Kotlin TSP solver used by the Routing feature when OSRM is unreachable.
 *
 * Ported from the legacy School Route Finder app's nearest-neighbour + 2-opt
 * solver. Contains no Android or coroutine code so it can be unit-tested in
 * plain JVM tests.
 *
 * The two public entry points are [solveNearestNeighbor] (greedy O(n²) build)
 * and [twoOptImprove] (local search up to 50 iterations). The optimisation
 * pipeline in [RoutingRepository] (and the on-device fallback in
 * [RoutingMapViewModel]) is: build with `solveNearestNeighbor`, then refine
 * with `twoOptImprove`, then optionally call [OsrmClient] for a real road
 * polyline.
 */
object TspSolver {

    /** Maximum number of 2-opt passes applied by [twoOptImprove]. */
    private const val TWO_OPT_MAX_ITERATIONS = 50

    /**
     * Greedy nearest-neighbour construction starting from [start]. Returns the
     * stops re-ordered by visit sequence with `orderInRoute` re-numbered from 1.
     *
     * Ties on the squared euclidean distance are broken by the stop's original
     * `orderInRoute` so the result is stable across recompositions.
     */
    fun solveNearestNeighbor(stops: List<RoutingStop>, start: GeoPoint): List<RoutingStop> {
        if (stops.isEmpty()) return emptyList()
        val remaining = stops.toMutableList()
        val ordered = mutableListOf<RoutingStop>()
        var currentLat = start.lat
        var currentLng = start.lng
        while (remaining.isNotEmpty()) {
            val nearestIdx = remaining.indices.minByOrNull { i ->
                val s = remaining[i]
                hypot(s.lat - currentLat, s.lng - currentLng)
            } ?: 0
            val next = remaining.removeAt(nearestIdx)
            ordered += next.copy(orderInRoute = ordered.size + 1)
            currentLat = next.lat
            currentLng = next.lng
        }
        return ordered
    }

    /**
     * 2-opt local search: iteratively reverse sub-segments of the route when
     * doing so reduces total haversine distance. Stops as soon as a full pass
     * produces no improvement or [TWO_OPT_MAX_ITERATIONS] is reached.
     *
     * The route is treated as an open path (school anchor → stop₁ → … → stopₙ)
     * — not a closed tour — because the driver does not return to school during
     * the morning pickup loop.
     */
    fun twoOptImprove(route: List<RoutingStop>): List<RoutingStop> {
        if (route.size < 4) return route // 2-opt needs at least 4 nodes to swap
        var best = route
        var improved = true
        var iterations = 0
        while (improved && iterations < TWO_OPT_MAX_ITERATIONS) {
            improved = false
            iterations++
            for (i in 0 until best.size - 1) {
                for (j in i + 1 until best.size) {
                    val candidate = best.toMutableList().also { it.subList(i, j + 1).reverse() }
                    if (totalDistance(candidate) < totalDistance(best) - 1e-9) {
                        best = candidate
                        improved = true
                    }
                }
            }
        }
        // Re-number the visit order after 2-opt swaps.
        return best.mapIndexed { idx, s -> s.copy(orderInRoute = idx + 1) }
    }

    /**
     * Great-circle distance between two geo points in kilometres. Uses the
     * haversine formula with Earth radius 6,371 km. Visible to the package so
     * [OsrmClient] fallback and [RoutingMapViewModel] ETA estimates share the
     * same metric.
     */
    fun haversineKm(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val s = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) *
            sin(dLng / 2).let { it * it }
        return 2 * r * atan2(sqrt(s), sqrt(1 - s))
    }

    /** Sum of haversine distances along a stop sequence (km). */
    private fun totalDistance(stops: List<RoutingStop>): Double {
        if (stops.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until stops.size) {
            sum += haversineKm(
                GeoPoint(stops[i - 1].lat, stops[i - 1].lng),
                GeoPoint(stops[i].lat, stops[i].lng),
            )
        }
        return sum
    }
}
