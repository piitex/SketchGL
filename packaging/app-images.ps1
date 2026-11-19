<#
.SYNOPSIS
    Builds SketchGL app-images for Windows, Linux and macOS with the RenGL packager (no jpackage), from any one of them.

.DESCRIPTION
    packaging\app-images.ps1                         Every platform, into dist\app-images\
    packaging\app-images.ps1 -Platform windows-x64   Only these (comma separated):
                                                     windows-x64, linux-x64, linux-arm64, macos-x64, macos-arm64

    Each platform gets a folder (dist\app-images\<platform>\SketchGL\, or SketchGL.app on macOS) and an archive next to it
    (.zip for Windows, .tar.gz for Linux and macOS). The app inside runs without installing anything or having Java.
    -Installer writes installers instead of the archives: a setup.exe for Windows (choose the folder, desktop and Start
    menu shortcuts; it installs for the current user and has an uninstaller) and a .deb for Linux, which adds a menu entry.
    macOS has no installer here and keeps its .tar.gz. For an .msi use packaging\package.ps1.

    Run it from anywhere; it works from the project folder. If PowerShell refuses to run scripts:
        powershell -ExecutionPolicy Bypass -File packaging\app-images.ps1

    It needs JDK 25 and, the first time, the internet (the other platforms' Java modules are downloaded once and kept in
    ~\.rengl\jdks). The RenGL engine, its packager and its natives for every platform come from Maven Central; the
    first run downloads them into the local Maven repository.

.PARAMETER Installer
    Write a setup.exe (Windows) and a .deb (Linux) instead of the .zip and .tar.gz. A silent install of the setup:
        Name-x.y.z-windows-x64-setup.exe /S [/DIR=C:\Folder] [/DESKTOP=0] [/STARTMENU=0]

.PARAMETER NoArchive
    Leave the folders; do not write the .zip and .tar.gz files.
#>
param(
    [string]$Platform = 'all',
    [string]$Version = $(if ($env:SKETCHGL_VERSION) { $env:SKETCHGL_VERSION } else { '1.0.0' }),
    [switch]$Installer,
    [switch]$NoArchive
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$Jar = 'SketchGL-1.0-SNAPSHOT.jar'
$RenglVersion = '0.1.1'  # the engine's version, as in pom.xml
$RenglGroup = 'io.github.piitex'
$Repository = Join-Path $env:USERPROFILE '.m2\repository\io\github\piitex'
$Packager = if ($env:RENGL_PACKAGER) { $env:RENGL_PACKAGER } else { "$Repository\rengl-packager\$RenglVersion\rengl-packager-$RenglVersion.jar" }
# The engine's natives artifact for each platform: the Skiko runtime, with LWJGL's native libraries as its dependencies.
$Natives = [ordered]@{
    'windows-x64' = 'rengl-natives-windows'
    'linux-x64'   = 'rengl-natives-linux'
    'linux-arm64' = 'rengl-natives-linux-arm64'
    'macos-x64'   = 'rengl-natives-macos'
    'macos-arm64' = 'rengl-natives-macos-arm64'
}

function Fail($message) {
    Write-Error $message
    exit 1
}

function Check($what) {
    if ($LASTEXITCODE -ne 0) { Fail "$what failed (exit code $LASTEXITCODE)" }
}

foreach ($tool in 'java', 'jdeps', 'jlink') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { Fail "$tool not found. Install JDK 25 and put its bin folder on PATH (or set JAVA_HOME)." }
}
$platforms = if ($Platform -eq 'all') { @($Natives.Keys) } else { $Platform -split ',' | ForEach-Object { $_.Trim() } }
foreach ($p in $platforms) {
    if (-not $Natives.Contains($p)) { Fail "Unknown platform '$p'. Use: $($Natives.Keys -join ', '), or all." }
}

# The packager comes from Maven Central, unless RENGL_PACKAGER points at one.
if (-not (Test-Path $Packager)) {
    Write-Host '==> Fetching the RenGL packager'
    $env:MAVEN_OPTS = '--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow'
    & .\mvnw.cmd -q "dependency:get" "-Dartifact=${RenglGroup}:rengl-packager:$RenglVersion"
    Check 'Fetching the RenGL packager'
    if (-not (Test-Path $Packager)) { Fail "The RenGL packager is not at $Packager." }
}

Write-Host '==> Building'
# Keeps Maven's own JVM from printing its native-access and Unsafe warnings over the build's output.
$env:MAVEN_OPTS = '--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow'
# junit comes in through RenGL's dependencies and has no business in an app.
& .\mvnw.cmd -q -DskipTests clean package dependency:copy-dependencies '-DincludeScope=runtime' '-DexcludeArtifactIds=junit' '-DoutputDirectory=target/input'
Check 'The Maven build'
Copy-Item "target\$Jar" target\input\

# The build brings the natives of the computer it runs on. The packager picks natives by file name and gives each image
# only its own platform's, so the other platforms' jars (and LWJGL's natives they depend on) just have to be in the
# folder. They are fetched from Maven Central with a throwaway pom that depends on each platform's natives artifact.
$missing = @($platforms | Where-Object { -not (Test-Path "target\input\$($Natives[$_])-$RenglVersion.jar") })
if ($missing.Count -gt 0) {
    $scratch = 'target\natives'
    New-Item -ItemType Directory -Force $scratch | Out-Null
    $dependencies = ($missing | ForEach-Object {
        "        <dependency><groupId>$RenglGroup</groupId><artifactId>$($Natives[$_])</artifactId><version>$RenglVersion</version></dependency>"
    }) -join "`n"
    @"
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>me.piitex</groupId>
    <artifactId>sketchgl-natives</artifactId>
    <version>1</version>
    <dependencies>
$dependencies
    </dependencies>
</project>
"@ | Set-Content -Encoding utf8 "$scratch\pom.xml"
    & .\mvnw.cmd -q -f "$scratch\pom.xml" dependency:copy-dependencies '-DincludeScope=runtime' '-DoutputDirectory=..\input'
    Check "Fetching the natives for $($missing -join ', ')"
}

$packagerArgs = @(
    '--name', 'SketchGL', '--app-version', $Version, '--vendor', 'piitex',
    '--description', 'A drawing app',
    '--identifier', 'me.piitex.sketchgl',
    '--input', 'target\input', '--main-jar', $Jar, '--main-class', 'me.piitex.sketchgl.SketchApp',
    '--icon', 'packaging\icons\SketchGL.ico',
    '--icon', 'packaging\icons\SketchGL.png',
    '--icon', 'icns=packaging\icons\SketchGL-macos-1024.png',
    # logging and prefs are used through reflection or by the libraries' own logging, so jdeps does not see them.
    '--add-modules', 'java.logging,java.prefs',
    # -XstartOnFirstThread is added for macOS by the packager, where it belongs.
    '--java-option', '--enable-native-access=ALL-UNNAMED',
    '--java-option', '--sun-misc-unsafe-memory-access=deny',
    '--platform', ($platforms -join ','),
    '--output', 'dist\app-images'
)
if ($Installer) { $packagerArgs += '--installer' }
if ($NoArchive) { $packagerArgs += '--no-archive' }

Write-Host "==> RenGL packager ($($platforms -join ', '))"
& java --enable-native-access=ALL-UNNAMED -jar $Packager @packagerArgs
Check 'The RenGL packager'
