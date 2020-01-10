package com.github.brewin.gdal_contourf

import kotlinx.coroutines.*

/**
 * Convenience type aliases
 */
private typealias Vertex<T> = Pair<T, T>

private typealias Edge<T> = Pair<Vertex<T>, Vertex<T>>

/**
 * Marching Squares implementation that outputs polygons.
 * https://en.wikipedia.org/wiki/Marching_squares
 *
 * @param grid a 2d grid of data.
 */
class MarchingSquares(grid: Array<DoubleArray>) {

    private val colSize = grid.size
    private val rowSize = grid.first().size

    // TODO: This is simple and works well enough for large grids. Grid side values are lost though,
    //  which isn't ideal especially for very small grids.
    // Replace the first and last columns and rows with extremely low values, forcing lines that
    // exit sides to form closed rings.
    private val closedGrid by lazy {
        Array(colSize) { col ->
            DoubleArray(rowSize) { row ->
                if (col == 0 || row == 0 || col == colSize - 1 || row == rowSize - 1)
                    -Double.MAX_VALUE
                else grid[col][row]
            }
        }
    }

    /**
     * Contour multiple levels in parallel. Produces linear rings, where rings with vertices in
     * counterclockwise order are exterior and rings with vertices in clockwise order of interior.
     *
     * @param levels a list of levels to contour.
     * @param smooth whether to apply linear interpolation to smooth edges.
     */
    suspend fun contourRings(
        levels: List<Double>,
        smooth: Boolean
    ): Set<Set<List<Vertex<Double>>>> =
        coroutineScope {
            levels.map { level ->
                async { connectRings(contourEdges(level, smooth)) }
            }.awaitAll().toSet()
        }

    private suspend fun contourEdges(
        level: Double,
        smooth: Boolean
    ): Map<Vertex<Double>, Vertex<Double>> =
        withContext(Dispatchers.Default) {
            (0 until colSize - 1).flatMap { col ->
                (0 until rowSize - 1).flatMap { row ->
                    edges(col, row, level, smooth)
                }
            }.toMap()
        }

    // Interpolation. Use epsilon to prevent duplicate vertices.
    private fun fraction(a: Double, b: Double, level: Double): Double =
        if (a == b) 0.5
        else ((level - a) / (b - a)).coerceIn(EPSILON, 1 - EPSILON)

    // Compute contour edges that can later be connected into linear rings.
    private fun edges(col: Int, row: Int, level: Double, smooth: Boolean): Set<Edge<Double>> {
        val tl = closedGrid[col][row]
        val tr = closedGrid[col + 1][row]
        val br = closedGrid[col + 1][row + 1]
        val bl = closedGrid[col][row + 1]

        // Lazily compute side crossing points.
        val top by lazy {
            if (smooth) Vertex(col + 0.5 + fraction(tl, tr, level), row + 0.5)
            else Vertex(col + 1.0, row + 0.5)
        }
        val bottom by lazy {
            if (smooth) Vertex(col + 0.5 + fraction(bl, br, level), row + 1.5)
            else Vertex(col + 1.0, row + 1.5)
        }
        val right by lazy {
            if (smooth) Vertex(col + 1.5, row + 0.5 + fraction(tr, br, level))
            else Vertex(col + 1.5, row + 1.0)
        }
        val left by lazy {
            if (smooth) Vertex(col + 0.5, row + 0.5 + fraction(tl, bl, level))
            else Vertex(col + 0.5, row + 1.0)
        }

        // Compute Marching Squares case
        var case = 0b0000
        if (tl > level) case = case or 0b1000
        if (tr > level) case = case or 0b0100
        if (br > level) case = case or 0b0010
        if (bl > level) case = case or 0b0001

        // Lazily disambiguate saddles based on center average. Only applies to 0101 and 1010.
        val flip by lazy { (tl + tr + br + bl) / 4 < level }

        // Return edges based on case. Vertices are in CCW order for exteriors and CW for interiors.
        return when (case) {
            0b0000 -> setOf()
            0b0001 -> setOf(Edge(bottom, left))
            0b0010 -> setOf(Edge(right, bottom))
            0b0011 -> setOf(Edge(right, left))
            0b0100 -> setOf(Edge(top, right))
            0b0101 ->
                if (flip) setOf(Edge(bottom, left), Edge(top, right))
                else setOf(Edge(top, left), Edge(bottom, right))
            0b0110 -> setOf(Edge(top, bottom))
            0b0111 -> setOf(Edge(top, left))
            0b1000 -> setOf(Edge(left, top))
            0b1001 -> setOf(Edge(bottom, top))
            0b1010 ->
                if (flip) setOf(Edge(left, top), Edge(right, bottom))
                else setOf(Edge(left, bottom), Edge(right, top))
            0b1011 -> setOf(Edge(right, top))
            0b1100 -> setOf(Edge(left, right))
            0b1101 -> setOf(Edge(bottom, right))
            0b1110 -> setOf(Edge(left, bottom))
            0b1111 -> setOf()
            else -> error("Invalid Marching Squares case")
        }
    }

    // Connect edges into linear rings where the last vertex is the same as the first.
    private fun <T> connectRings(edgeMap: Map<Vertex<T>, Vertex<T>>): Set<List<Vertex<T>>> {
        tailrec fun connect(vertices: List<Vertex<T>>, count: Int = 1): List<Vertex<T>> = when {
            count > edgeMap.size -> error("Failed to find edge key matching vertex: ${vertices.last()}")
            vertices.size > 2 && vertices.last() == vertices.first() -> vertices
            else -> connect(vertices + edgeMap.getValue(vertices.last()), count + 1)
        }

        val unconnected = edgeMap.keys.toMutableSet()
        return edgeMap.keys.mapNotNull { vertex ->
            if (vertex in unconnected) connect(listOf(vertex)).also { unconnected -= it }
            else null
        }.toSet()
    }

    companion object {

        private const val EPSILON = 1e-8

        // https://en.wikipedia.org/wiki/Shoelace_formula
        fun partitionExteriorInterior(rings: Set<List<Vertex<Double>>>) =
            rings.partition {
                it.zipWithNext().sumByDouble { (v0, v1) ->
                    (v1.first - v0.first) * (v1.second + v0.second)
                } > 0
            }
    }
}