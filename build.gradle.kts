plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "ai.music.workstation"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.test {
    useJUnitPlatform()
}

springBoot {
    mainClass.set("ai.music.workstation.server.ServerKt")
}

tasks.named<JavaExec>("bootRun") {
    jvmArgs("-Xmx4g")
}

tasks.register<JavaExec>("cliRun") {
    group = "application"
    description = "Run the AI Music Workstation CLI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ai.music.workstation.cli.CliMainKt")
    jvmArgs("-Xmx4g")
}
