import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "1.9.23"
    application
}

group = "info.skyblond"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bytedeco:javacv-platform:1.5.10")
    if (System.getenv("GRADLE_BUILD_OMIT_CUDA").isNullOrBlank()) {
        implementation("org.bytedeco:cuda-platform:12.3-8.9-1.5.10")
        implementation("org.bytedeco:cuda-platform-redist:12.3-8.9-1.5.10")
    }
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("com.github.ajalt.colormath:colormath:3.5.0")
    implementation("com.microsoft.onnxruntime:onnxruntime_gpu:1.18.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

application {
    mainClass.set("info.skyblond.yolo.bird.MainKt")
    applicationDefaultJvmArgs = listOf(
        // we don't need the full GPU features for awt
        // added this flag to avoid issues on headless linux
        "-Djava.awt.headless=true",
    )
}

// This is for Windows, when the classpath is too long
tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        val newScript = windowsScript.readText().replace(
            "set CLASSPATH=.*".toRegex(),
            "set CLASSPATH=.;%APP_HOME%/lib/*"
        )
        windowsScript.writeText(newScript)
    }
}

