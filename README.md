## gdal_contourf
Generates filled contours (polygons) from raster data. It runs on the 
JVM and is optimized for speed. A large raster can typically be processed in a 
matter of seconds. It uses [GDAL](https://gdal.org/) to read raster files and 
write vector files. Contours are generated using the 
[Marching Squares](https://en.wikipedia.org/wiki/Marching_squares) algorithm.

Note that as of GDAL v2.4.0, 
[gdal_contour](https://gdal.org/programs/gdal_contour.html) can produce 
polygons using the -p option, but it is very slow.

#### Requirements
- Java
- GDAL 
- GDAL Java bindings (libgdal-java on Debian/Ubuntu, gdal-java on Fedora/Centos)

#### Building
Make sure the GDAL version in build.gradle.kts matches the system version. (ie. 3.0.0)

    ./gradlew shadowJar

#### Usage
It can be used as a library with Gradle:
    
    repositories {
        jcenter()
        maven("https://jitpack.io")
    }
    dependencies {
        implementation("com.github.brewin:gdal_contourf:1.1.2")
    }
    
Or run from the command line:

    java -jar gdal_contourf-1.1.2_gdal-3.0.0.jar --in input.tif --band 1 \
        --levels -50 0 50 100 150 --simplification 30 --epsg 4326 \
        --format GeoJSON --out output.geojson
        
#### Disclaimer
This is not well-tested and probably never will be. It works for my use-case. 
Correctness not guaranteed.
