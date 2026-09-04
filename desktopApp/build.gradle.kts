import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(project(":composeApp"))
            implementation(project(":shared"))
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.material3.window.size)
            // Skiko AWT native runtime, resolved per host OS (linux-x64 on the
            // dev box, windows-x64 when building on Windows). Replaces a
            // hardcoded linux-x64 pin that would leak into Windows builds.
            implementation(compose.desktop.currentOs)
        }
        commonMain.dependencies {
            implementation(compose.runtime)
        }
    }
}

val appPackageName = "OpenPolaris"
val appPackageVersion = "1.0.0"

compose.desktop {
    application {
        mainClass = "dev.openpolaris.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = appPackageName
            packageVersion = appPackageVersion
        }
    }
}

// ---------------------------------------------------------------------------
// Inno Setup single-file EXE installer (Windows).
//
// Wraps the jpackage app image produced by `createDistributable` in an
// Inno Setup script and compiles it with ISCC. The task runs on Windows
// hosts and is skipped elsewhere. Requires Inno Setup 6+ installed, or set
// INNO_SETUP_HOME to a directory containing ISCC.exe.
// ---------------------------------------------------------------------------

val innoScriptFile = layout.buildDirectory.file("inno/$appPackageName.iss")

tasks.register("generateInnoScript") {
    val script = innoScriptFile.get().asFile
    inputs.properties(mapOf("packageName" to appPackageName, "packageVersion" to appPackageVersion))
    outputs.file(script)
    doLast {
        script.parentFile?.mkdirs()
        // Paths are relative to the .iss location (build/inno/).
        script.writeText(
            """
#define MyAppName "$appPackageName"
#define MyAppVersion "$appPackageVersion"
#define MyAppPublisher "OpenPolaris"
#define MyAppExeName "$appPackageName.exe"

[Setup]
AppId={{8C4E7A2B-6D3F-4E5A-9B1C-D2E3F4A5B6C7}}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputBaseFilename={#MyAppName}-Setup-{#MyAppVersion}
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "..\compose\distributions\{#MyAppName}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch {#MyAppName}"; Flags: nowait postinstall skipifsilent
"""
                .trimIndent()
        )
    }
}

tasks.register("createExeInstaller") {
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    dependsOn("createDistributable", "generateInnoScript")
    onlyIf {
        if (!isWindows) logger.lifecycle(":desktopApp:createExeInstaller skipped (not a Windows host)")
        isWindows
    }
    doLast {
        val script = innoScriptFile.get().asFile
        val outDir = layout.buildDirectory.dir("inno/output").get().asFile
        // Locate ISCC.exe: INNO_SETUP_HOME env var, then PATH, then standard install locations.
        val iscc = sequenceOf(
            System.getenv("INNO_SETUP_HOME")?.let { File(it, "ISCC.exe") },
            System.getenv("PATH")?.split(File.pathSeparator)
                ?.map { File(it, "ISCC.exe") }
                ?.firstOrNull { it.exists() },
            File("C:/Program Files (x86)/Inno Setup 7/ISCC.exe"),
            File("C:/Program Files (x86)/Inno Setup 6/ISCC.exe"),
        ).filterNotNull().firstOrNull { it.exists() }
            ?: throw GradleException(
                "ISCC.exe not found. Install Inno Setup 6+ from https://jrsoftware.org/isinfo.php " +
                    "or set INNO_SETUP_HOME to a directory containing ISCC.exe."
            )
        val result = project.exec { commandLine(iscc.absolutePath, "/O" + outDir.absolutePath, script.absolutePath) }
        if (result.exitValue != 0) throw GradleException("ISCC failed with exit code ${result.exitValue}")
        logger.lifecycle("Installer written to " + File(outDir, "$appPackageName-Setup-$appPackageVersion.exe"))
    }
}
