plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
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
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
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

springBoot {
    mainClass.set("app.melotrail.server.ServerKt")
}

tasks.named<JavaExec>("bootRun") {
    jvmArgs("-Xmx4g")
}

tasks.register<JavaExec>("cliRun") {
    group = "application"
    description = "Run the Melotrail CLI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.melotrail.cli.CliMainKt")
    jvmArgs("-Xmx4g")
}
