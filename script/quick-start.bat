@echo off
chcp 65001 >nul 2>&1

echo ==========================================
echo  Streaming Download - Quick Start
echo ==========================================
echo.

set "PROJECT_ROOT=%~dp0.."
cd /d "%PROJECT_ROOT%"

set "JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202"
set "MAVEN_HOME=C:\tools\apache-maven-3.9.9"
set "PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%"

echo [1/4] Checking Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java not found at: %JAVA_HOME%
    pause
    exit /b 1
)
echo       Java OK

echo [2/4] Checking Maven...
if exist "%MAVEN_HOME%\bin\mvn.cmd" (
    set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
    echo       Maven OK
) else (
    echo [ERROR] Maven not found at: %MAVEN_HOME%
    echo       Please run setup-maven.bat first
    pause
    exit /b 1
)

echo.
echo [3/4] Building project...
call %MVN_CMD% clean install -DskipTests -q
if errorlevel 1 (
    echo [ERROR] Build failed
    pause
    exit /b 1
)
echo       Build OK

echo.
echo [4/4] Checking port 8080...
netstat -ano | findstr ":8080 " | findstr "LISTENING" >nul
if not errorlevel 1 (
    echo [ERROR] Port 8080 is in use
    pause
    exit /b 1
)
echo       Port OK

echo.
echo Starting service...
echo.
echo ==========================================
echo  Test page: http://localhost:8080/download.html
echo  API: http://localhost:8080/api/proxy/download
echo ==========================================
echo.

cd streaming-download-demo
call %MVN_CMD% spring-boot:run

pause
