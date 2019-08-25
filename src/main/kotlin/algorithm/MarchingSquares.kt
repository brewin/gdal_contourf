package algorithm

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Marching Squares implementation that outputs polygons.
 *
 * Sources:
 * https://en.wikipedia.org/wiki/Marching_squares
 * https://github.com/d3/d3-contour
 * https://github.com/azrafe7/hxGeomAlgo
 *
 * @param grid a 2d grid of data.
 * @param geoTransform a 6-element GeoTransform in GDAL order. (c,a,b,f,d,e)
 */
class MarchingSquares(
    grid: Array<DoubleArray>,
    private val geoTransform: DoubleArray
) {

    private val paddedGrid = pad(grid)
    private val epsilon = 10e-6

    // Line segments with points in CCW polygon order
    private val segmentTable = arrayOf(
        // 0000
        arrayOf(),
        // 0001
        arrayOf(arrayOf(Segment(Point(0.5, 0.0), Point(0.0, 0.5)))),
        // 0010
        arrayOf(arrayOf(Segment(Point(1.0, 0.5), Point(0.5, 0.0)))),
        // 0011
        arrayOf(arrayOf(Segment(Point(1.0, 0.5), Point(0.0, 0.5)))),
        // 0100
        arrayOf(arrayOf(Segment(Point(0.5, 1.0), Point(1.0, 0.5)))),
        // 0101
        arrayOf(
            arrayOf(
                Segment(Point(0.5, 0.0), Point(0.0, 0.5)),
                Segment(Point(0.5, 1.0), Point(1.0, 0.5))
            ),
            arrayOf(
                Segment(Point(0.5, 1.0), Point(0.0, 0.5)),
                Segment(Point(0.5, 0.0), Point(1.0, 0.5))
            )
        ),
        // 0110
        arrayOf(arrayOf(Segment(Point(0.5, 1.0), Point(0.5, 0.0)))),
        // 0111
        arrayOf(arrayOf(Segment(Point(0.5, 1.0), Point(0.0, 0.5)))),
        // 1000
        arrayOf(arrayOf(Segment(Point(0.0, 0.5), Point(0.5, 1.0)))),
        // 1001
        arrayOf(arrayOf(Segment(Point(0.5, 0.0), Point(0.5, 1.0)))),
        // 1010
        arrayOf(
            arrayOf(
                Segment(Point(0.0, 0.5), Point(0.5, 1.0)),
                Segment(Point(1.0, 0.5), Point(0.5, 0.0))
            ),
            arrayOf(
                Segment(Point(0.0, 0.5), Point(0.5, 0.0)),
                Segment(Point(1.0, 0.5), Point(0.5, 1.0))
            )
        ),
        // 1011
        arrayOf(arrayOf(Segment(Point(1.0, 0.5), Point(0.5, 1.0)))),
        // 1100
        arrayOf(arrayOf(Segment(Point(0.0, 0.5), Point(1.0, 0.5)))),
        // 1101
        arrayOf(arrayOf(Segment(Point(0.5, 0.0), Point(1.0, 0.5)))),
        // 1110
        arrayOf(arrayOf(Segment(Point(0.0, 0.5), Point(0.5, 0.0)))),
        // 1111
        arrayOf()
    )

    init {
        require(geoTransform.size == 6) {
            "geoTransform must be 6 elements in GDAL order. (c,a,b,f,d,e)"
        }
    }

    /**
     * Asynchronously computes isocontours of a 2d grid
     *
     * @param levels a list of isovalues
     * @return a list of lists of polygons (i.e. layers of multipolygons)
     */
    suspend fun contour(levels: DoubleArray): List<List<Polygon>> =
        coroutineScope {
            levels.map { level ->
                async {
                    println("Contouring level $level...")

                    val adjacencyMap = buildAdjacencyMap(level)
                    val rings = arrayListOf<LinearRing>()

                    var segment: Segment
                    var forward: Point?
                    var backward: Point?
                    var forwardLine: ArrayList<Point>
                    var backwardLine: ArrayList<Point>
                    while (true) {
                        segment = adjacencyMap.firstSegment() ?: break
                        forward = segment.end
                        backward = segment.start
                        forwardLine = arrayListOf(forward)
                        backwardLine = arrayListOf(backward)

                        while (backward != null || forward != null) {
                            forward = forward
                                ?.let(adjacencyMap::endFromStart)
                                ?.also { forwardLine.add(it) }
                            backward = backward
                                ?.let(adjacencyMap::startFromEnd)
                                ?.also { backwardLine.add(it) }
                        }

                        rings.add(LinearRing(backwardLine.reversed() + forwardLine))
                    }

                    // Attach interior rings (holes) to their exteriors to create polygons.
                    val levelPolygons = arrayListOf<Polygon>()
                    val (interiors, exteriors) = rings.partition(LinearRing::isInterior)
                    val unattachedInteriorIndices = interiors.indices.toMutableSet()
                    val attachedInteriorIndices = mutableSetOf<Int>()
                    var polygon: Polygon
                    for (exterior in exteriors) {
                        polygon = Polygon(exterior)
                        for (i in unattachedInteriorIndices) {
                            if (polygon.contains(interiors[i])) {
                                polygon.interiorRings.add(interiors[i])
                                attachedInteriorIndices.add(i)
                            }
                        }
                        unattachedInteriorIndices.removeAll(attachedInteriorIndices)
                        levelPolygons.add(polygon)
                    }

                    levelPolygons
                }
            }.awaitAll()
        }

    private fun buildAdjacencyMap(level: Double): AdjacencyMap {
        val adjacencyMap = AdjacencyMap()

        var tl: Double
        var tr: Double
        var br: Double
        var bl: Double
        var index: Int
        var flip: Int

        for (x in 0 until paddedGrid.size - 1) {
            for (y in 0 until paddedGrid[0].size - 1) {
                tl = paddedGrid[x][y + 1]
                tr = paddedGrid[x + 1][y + 1]
                br = paddedGrid[x + 1][y]
                bl = paddedGrid[x][y]

                // Calculate the Marching Squares cell index.
                index = 0b0000
                if (tl < level) index = index or 0b1000
                if (tr < level) index = index or 0b0100
                if (br < level) index = index or 0b0010
                if (bl < level) index = index or 0b0001

                // Ignore cells with no intersecting segments.
                if (index == 0b0000 || index == 0b1111) continue

                // Disambiguate saddles.
                flip =
                    if ((index == 0b0101 || index == 0b1010) && ((tl + tr + br + bl) / 4 < level)) 1
                    else 0

                // Form the line segment(s) and add them to the adjacency map.
                for ((start, end) in segmentTable[index][flip]) {
                    adjacencyMap.addSegment(
                        gridPositionToLonLat(
                            interpolateLinear(
                                level,
                                Point(x + floor(start.x), y + ceil(start.y)),
                                Point(x + ceil(start.x), y + floor(start.y)),
                                paddedGrid[x + floor(start.x).toInt()][y + ceil(start.y).toInt()],
                                paddedGrid[x + ceil(start.x).toInt()][y + floor(start.y).toInt()]
                            )
                        ),
                        gridPositionToLonLat(
                            interpolateLinear(
                                level,
                                Point(x + floor(end.x), y + ceil(end.y)),
                                Point(x + ceil(end.x), y + floor(end.y)),
                                paddedGrid[x + floor(end.x).toInt()][y + ceil(end.y).toInt()],
                                paddedGrid[x + ceil(end.x).toInt()][y + floor(end.y).toInt()]
                            )
                        )
                    )
                }
            }
        }

        return adjacencyMap
    }

    private fun interpolateLinear(
        level: Double,
        pointA: Point,
        pointB: Point,
        valueA: Double,
        valueB: Double
    ): Point {
        if (abs(level - valueA) < epsilon) return pointA
        if (abs(level - valueB) < epsilon) return pointB
        if (abs(valueA - valueB) < epsilon) return pointA

        val mu = (level - valueA) / (valueB - valueA)
        return Point(
            pointA.x + mu * (pointB.x - pointA.x),
            pointA.y + mu * (pointB.y - pointA.y)
        )
    }

    private fun gridPositionToLonLat(position: Point) =
        Point(
            geoTransform[0] + (position.x + 0.5) * geoTransform[1] +
                    (position.y + 0.5) * geoTransform[2],
            geoTransform[3] + (position.x + 0.5) * geoTransform[4] +
                    (position.y + 0.5) * geoTransform[5]
        )

    // Surround the data with extremely low values, forcing lines that exit sides to form
    // closed rings.
    private fun pad(grid: Array<DoubleArray>) =
        Array(grid.size + 2) { x ->
            DoubleArray(grid[0].size + 2) { y ->
                if (x == 0 || y == 0 || x == grid.size + 2 - 1 || y == grid[0].size + 2 - 1) {
                    -Double.MAX_VALUE
                } else {
                    grid[x - 1][y - 1]
                }
            }
        }
}