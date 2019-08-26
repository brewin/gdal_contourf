package com.github.brewin.gdal_contourf

import org.gdal.gdal.Band
import org.gdal.gdal.Dataset
import org.gdal.gdal.gdal
import org.gdal.gdalconst.gdalconstConstants
import org.gdal.osr.SpatialReference
import java.io.File

object GdalUtil {

    fun openRaster(filePath: String, gzipped: Boolean): Dataset {
        return gdal.OpenEx(
            "${if (gzipped) "/vsigzip/" else ""}${File(filePath).absolutePath}",
            gdalconstConstants.OF_READONLY.toLong()
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