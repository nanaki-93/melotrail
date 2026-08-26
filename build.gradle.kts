plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
}

group = "app.melotrail"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.4")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.test {
    useJUnitPlatform()
    System.getProperty("melotrail.dawSpikeDirectory")?.let { directory ->
        systemProperty("melotrail.dawSpikeDirectory", directory)
    }
}

tasks.register<Exec>("checkDocumentationCoverage") {
    group = "verification"
    description = "Validate the checked-in Kotlin/Python function documentation inventory"
    commandLine("python3", "tools/check_documentation_coverage.py", "--repository", projectDir)
}

tasks.check {
    dependsOn("checkDocumentationCoverage")
}
