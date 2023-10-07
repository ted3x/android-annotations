plugins {
    alias(libs.plugins.kotlin.jvm)
    id("maven-publish")
}

repositories {
    mavenCentral()
}

group = "com.github.ted3x.android-annotations"
version = "0.0.1"

dependencies {
    implementation(project(":annotations"))
    implementation(libs.ksp)
    implementation(libs.kotlinpoet.ksp)
}