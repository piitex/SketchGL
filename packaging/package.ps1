<#
.SYNOPSIS
    Builds SketchGL as a Windows x64 app with its own Java runtime.

.DESCRIPTION
    packaging\package.ps1                  SketchGL-<version>.msi in dist\   (needs the WiX Toolset)
    packaging\package.ps1 -Type exe        SketchGL-<version>.exe installer  (needs the WiX Toolset)
    packaging\package.ps1 -Type app-image  dist\SketchGL\ with SketchGL.exe, nothing to install (needs nothing extra)

    Run it from anywhere; it works from the project folder. If PowerShell refuses to run scripts:
        powershell -ExecutionPolicy Bypass -File packaging\package.ps1

    It has to run on Windows x64 with JDK 25 (jpackage builds for the system it runs on). The RenGL engine comes from
    Maven Central. Output goes to dist\.

.PARAMETER PerUser
    Install for the current user only, so installing needs no administrator rights.
#>
param(
    [ValidateSet('msi', 'exe', 'app-image')]
    [string]$Type = 'msi',
    [string]$Version = $(if ($env:SKETCHGL_VERSION) { $env:SKETCHGL_VERSION } else { '1.0.0' }),  # jpackage wants x.y.z, not 1.0-SNAPSHOT
    [switch]$PerUser
)

$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')

$Jar = 'SketchGL-1.0-SNAPSHOT.jar'
# Keeps every build an upgrade of the one before it: Windows Installer replaces the old SketchGL instead of installing a second.
# Don't change it once people have installed a build.
$UpgradeUuid = '1c8a39f4-78e2-4935-bf6b-bf3222a238d7'

function Fail($message) {
    Write-Error $message
    exit 1
}

function Check($what) {
    if ($LASTEXITCODE -ne 0) { Fail "$what failed (exit code $LASTEXITCODE)" }
}

if ([Environment]::OSVersion.Platform -ne 'Win32NT') { Fail 'This script builds the Windows app. Run it on Windows.' }
if (-not [Environment]::Is64BitOperatingSystem -or $env:PROCESSOR_ARCHITECTURE -eq 'ARM64') {
    Fail 'This builds the Windows x64 app. Run it on a 64-bit Intel/AMD system (or an x64 JDK on ARM).'
}
foreach ($tool in 'java', 'jdeps', 'jpackage') {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { Fail "$tool not found. Install JDK 25 and put its bin folder on PATH (or set JAVA_HOME)." }
}
if ($Type -ne 'app-image' -and -not (Get-Command wix.exe, candle.exe -ErrorAction SilentlyContinue)) {
    Fail "-Type $Type needs the WiX Toolset (https://wixtoolset.org/, or: dotnet tool install --global wix). Use -Type app-image to skip it."
}

Write-Host '==> Building'
# Keeps Maven's own JVM from printing its native-access and Unsafe warnings over the build's output.
$env:MAVEN_OPTS = '--enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow'
# junit comes in through RenGL's dependencies and has no business in an app.
& .\mvnw.cmd -q -DskipTests clean package dependency:copy-dependencies '-DincludeScope=runtime' '-DexcludeArtifactIds=junit' '-DoutputDirectory=target/input'
Check 'The Maven build'
Copy-Item "target\$Jar" target\input\

# Without this jpackage puts nearly the whole JDK in the app. jdeps finds what the jars need; logging and prefs are used
# through reflection or by the libraries' own logging, so they are named too.
$jars = (Get-ChildItem target\input\*.jar).FullName
$modules = (& jdeps --multi-release 25 --ignore-missing-deps --print-module-deps $jars) + ',java.logging,java.prefs'
Check 'jdeps'

$jpackageArgs = @(
    '--type', $Type,
    '--name', 'SketchGL', '--app-version', $Version, '--vendor', 'piitex',
    '--description', 'A drawing app',
    '--dest', 'dist',
    '--input', 'target\input', '--main-jar', $Jar, '--main-class', 'me.piitex.sketchgl.SketchApp',
    '--icon', 'packaging\icons\SketchGL.ico',
    '--add-modules', $modules,
    # No -XstartOnFirstThread: that is a macOS flag and a Windows JVM refuses to start with it.
    '--java-options', '--enable-native-access=ALL-UNNAMED',
    '--java-options', '--sun-misc-unsafe-memory-access=deny'
)
if ($Type -ne 'app-image') {
    $jpackageArgs += '--win-menu', '--win-menu-group', 'SketchGL', '--win-shortcut', '--win-dir-chooser', '--win-upgrade-uuid', $UpgradeUuid
    if ($PerUser) { $jpackageArgs += '--win-per-user-install' }
}

# Only replace our own earlier output; dist\ may hold the macOS and Linux builds too.
New-Item -ItemType Directory -Force dist | Out-Null
Get-ChildItem dist -ErrorAction SilentlyContinue | Where-Object { $_.Name -in "SketchGL-$Version.msi", "SketchGL-$Version.exe", 'SketchGL' } |
    Remove-Item -Recurse -Force

Write-Host "==> jpackage (Windows x64, $Type)"
& jpackage @jpackageArgs
Check 'jpackage'

Write-Host '==> Done'
Get-ChildItem dist | Select-Object Name, @{ n = 'MB'; e = { if ($_.PSIsContainer) { '' } else { [math]::Round($_.Length / 1MB, 1) } } } | Format-Table -AutoSize
