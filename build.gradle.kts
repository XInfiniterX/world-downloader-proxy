plugins {
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10"
    id("org.jetbrains.compose") version "1.11.1"
    id("com.gradleup.shadow") version "9.6.1"
    application
}

val javaVersion = property("java.version") as String
val skipTests = (property("skipTests") as String).toBoolean()

val arch = project.findProperty("arch") as String? ?: "arm64"

version = "2026.5"
group = "mircokroon.minecraft-world-downloader"

// GitHub repo for update checks
val repoUrl = "https://github.com/XInfiniterX/world-downloader-proxy"

repositories {
    mavenCentral()
    maven("https://jitpack.io")
    maven("https://repository.apache.org/content/repositories/snapshots/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Backend
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("commons-io:commons-io:2.18.0")
    implementation("org.apache.commons:commons-lang3:3.18.0")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.konghq:unirest-java:3.13.8")
    implementation("commons-codec:commons-codec:1.18.0")
    implementation("org.slf4j:slf4j-api:1.7.36")
    implementation("org.slf4j:slf4j-simple:1.7.36")
    implementation("com.github.llbit:jo-nbt:1baae2f49a")
    implementation("args4j:args4j:2.33")
    implementation("dnsjava:dnsjava:3.6.0")

    // Compose Desktop — GUI
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(compose.foundation)
    implementation(compose.runtime)
    // Compose resources — needed by Jewel (SVG icons for RadioButton/Checkbox etc.)
    implementation("org.jetbrains.compose.components:components-resources:1.11.1")
    // Material3 — only for the old spike core.gui.compose.MainCompose (not JavaFX)
    implementation(compose.material3)

    // Jewel — 0.39.1 built against Compose 1.11
    implementation("org.jetbrains.jewel:jewel-int-ui-standalone:0.39.1-262.9437.29")

    // Skiko native libs — include ALL platforms/arches in a single universal jar.
    // Skiko selects the correct native lib at runtime based on the host OS/arch.
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.144.6")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-arm64:0.144.6")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-x64:0.144.6")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-linux-arm64:0.144.6")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-x64:0.144.6")
    implementation("org.jetbrains.skiko:skiko-awt-runtime-macos-arm64:0.144.6")

    // Tests
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.2")
    testImplementation("org.junit.platform:junit-platform-launcher:1.8.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:4.5.1")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(javaVersion))
    }
}

kotlin {
    jvmToolchain(javaVersion.toInt())
}

// Resources filtering — version.txt and repo.txt
tasks.processResources {
    filesMatching(listOf("version.txt", "repo.txt")) {
        expand(mapOf(
            "project" to mapOf("version" to project.version),
            "repoUrl" to repoUrl,
        ))
    }
}

// Tests
tasks.test {
    useJUnitPlatform()
    enabled = !skipTests
}

// Fat jar (Shadow) — single universal jar for all platforms (mac/linux/win × arm/x86)
tasks.shadowJar {
    archiveClassifier.set("mac-linux-win-arm-x86")
    archiveBaseName.set("world-downloader-proxy")
    mergeServiceFiles()
    duplicatesStrategy = DuplicatesStrategy.WARN
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("module-info.class")
    exclude("META-INF/services/java.net.spi.InetAddressResolverProvider")
    manifest {
        attributes("Main-Class" to "core.gui.jewel.MainKt")
    }
}

// Make `build` produce the fat jar (like Maven `package` + shade plugin)
tasks.build {
    dependsOn(tasks.shadowJar)
}

// Disable application plugin's distribution archives — we ship the shadow fat
// jar, and distTar/distZip choke on duplicate JavaFX cross-OS classifier jars.
tasks.distTar { enabled = false }
tasks.distZip { enabled = false }

// Disable thin jar — only fat jar is useful
tasks.jar {
    enabled = false
}

// Run Compose GUI (Jewel)
tasks.register<JavaExec>("runCompose") {
    group = "application"
    mainClass.set("core.gui.jewel.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
}

application {
    mainClass.set("core.gui.jewel.MainKt")
}
