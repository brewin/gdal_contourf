import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    `java-library`
    kotlin("jvm") version "1.3.50"
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("com.github.ben-manes.versions") version "0.22.0"
}

group = "com.github.brewin"
version = "1.0"

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.0")
    api("org.gdal:gdal:2.4.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

application {
    mainClassName = "com.github.brewin.gdal_contourf.GdalContourF"
}

/*tasks.withType<ShadowJar> {
    baseName = "gdal_contourf"
    classifier = ""
    version = ""
    destinationDir = File("dist")
}*/