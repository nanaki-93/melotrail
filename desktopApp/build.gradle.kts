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
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.16"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.compose.ui:ui-test-junit4-desktop:1.11.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "ai.music.workstation.desktop.DesktopMainKt"

        nativeDistributions {
            packageName = "Personal AI Music Arranger"
            packageVersion = rootProject.version.toString()
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
        }
    }
}
