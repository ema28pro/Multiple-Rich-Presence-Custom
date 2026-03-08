@echo off
REM Script para actualizar la rama release con archivos principales desde main

REM Cambiar a main y actualizar
call git checkout main
call git pull

REM Cambiar a release
call git checkout release

REM Traer archivos principales desde main
call git checkout main -- README.md README-EN.md bridge-state.json DiscordPipeSocket.jar config.json custom-status/ userscripts/ img/

REM Hacer commit solo si hay cambios
git diff --cached --quiet
if %errorlevel% neq 0 (
    call git commit -m "Actualizados archivos principales desde main"
    echo Cambios commiteados.
)

REM Siempre pushear (puede haber commits locales pendientes)
call git push origin release

REM Volver a main
call git checkout main

echo Proceso completado.
pause