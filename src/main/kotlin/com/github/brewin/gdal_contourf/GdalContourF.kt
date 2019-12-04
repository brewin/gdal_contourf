package com.github.brewin.gdal_contourf

import com.github.brewin.gdal_contourf.algorithm.MarchingSquares
import kotlinx.coroutines.runBlocking
import org.gdal.gdal.Dataset
import org.gdal.gdal.TranslateOptions
import org.gdal.gdal.gdal
import org.gdal.ogr.DataSource
import org.gdal.ogr.Feature
import org.gdal.ogr.FieldDefn
import org.gdal.ogr.ogr
import org.gdal.ogr.ogrConstants.*
import org.gdal.osr.SpatialReference
import java.util.*
import kotlin.system.exitProcess

object GdalContourF {

    init {
        gdal.AllRegister()
        gdal.UseExceptions()
        ogr.RegisterAll()
        ogr.UseExceptions()
    }

    private suspend fun process(
        grid: Array<DoubleArray>,
        levels: List<Double>,
        geoTransform: DoubleArray,
        outSrs: SpatialReference
    ): DataSource {
        val layerName = "levels"
        val featureName = "level"

        val outDataSource = ogr.GetDriverByName("Memory").CreateDataSource("")
        val layer = outDataSource.CreateLayer(layerName, outSrs, wkbGeometryCollection)
            .apply { CreateField(FieldDefn(featureName, OFTReal)) }

        MarchingSquares(grid, geoTransform)
            .contour(levels.toDoubleArray())
            .forEachIndexed { i, levelGeometry ->
                if (!levelGeometry.IsEmpty()) {
                    Feature(layer.GetLayerDefn())
                        .apply {
                            SetGeometry(levelGeometry)
                            SetField(featureName, levels[i])
                            layer.CreateFeature(this)
                            delete()
                        }
                }
            }

        return outDataSource
    }

    suspend fun rasterToVector(
        inputRaster: Dataset,
        band: Int,
        levels: List<Double>,
        simplification: Int,
        outputEpsgId: Int
    ): DataSource {
        require(simplification in 0..99) { "Error: simplification must be between 0 and 99" }

        val outSrs = SpatialReference()
            .apply { ImportFromEPSG(outputEpsgId) }
        val translateArgs = "-of VRT -r cubicspline -outsize ${100 - simplification}% 0"
        val reprojectedDataset = gdal.Translate(
            "/vsimem/reprojected.vrt",
            GdalUtil.reproject(inputRaster, outSrs),
            TranslateOptions(gdal.ParseCommandLine(translateArgs))
        )
        val grid = GdalUtil.bandTo2dDoubleArray(reprojectedDataset.GetRasterBand(band))
        val geoTransform = reprojectedDataset.GetGeoTransform()

        return process(grid, levels, geoTransform, outSrs)
    }

    suspend fun rasterToVector(
        inputRaster: Dataset,
        band: Int,
        levels: List<Double>,
        simplification: Int,
        outputEpsgId: Int,
        outputFormat: String,
        outputOptions: List<String>,
        outputPath: String
    ) {
        val outputDataSource = rasterToVector(
            inputRaster,
            band,
            levels,
            simplification,
            outputEpsgId
        )
        ogr.GetDriverByName(outputFormat)
            .CopyDataSource(outputDataSource, outputPath, Vector(outputOptions))
            .delete()
    }

    suspend fun rasterToVector(
        inputRasterPath: String,
        gzipped: Boolean,
        band: Int,
        levels: List<Double>,
        simplification: Int,
        outputEpsgId: Int,
        outputFormat: String,
        outputOptions: List<String>,
        outputPath: String
    ) {
        val outputDataSource = rasterToVector(
            GdalUtil.openRaster(inputRasterPath, gzipped),
            band,
            levels,
            simplification,
            outputEpsgId
        )
        ogr.GetDriverByName(outputFormat)
            .CopyDataSource(outputDataSource, outputPath, Vector(outputOptions))
            .delete()
    }

    suspend fun rasterToVector(
        inputRasterPath: String,
        gzipped: Boolean,
        band: Int,
        levels: List<Double>,
        simplification: Int,
        outputEpsgId: Int
    ): DataSource {
        return rasterToVector(
            GdalUtil.openRaster(inputRasterPath, gzipped),
            band,
            levels,
            simplification,
            outputEpsgId
        )
    }

    private fun mapArgs(args: Array<String>): Map<String, List<String>> =
        args.fold(Pair(emptyMap<String, List<String>>(), "")) { (map, lastKey), elem ->
            if (elem.startsWith("-")) Pair(map + (elem to emptyList()), elem)
            else Pair(map + (lastKey to map.getOrDefault(lastKey, emptyList()) + elem), lastKey)
        }.first

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            println("Usage:")
            println("java -jar gdal_contourf-<version>-all.jar --in input.tif --band 1 \\")
            println("   --levels 0 50 100 --simplification 30 --epsg 4326 \\")
            println("   --format MBTile --options MAXZOOM=7 --out output.mbtiles")
            exitProcess(1)
        }

        val a = mapArgs(args)

        val raster = a["--in"]?.singleOrNull()
            ?: throw IllegalArgumentException("--in is required (--in raster.tif)")
        val gzipped = a["--gzipped"]?.singleOrNull()?.toBoolean() ?: false
        val band = a["--band"]?.singleOrNull()?.toIntOrNull() ?: 1
        val levels = a["--levels"]?.map(String::toDouble)
            ?: throw IllegalArgumentException("--levels is required (--levels 0 50 100)")
        val simp = a["--simplification"]?.singleOrNull()?.toInt() ?: 0
        val epsg = a["--epsg"]?.singleOrNull()?.toIntOrNull() ?: 4326
        val format = a["--format"]?.singleOrNull()
            ?: throw IllegalArgumentException("--format is required (--format GeoJSON)")
        val options = a["--options"].orEmpty()
        val out = a["--out"]?.singleOrNull()
            ?: throw IllegalArgumentException("--out is required (--out out.geojson)")

        //arrayOf(raster, band, levels, simp, epsg, format, options, out).map(::println)

        runBlocking {
            rasterToVector(raster, gzipped, band, levels, simp, epsg, format, options, out)
        }
    }
}