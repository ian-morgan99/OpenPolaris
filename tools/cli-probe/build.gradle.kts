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
}
