package com.github.brewin.gdal_contourf.algorithm

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class Point(val x: Double, val y: Double)

internal data class Segment(val start: Point, val end: Point) {

    // https://rosettacode.org/wiki/Ray-casting_algorithm
    operator fun contains(point: Point): Boolean = when {
        start.y > end.y ->
            Segment(end, start).contains(point)
        point.y == start.y || point.y == end.y ->
            contains(Point(point.x, point.y + EPSILON))
        point.y > end.y || point.y < start.y || point.x > max(start.x, end.x) ->
            false
        point.x < min(start.x, end.x) ->
            true
        else ->
            when {
                abs(start.x - point.x) > Double.MIN_VALUE ->
                    (point.y - start.y) / (point.x - start.x)
                else ->
                    Double.MAX_VALUE
            } >= when {
                abs(start.x - end.x) > Double.MIN_VALUE ->
                    (end.y - start.y) / (end.x - start.x)
                else ->
                    Double.MAX_VALUE
            }
    }

    companion object {
        private const val EPSILON = 0.00001
    }
}

internal data class LinearRing(val points: List<Point>) {

    val segments
        get() = points.zipWithNext { a, b -> Segment(a, b) }

    // Rings with segments in clockwise order are interior rings, or holes. (Shoelace Formula)
    val isInterior
        get() = segments.sumByDouble { (a, b) -> (b.x - a.x) * (b.y + a.y) } > 0
}

internal data class Polygon(
    val exteriorRing: LinearRing,
    val interiorRings: ArrayList<LinearRing> = arrayListOf()
) {

    operator fun contains(point: Point): Boolean {
        exteriorRing.segments.forEach {
            if (it.contains(point)) return true
        }
        return false
    }

    // Brute force. Probably very slow.
    operator fun contains(ring: LinearRing): Boolean {
        exteriorRing.segments.forEach { segment ->
            ring.points.forEach { point ->
                if (segment.contains(point)) return true
            }
        }
        return false
    }
}