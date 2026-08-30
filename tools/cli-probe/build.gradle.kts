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
