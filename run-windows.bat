@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java 17 or later is required. Install a JDK and try again.
  pause
  exit /b 1
)
if exist "%~dp0weeklyMergeMD.jar" (
  java -jar "%~dp0weeklyMergeMD.jar"
) else (
  java -jar "%~dp0target\weeklyMergeMD.jar"
)
if errorlevel 1 pause
