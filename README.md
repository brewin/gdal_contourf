## gdal_contourf
Generates filled contours (polygons) from a raster dataset. It runs on the 
JVM and is optimized for speed. A large raster can typically be processed in a 
matter of seconds. It uses [GDAL](https://gdal.org/) to read raster files and 
write vector files. Contours are generated using the 
[Marching Squares](https://en.wikipedia.org/wiki/Marching_squares) com.github.brewin.gdal_contourf.algorithm.

Note that as of GDAL v2.4.0, 
[gdal_contour](https://gdal.org/programs/gdal_contour.html) can produce 
polygons using the -p option, but it is very slow.

#### Requirements
- Java
- GDAL 
- GDAL Java bindings (install the gdal-java package on Debian/Ubuntu and Fedora)

#### Building
Make sure the GDAL version in build.gradle.kts matches the system version. (ie. 2.4.x)

    ./gradlew shadowJar

#### Usage
    java -jar gdal_contourf.jar --in raster.tif --band 1 \
        --levels 0,50,100,150 --simplification 30 --epsg 4326 \
        --format GeoJSON --out hello.geojson
        
#### Disclaimer
This is not well-tested and probably never will be. It works for my use-case. 
Accuracy not guaranteed.