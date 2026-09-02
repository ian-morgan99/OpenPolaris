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
//
// When enabled, the cli-probe test suite re-routes itself to talk to a
// real mount at -Popenpolaris.realMount.host:openpolaris.realMount.port
// (defaults 192.168.0.1:9090) instead of the in-process FakeMount.
//
// The forwarding of those project properties into the test JVM as -D
// system properties happens inside `tools/cli-probe/build.gradle.kts`,
// which keeps the wiring local to the subproject that owns the test.
//
// Two opt-in levels:
//   smokeReal       — read-only / non-motion subset. Default for safety.
//   smokeRealFull   — motion + destructive. Requires the extra flags:
//       -Popenpolaris.realMount.allowMotion=true
//       -Popenpolaris.realMount.allowDestructive=true
// Each mutating test class self-gates via
// `dev.openpolaris.probe.ProbeSmokeTest.requireRealMountPolicy`; if the
// relevant flag is not set, JUnit reports the test as `skipped` (not
// failed) and the suite continues.
tasks.register("smokeReal") {
    group = "verification"
    description = "Run read-only smoke tests against a real Polaris (requires -Popenpolaris.realMount=true)"
    val enabled = (project.findProperty("openpolaris.realMount") as String?)?.toBoolean() == true
    val realHost = (project.findProperty("openpolaris.realMount.host") as String?) ?: "192.168.0.1"
    val realPort = (project.findProperty("openpolaris.realMount.port") as String?) ?: "9090"
    if (enabled) {
        dependsOn(":tools:cli-probe:test")
        doFirst {
            logger.lifecycle("smokeReal: running with real mount at $realHost:$realPort (read-only subset)")
        }
    } else {
        doFirst {
            logger.lifecycle("smokeReal: skipped (set -Popenpolaris.realMount=true to enable)")
        }
    }
}

tasks.register("smokeRealFull") {
    group = "verification"
    description = "Run full smoke tests (motion + destructive) against a real Polaris (requires -Popenpolaris.realMount=true plus allowMotion and allowDestructive)"
    val enabled = (project.findProperty("openpolaris.realMount") as String?)?.toBoolean() == true
    val allowMotion = (project.findProperty("openpolaris.realMount.allowMotion") as String?)?.toBoolean() == true
    val allowDestructive = (project.findProperty("openpolaris.realMount.allowDestructive") as String?)?.toBoolean() == true
    val realHost = (project.findProperty("openpolaris.realMount.host") as String?) ?: "192.168.0.1"
    val realPort = (project.findProperty("openpolaris.realMount.port") as String?) ?: "9090"
    when {
        !enabled -> doFirst {
            logger.lifecycle("smokeRealFull: skipped (set -Popenpolaris.realMount=true to enable)")
        }
        !allowMotion || !allowDestructive -> doFirst {
            logger.lifecycle(
                "smokeRealFull: skipped (set -Popenpolaris.realMount.allowMotion=true and " +
                    "-Popenpolaris.realMount.allowDestructive=true to enable destructive tests)",
            )
        }
        else -> {
            dependsOn(":tools:cli-probe:test")
            doFirst {
                logger.lifecycle("smokeRealFull: running with real mount at $realHost:$realPort (motion + destructive)")
            }
        }
    }
}
