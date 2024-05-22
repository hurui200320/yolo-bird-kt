import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

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
    if (DefaultNativePlatform.getCurrentOperatingSystem().isWindows) {
        // not in AWS
        implementation("org.bytedeco:cuda-platform:12.3-8.9-1.5.100")
        implementation("org.bytedeco:cuda-platform-redist:12.3-8.9-1.5.10")
    }

    implementation("com.github.ajalt.colormath:colormath:3.5.0")
    implementation("com.microsoft.onnxruntime:onnxruntime_gpu:1.17.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("info.skyblond.yolo.bird.DetectAndMoveKt")
    if (DefaultNativePlatform.getCurrentOperatingSystem().isLinux)
        applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
