@echo off
title Empaquetar Discord Custom Status (Standalone)
cd /d "%~dp0"

echo ============================================
echo  Empaquetando Custom Status Standalone
echo ============================================
echo.

:: 1. Compilar si no existe DiscordCustomRPC.jar
if not exist "DiscordCustomRPC.jar" (
    echo Compilando JARs primero...
    call build.bat
)

:: 2. Crear carpeta temporal para el paquete
set DIST_DIR=dist-custom
if exist "%DIST_DIR%" rmdir /s /q "%DIST_DIR%"
mkdir "%DIST_DIR%"
mkdir "%DIST_DIR%\custom-status"

echo [1/4] Copiando binarios y configuracion...
copy /y "DiscordCustomRPC.jar" "%DIST_DIR%\" >nul
copy /y "config.json" "%DIST_DIR%\" >nul
if exist "bridge-state.json" copy /y "bridge-state.json" "%DIST_DIR%\" >nul
copy /y "INICIO-RAPIDO-CUSTOM.md" "%DIST_DIR%\INICIO-RAPIDO.md" >nul

echo [2/4] Creando script de logs...
echo java -jar DiscordCustomRPC.jar > "%DIST_DIR%\logs.bat"

echo [3/4] Copiando interfaz web custom-status...
xcopy /e /i /y "custom-status" "%DIST_DIR%\custom-status" >nul

echo [4/4] Comprimiendo Custom-Status-Release.zip...
if exist "Custom-Status-Release.zip" del /f /q "Custom-Status-Release.zip"
powershell -Command "Compress-Archive -Path '%DIST_DIR%\*' -DestinationPath 'Custom-Status-Release.zip' -Force"

:: Limpieza
rmdir /s /q "%DIST_DIR%"

echo.
echo ============================================
echo  Paquete creado exitosamente:
echo  Custom-Status-Release.zip
echo ============================================
echo.
pause
