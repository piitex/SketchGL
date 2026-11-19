@echo off
rem Builds this project's app with the RenGL packager. The settings are in rengl-package.properties.
rem The packager is found in this order:
rem   1. the RENGL_HOME environment variable (the folder of your RenGL checkout)
rem   2. a RenGL checkout next to this project: ..\RenGL
rem Parameters pass through: package.cmd -Installer true   or   package.cmd -Platform linux-x64
set "RENGL_ROOT=%RENGL_HOME%"
if "%RENGL_ROOT%"=="" set "RENGL_ROOT=%~dp0..\RenGL"
if not exist "%RENGL_ROOT%\rengl-packager\package-app.ps1" (
    echo Could not find the RenGL packager at "%RENGL_ROOT%\rengl-packager".
    echo Set RENGL_HOME to the folder of your RenGL checkout, or clone RenGL next to this project.
    exit /b 1
)
powershell -NoProfile -ExecutionPolicy Bypass -File "%RENGL_ROOT%\rengl-packager\package-app.ps1" -Project "%~dp0." %*
exit /b %errorlevel%
