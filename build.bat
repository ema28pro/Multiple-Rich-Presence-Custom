@echo off
title Build Discord RPC Bridge
cd /d "%~dp0"

echo ============================================
echo  Building Discord RPC Bridge (Multi-Source)
echo ============================================
echo.

:: Check for javac
where javac >nul 2>nul
if %errorlevel% neq 0 (
    echo ERROR: javac not found. Install JDK 8+ and add it to PATH.
    echo Download: https://adoptium.net/
    pause
    exit /b 1
)

:: Clean build directory
if exist build rmdir /s /q build
mkdir build

echo [1/3] Compiling Java sources...
javac -cp "DiscordPipeSocket.jar" -d "build" --release 8 ^
    src\br\com\brforgers\armelin\dps\Config.java ^
    src\br\com\brforgers\armelin\dps\DiscordIPC.java ^
    src\br\com\brforgers\armelin\dps\SourceManager.java ^
    src\br\com\brforgers\armelin\dps\RobloxMonitor.java ^
    src\br\com\brforgers\armelin\dps\DiscordPipeSocket.java ^
    src\br\com\brforgers\armelin\dps\CustomPipeSocket.java ^
    src\br\com\brforgers\armelin\dps\LoggerConfig.java

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Compilation failed.
    pause
    exit /b 1
)

echo [2/3] Updating JARs with new classes...
:: Copy original JAR as backup if not already done
if not exist "DiscordPipeSocket-original.jar" (
    copy "DiscordPipeSocket.jar" "DiscordPipeSocket-original.jar" >nul
    echo     Created backup: DiscordPipeSocket-original.jar
)

:: Update multi-source JAR in-place
jar uf "DiscordPipeSocket.jar" -C build br/com/brforgers/armelin/dps/
if %errorlevel% neq 0 (
    echo ERROR: Failed to update DiscordPipeSocket.jar.
    pause
    exit /b 1
)

:: Build/update custom-only JAR with CustomPipeSocket entrypoint and embedded UI
copy /y "DiscordPipeSocket.jar" "DiscordCustomRPC.jar" >nul
jar ufe "DiscordCustomRPC.jar" br.com.brforgers.armelin.dps.CustomPipeSocket -C build br/com/brforgers/armelin/dps/
jar uf "DiscordCustomRPC.jar" custom-status/index.html custom-status/app.js custom-status/style.css
if %errorlevel% neq 0 (
    echo ERROR: Failed to create DiscordCustomRPC.jar.
    pause
    exit /b 1
)

echo [3/3] Verifying...
jar tf "DiscordPipeSocket.jar" | findstr /i "Config.class SourceManager DiscordIPC LoggerConfig CustomPipeSocket"
echo.
echo ============================================
echo  Build successful!
echo  Multi-Source JAR:  DiscordPipeSocket.jar
echo  Custom-Only JAR:   DiscordCustomRPC.jar
echo  Original Backup:   DiscordPipeSocket-original.jar
echo ============================================
echo.
echo Make sure config.json is next to the JAR with your Discord Client ID.
pause
