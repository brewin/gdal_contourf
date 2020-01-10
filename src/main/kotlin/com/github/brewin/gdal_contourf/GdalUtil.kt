package com.github.brewin.gdal_contourf

import org.gdal.gdal.Band
import org.gdal.gdal.Dataset
import org.gdal.gdal.gdal
import org.gdal.gdalconst.gdalconstConstants
import org.gdal.osr.SpatialReference
import java.io.File

internal object GdalUtil {

    fun openRaster(filePath: String, gzipped: Boolean): Dataset {
        return gdal.OpenEx(
            "${if (gzipped) "/vsigzip/" else ""}${File(filePath).absolutePath}",
            gdalconstConstants.OF_READONLY.toLong()
        )
    }

    fun openVector(filePath: String, gzipped: Boolean): Dataset {
        return gdal.OpenEx(
            "${if (gzipped) "/vsigzip/" else ""}${File(filePath).absolutePath}",
            gdalconstConstants.OF_VECTOR.toLong()
        )
    }

    fun reproject(dataset: Dataset, srs: SpatialReference): Dataset =
        gdal.AutoCreateWarpedVRT(
            dataset,
            dataset.GetProjectionRef(),
            srs.ExportToWkt(),
            gdalconstConstants.GRA_Bilinear
        )

    fun bandTo2dDoubleArray(band: Band): Array<DoubleArray> {
        // Read data into 1d array.
        val dataArray = DoubleArray(band.xSize * band.ySize)
        band.ReadRaster(0, 0, band.xSize, band.ySize, dataArray)

        // Convert 1d array to 2d array.
        return Array(band.xSize) { x ->
            DoubleArray(band.ySize) { y ->
                dataArray[y * band.xSize + x]
            }
        }
    }
}

fun Double.rescale(
    domain0: Double,
    domain1: Double,
    range0: Double,
    range1: Double
): Double {
    fun interpolate(value: Double): Double {
        return range0 * (1f - value) + range1 * value
    }

    fun uninterpolate(value: Double): Double {
        return (value - domain0) / if (domain1 - domain0 != 0.0) domain1 - domain0 else 1.0 / domain1
    }

    return interpolate(uninterpolate(this))
}