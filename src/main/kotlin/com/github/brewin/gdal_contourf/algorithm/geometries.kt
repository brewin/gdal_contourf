package com.github.brewin.gdal_contourf.algorithm

import org.gdal.ogr.Geometry
import org.gdal.ogr.ogrConstants

data class Point(val x: Double, val y: Double) : Geometry(ogrConstants.wkbPoint) {

    init {
        AddPoint(x, y)
    }
}

data class Segment(val start: Point, val end: Point) : Geometry(ogrConstants.wkbLineString) {

    init {
        AddPoint(start.x, start.y)
        AddPoint(end.x, end.y)
    }
}

data class LinearRing(val points: List<Point>) : Geometry(ogrConstants.wkbLinearRing) {

    init {
        segments.forEach { AddGeometry(it) }
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

    fun addInteriorRing(interiorRing: LinearRing) {
        AddGeometry(interiorRing)
    }
}

data class MultiPolygon(val polygons: List<Polygon>) : Geometry(ogrConstants.wkbMultiPolygon) {

    init {
        polygons.forEach { AddGeometry(it) }
    }
}