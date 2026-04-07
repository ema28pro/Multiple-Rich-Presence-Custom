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
    src\br\com\brforgers\armelin\dps\SourceManager.java ^
    src\br\com\brforgers\armelin\dps\RobloxMonitor.java ^
    src\br\com\brforgers\armelin\dps\DiscordPipeSocket.java ^
    src\br\com\brforgers\armelin\dps\LoggerConfig.java

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Compilation failed.
    pause
    exit /b 1
)

echo [2/3] Updating JAR with new classes...
:: Copy original JAR as backup if not already done
if not exist "DiscordPipeSocket-original.jar" (
    copy "DiscordPipeSocket.jar" "DiscordPipeSocket-original.jar" >nul
    echo     Created backup: DiscordPipeSocket-original.jar
)

:: Update the JAR in-place with compiled classes
jar uf "DiscordPipeSocket.jar" -C build br/com/brforgers/armelin/dps/

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to update JAR.
    pause
    exit /b 1
)

echo [3/3] Verifying...
jar tf "DiscordPipeSocket.jar" | findstr /i "Config.class SourceManager"
echo.
echo ============================================
echo  Build successful!
echo  JAR updated: DiscordPipeSocket.jar
echo  Backup:      DiscordPipeSocket-original.jar
echo ============================================
echo.
echo Make sure config.json is next to the JAR with your Discord Client ID.
pause
