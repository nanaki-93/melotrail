import java.nio.file.Path

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
    implementation("net.java.dev.jna:jna:5.17.0")

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

// V11 keeps this explicit host probe on the test classpath so model startup stays outside app startup.
tasks.register<JavaExec>("videoLocalProbe") {
    group = "verification"
    description = "Prepare a pinned request, or run its explicit bounded local-video execution block."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.melotrail.video.VideoLocalFeasibilityCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir(rootDir)
    doFirst {
        val request = providers.gradleProperty("videoProbeRequest").orNull
            ?: error("Supply an absolute request file with -PvideoProbeRequest=/absolute/path/to/file.json")
        require(file(request).isAbsolute) {
            "videoProbeRequest must be absolute: $request"
        }
        setArgs(listOf(request))
    }
}

// V12 keeps native media proof on the test classpath and outside MIDI/app startup.
tasks.register<JavaExec>("videoMediaProbe") {
    group = "verification"
    description = "Decode, seek and silently encode the owned video fixture with pinned local FFmpeg tools."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.melotrail.video.VideoMediaHostCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir(rootDir)
    doFirst {
        val toolsDirectory = providers.gradleProperty("videoToolsDirectory").orNull
            ?: error("Supply the separately installed tools with -PvideoToolsDirectory=/absolute/path/to/tools")
        require(Path.of(toolsDirectory).isAbsolute) {
            "videoToolsDirectory must be absolute: $toolsDirectory"
        }
        val arguments = mutableListOf(toolsDirectory)
        providers.gradleProperty("videoMediaProbeOutput").orNull?.let { output ->
            require(Path.of(output).isAbsolute) {
                "videoMediaProbeOutput must be absolute: $output"
            }
            arguments += output
        }
        setArgs(arguments)
    }
}

// VG2 preview proof runs only when explicitly requested with pinned host paths and a new destination.
tasks.register<JavaExec>("videoPreviewProbe") {
    group = "verification"
    description = "Run bounded sequential controlled previews through production video services (opt-in)."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.melotrail.video.VideoPreviewHostCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir(rootDir)
    doFirst {
        val names = listOf("videoToolsDirectory", "videoNodeExecutable", "videoCanvasManifest", "videoPreviewOutput")
        val values = names.map { name ->
            val value = providers.gradleProperty(name).orNull
                ?: error("Supply -P$name=/absolute/path (all four videoPreviewProbe paths are required)")
            require(Path.of(value).isAbsolute && Path.of(value).normalize() == Path.of(value)) {
                "$name must be an absolute normalized path: $value"
            }
            value
        }
        setArgs(values)
    }
}

// V17 keeps the real ComfyUI generation proof explicit and off ordinary test/app startup paths.
tasks.register<JavaExec>("comfyVideoProbe") {
    group = "verification"
    description = "Run one bounded image-conditioned shot through the owned production ComfyUI job path."
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("app.melotrail.video.ComfyVideoHostCheck")
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
    workingDir(rootDir)
    doFirst {
        val request = providers.gradleProperty("comfyVideoRequest").orNull
            ?: error("Supply an owned request with -PcomfyVideoRequest=/absolute/path/to/request.json")
        require(Path.of(request).isAbsolute) {
            "comfyVideoRequest must be absolute: $request"
        }
        setArgs(listOf(request))
    }
}
