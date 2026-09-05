plugins {
    kotlin("jvm")
    kotlin("plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.11.0"
}

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
    implementation(project(":"))
    implementation(compose.desktop.currentOs)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.test {
    useJUnitPlatform()
    if (System.getenv("MELOTRAIL_RUN_LIVE_E2E") == "1" || System.getenv("MELOTRAIL_RESUME_LIVE_E2E") == "1") {
        maxHeapSize = "2g"
    }
}

compose.desktop {
    application {
        mainClass = "app.melotrail.desktop.DesktopMainKt"

        nativeDistributions {
            packageName = "Melotrail"
            // jpackage requires a positive major component; keep the engine's 0.x version independent.
            packageVersion = "1.0.0"
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            modules("java.desktop", "java.logging", "java.prefs", "java.management")
            macOS {
                iconFile.set(project.file("src/main/resources/Melotrail.icns"))
            }
        }
    }
}
