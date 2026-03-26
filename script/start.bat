@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1

echo ==========================================
echo  Streaming Download Service - Quick Start
echo ==========================================
echo.

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202"
set "PATH=%JAVA_HOME%\bin;%PATH%"

set "MAVEN_HOME=C:\tools\apache-maven-3.9.9"
set "PATH=%MAVEN_HOME%\bin;%PATH%"

echo [1/4] Checking Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found
    pause
    exit /b 1
)
echo       Java OK

echo [2/4] Checking Maven...
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo [ERROR] Maven not found, run quick-start.bat first
    pause
    exit /b 1
)
echo       Maven OK

echo [3/4] Building project...
call mvn clean install -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)
echo       Build OK

echo [4/4] Checking port 8080...
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    set "PID=%%a"
)
if defined PID (
    echo       Port 8080 is in use by PID !PID!, killing...
    taskkill /F /PID !PID! >nul 2>&1
    timeout /t 1 >nul
    echo       Port released
) else (
    echo       Port OK
)

echo.
echo ==========================================
echo  Service starting...
echo  Test page: http://localhost:8080/download.html
echo  API: http://localhost:8080/api/proxy/download
echo  Press Ctrl+C to stop
echo ==========================================
echo.

cd streaming-download-demo
call mvn spring-boot:run

pause
