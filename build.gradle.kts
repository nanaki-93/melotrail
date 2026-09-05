plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "app.melotrail"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
}

tasks.withType<JavaExec>().configureEach {
    val launcherProvider = javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
    javaLauncher.set(launcherProvider)
    executable(launcherProvider.map { it.executablePath.asFile.absolutePath })
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(24)
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    })
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
}

tasks.register<Exec>("checkDocumentationCoverage") {
    group = "verification"
    description = "Validate the checked-in Kotlin/Python function documentation inventory"
    commandLine("python3", "tools/check_documentation_coverage.py", "--repository", projectDir)
}

tasks.check {
    dependsOn("checkDocumentationCoverage")
}
