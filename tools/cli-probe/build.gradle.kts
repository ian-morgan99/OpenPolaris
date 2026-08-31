plugins {
    kotlin("jvm")
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(project(":tools:stub-server"))
}

application {
    mainClass.set("dev.openpolaris.probe.MainKt")
}

tasks.register<JavaExec>("burst") {
    group = "application"
    description = "Send a burst of codes to the stub. Args: <host> <port> <code,code,...>"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.openpolaris.probe.BurstKt")
}

// Full pre-camera burst (uses CommandTable.BURST_PRE_CAMERA — all 9 codes).
// Args: <host> <port> --full   (host/port default to 192.168.0.1:9090)
tasks.register<JavaExec>("liveBurst") {
    group = "application"
    description = "Send the canonical pre-camera burst to a live gimbal. Args: [host] [port] --full"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.openpolaris.probe.BurstKt")
    standardInput = System.`in`
}

// Comprehensive smoke test of every CommandTable code (read + write where safe).
// Set DESTRUCTIVE=1 to also exercise setters.
// Args: <host> <port>
tasks.register<JavaExec>("smoke") {
    group = "application"
    description = "Smoke-test every code against the stub. DESTRUCTIVE=1 includes setters."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.openpolaris.probe.SmokeKt")
    standardInput = System.`in`
}
