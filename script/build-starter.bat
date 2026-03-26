@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ========================================
echo  Streaming Download Starter 构建脚本
echo ========================================
echo.

REM 获取脚本所在目录的父目录
set "SCRIPT_DIR=%~dp0"
set "PROJECT_ROOT=%SCRIPT_DIR%.."
cd /d "%PROJECT_ROOT%"

echo [1/4] 清理旧构建...
if exist "dist" rd /s /q "dist"

echo [2/4] 构建 core 和 starter 模块...
call mvn clean install -pl streaming-download-core,streaming-download-spring-boot-starter -DskipTests -q
if errorlevel 1 (
    echo.
    echo [错误] Maven 构建失败！
    pause
    exit /b 1
)

echo [3/4] 创建 dist 目录并复制 JAR...
mkdir "dist"

REM 复制 core JAR
for %%f in (streaming-download-core\target\*.jar) do (
    if not "%%~nxf"=="*-sources.jar" (
        if not "%%~nxf"=="*-javadoc.jar" (
            copy /y "%%f" "dist\" >nul
            echo   - %%~nxf
        )
    )
)

REM 复制 starter JAR
for %%f in (streaming-download-spring-boot-starter\target\*.jar) do (
    if not "%%~nxf"=="*-sources.jar" (
        if not "%%~nxf"=="*-javadoc.jar" (
            copy /y "%%f" "dist\" >nul
            echo   - %%~nxf
        )
    )
)

echo.
echo [4/4] 构建完成！
echo.
echo ========================================
echo  输出目录: %CD%\dist
echo  本地仓库: 已安装
echo ========================================
echo.
echo JAR 文件列表:
dir /b "dist\*.jar"
echo.
echo 使用方法:
echo  1. 将 dist 目录中的 JAR 文件复制到目标项目
echo  2. 或直接在 pom.xml 中引用（已安装到本地仓库）
echo.
pause
