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
    testImplementation(libs.kotlinx.coroutines.test)
    // BurstTest starts a stub TCP server in-process; depends on
    // dev.openpolaris.stub.runServer.
    testImplementation(project(":tools:stub-server"))
}

application {
    mainClass.set("dev.openpolaris.probe.MainKt")
}

tasks.register<JavaExec>("runFakeMount") {
    group = "application"
    description = "Run FakeMount on 127.0.0.1:<port> (default 9090)."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.openpolaris.probe.FakeMountKt")
    // Default port; override with -PfakeMountPort=NNNN.
    val port = (project.findProperty("fakeMountPort") as String?)?.toIntOrNull() ?: 9090
    args(port.toString())
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
    // Forward the real-mount toggle and target host/port (if provided as
    // Gradle project properties) into the test JVM as system properties.
    // The ProbeSmokeTest suite reads them via System.getProperty(...).
    val realMount = (project.findProperty("openpolaris.realMount") as String?)?.toBoolean() == true
    if (realMount) {
        val realHost = (project.findProperty("openpolaris.realMount.host") as String?) ?: "192.168.0.1"
        val realPort = (project.findProperty("openpolaris.realMount.port") as String?) ?: "9090"
        systemProperty("openpolaris.realMount", "true")
        systemProperty("openpolaris.realMount.host", realHost)
        systemProperty("openpolaris.realMount.port", realPort)
        val allowMotion = (project.findProperty("openpolaris.realMount.allowMotion") as String?)?.toBoolean() == true
        val allowDestructive = (project.findProperty("openpolaris.realMount.allowDestructive") as String?)?.toBoolean() == true
        if (allowMotion) systemProperty("openpolaris.realMount.allowMotion", "true")
        if (allowDestructive) systemProperty("openpolaris.realMount.allowDestructive", "true")
    }
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

// Long-lived passive listener that logs EVERY incoming frame to a file
// (default: push.log). Use --send or --send-step to fire a one-shot frame
// halfway through. Designed for investigating push-mode codes (525 Tempa,
// 524/517 AHRS, 779, 808) and any handshake triggered by SP_TEST=526.
// Args: <seconds> [host] [port] [--send <code>[:k=v;k=v]] [--send-step <n>] [--out <file>]
tasks.register<JavaExec>("liveListen") {
    group = "application"
    description = "Passive listener that logs every push-mode frame to push.log. Use --send/--send-step to probe."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.openpolaris.probe.PushListenerKt")
}

// End-to-end smoke test for the 820/821/823 app handshake. Connects via
// MountSession (so the full 284 + 820 + 823 sequence runs), then issues a
// single 519 EX_AXIS_STA read as proof that post-handshake commands work.
// Use to validate the handshake against a live gimbal; with no password
// the gimbal must report needed:0 for the probe to succeed.
// Args: <host> [port] [password] [--app <name>] [--ver <version>]
tasks.register<JavaExec>("authSmoke") {
    group = "application"
    description = "MountSession-based handshake smoke test against a live gimbal (or the stub-server)."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.openpolaris.probe.AuthSmokeKt")
    standardInput = System.`in`
}
