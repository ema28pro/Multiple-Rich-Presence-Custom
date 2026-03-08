@echo off
REM Script para actualizar la rama release con archivos principales desde main

REM Cambiar a main y actualizar
call git checkout main
call git pull

REM Cambiar a release y resetear
call git checkout release
call git reset --hard

REM Traer archivos principales desde main
call git checkout main -- README.md README-EN.md bridge-state.json DiscordPipeSocket.jar config.json custom-status/ userscripts/ img/

REM Hacer commit solo si hay cambios
git diff --cached --quiet
if %errorlevel% neq 0 (
    call git commit -m "Actualizados archivos principales desde main"
    echo Cambios commiteados.
)

REM Pushear forzado con seguridad
call git push --force-with-lease origin release
if %errorlevel% neq 0 (
    echo Error: el push falló. Verifica el estado de la rama remota.
)

REM Volver a main
call git checkout main

echo Proceso completado.
pause