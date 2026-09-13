plugins {
    kotlin("jvm")
    kotlin("plugin.compose") version "2.2.21"
    id("org.jetbrains.compose") version "1.11.0"
}

kotlin {
    jvmToolchain(21)
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

// Foreground evidence is a separate mandatory visual/release gate, never an unattended-test fallback.
tasks.register<Test>("nativeDesktopCapture") {
    group = "verification"
    description = "Capture the real desktop window for U07/native release review; requires a visible, capturable desktop."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("app.melotrail.desktop.MidiCoreNativeResponsivenessTest") }
    systemProperty("melotrail.nativeScreenCapture", "true")
    mustRunAfter(tasks.test)
    outputs.upToDateWhen { false }
}

// Host coordinator only: install the actual DMG into a new private directory and launch its bundled JVM.
tasks.register<JavaExec>("nativeInstallSmoke") {
    group = "verification"
    description = "Install and start the macOS DMG without user preferences or an external JVM/runtime."
    dependsOn("packageDmg", tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.melotrail.desktop.MidiCoreNativeInstallCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir(rootDir)
    doFirst {
        val destination = providers.gradleProperty("nativeInstallDirectory").orNull
            ?: error("Choose a new evidence directory with -PnativeInstallDirectory=<new-directory>; its parent must exist")
        setArgs(listOf(rootDir.absolutePath, layout.buildDirectory.dir("compose/binaries/main/dmg").get().asFile.absolutePath,
            file(destination).absolutePath))
    }
}
