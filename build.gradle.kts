plugins {
    kotlin("jvm") version "1.9.23"
}

group = "info.skyblond"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bytedeco:javacv-platform:1.5.10")
    implementation("org.bytedeco:cuda-platform:12.3-8.9-1.5.100")
    implementation("org.bytedeco:cuda-platform-redist:12.3-8.9-1.5.10")
    implementation("org.bytedeco:opencv-platform-gpu:4.9.0-1.5.10")
    implementation("org.bytedeco:ffmpeg-platform-gpl:6.1.1-1.5.10")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
