plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(project(":annotations"))
    implementation(project(":integration-test"))
    ksp(project(":compiler"))

    testImplementation(libs.junit5)
    testImplementation(libs.assertk)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

idea {
    module {
        // Not using += due to https://github.com/gradle/gradle/issues/8749
        sourceDirs.plusAssign(file("build/generated/ksp/main/kotlin"))
        testSourceDirs.plusAssign(file("build/generated/ksp/test/kotlin"))
        generatedSourceDirs.plusAssign(
            arrayOf(
                file("build/generated/ksp/main/kotlin"),
                file("build/generated/ksp/test/kotlin")
            )
        )
    }
}