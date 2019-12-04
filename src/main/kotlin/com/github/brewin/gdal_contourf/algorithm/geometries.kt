package com.github.brewin.gdal_contourf.algorithm

import org.gdal.ogr.Geometry
import org.gdal.ogr.ogrConstants

data class Point(val x: Double, val y: Double) : Geometry(ogrConstants.wkbPoint) {

    init {
        AddPoint_2D(x, y)
    }
}

data class Segment(val start: Point, val end: Point) : Geometry(ogrConstants.wkbLineString) {

    init {
        AddPoint_2D(start.x, start.y)
        AddPoint_2D(end.x, end.y)
    }
}

data class LinearRing(val points: List<Point>) : Geometry(ogrConstants.wkbLinearRing) {

    init {
        points.forEach { AddPoint_2D(it.x, it.y) }
    }

    val segments
        get() = points.zipWithNext { a, b -> Segment(a, b) }

    // Rings with segments in clockwise order are interior rings, or holes. (Shoelace Formula)
    val isInterior
        get() = segments.sumByDouble { (a, b) -> (b.x - a.x) * (b.y + a.y) } > 0
}

data class Polygon(val exteriorRing: LinearRing) : Geometry(ogrConstants.wkbPolygon) {

    init {
        AddGeometry(exteriorRing)
    }
}

data class GeometryCollection(
    val geometries: List<Geometry>
) : Geometry(ogrConstants.wkbMultiPolygon) {

    init {
        geometries.forEach { AddGeometry(it) }
    }
}