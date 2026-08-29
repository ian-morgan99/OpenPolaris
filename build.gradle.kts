plugins {
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
}

// Aggregate smoke tests: JVM unit tests for :shared plus the cli-probe
// E2E suite. The cli-probe tests run against an in-process FakeMount, so
// this task works on any developer machine without a real mount.
tasks.register("smoke") {
    group = "verification"
    description = "Run JVM unit + cli-probe smoke tests (no hardware required)"
    dependsOn(":shared:jvmTest", ":tools:cli-probe:test")
}

// Real-mount smoke tests against a physical Polaris. Gated behind
// -Popenpolaris.realMount=true; otherwise prints a skip message.
tasks.register("smokeReal") {
    group = "verification"
    description = "Run smoke tests against a real Polaris (requires -Popenpolaris.realMount=true)"
    val enabled = (project.findProperty("openpolaris.realMount") as String?)?.toBoolean() == true
    if (enabled) {
        dependsOn(":tools:cli-probe:test")
        doFirst {
            logger.lifecycle("smokeReal: running with real mount at configured address")
        }
    } else {
        doFirst {
            logger.lifecycle("smokeReal: skipped (set -Popenpolaris.realMount=true to enable)")
        }
    }
}
