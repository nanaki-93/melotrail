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

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.test {
    useJUnitPlatform()
    // Documentation integrity reads these files directly, so doc edits must invalidate test caching.
    inputs.files("AGENTS.md", "README.md", "PLAN.md", "TASKS.md")
    inputs.dir("docs")
}

tasks.register<JavaExec>("musicalEvaluation") {
    group = "verification"
    description = "Freeze supplied owned musical cases or export a hash-pinned frozen set (use --args)."
    dependsOn(tasks.classes)
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("app.melotrail.application.MidiCoreMusicalEvaluationCommand")
}

tasks.register<JavaExec>("musicalComparison") {
    group = "verification"
    description = "Export separate M01 synthetic baseline/current comparisons to a new directory (use --args)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("app.melotrail.application.MidiCoreComparisonCommand")
}

// Evidence preparation stays on the test classpath; it adds no desktop/runtime dependency.
tasks.register<JavaExec>("prepareLogicMatrix") {
    group = "verification"
    description = "Prepare current owned Logic MIDI packages and an unfilled human review matrix."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.melotrail.application.MidiCoreLogicMatrix")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir(rootDir)
    doFirst {
        val destination = providers.gradleProperty("logicMatrixDirectory").orNull
            ?: error("Choose a new output directory with -PlogicMatrixDirectory=build/q02-logic-matrix/<new-run>")
        setArgs(listOf(rootDir.absolutePath, file(destination).absolutePath))
    }
}
