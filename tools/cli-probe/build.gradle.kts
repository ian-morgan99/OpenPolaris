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
    testImplementation(libs.kotlinx.coroutines.test)
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
    }
}
