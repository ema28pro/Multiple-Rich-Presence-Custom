@echo off

echo [1/51] Cambiando a main...
git checkout main

echo [2/15] Pulling main...
git pull

echo [3/15] Cambiando a release...
git checkout release

echo [4/15] Reset hard...
git reset --hard HEAD

echo [5/15] Copiando DiscordPipeSocket.jar...
git checkout main -- DiscordPipeSocket.jar

echo [6/15] Copiando README.md...
git checkout main -- README.md

echo [7/15] Copiando README-EN.md...
git checkout main -- README-EN.md

echo [8/15] Copiando bridge-state.json...
git checkout main -- bridge-state.json

echo [9/15] Copiando config.json...
git checkout main -- config.json

echo [10/15] Copiando custom-status/...
git checkout main -- custom-status/

echo [11/15] Copiando userscripts/...
git checkout main -- userscripts/

echo [12/15] Copiando img/...
git checkout main -- img/

echo [13/15] Chequeando diff...
git diff --cached --quiet
if %errorlevel% neq 0 (
    git commit -m "Actualizados archivos principales desde main"
    echo Cambios commiteados.
) else (
    echo Sin cambios nuevos.
)

echo [14/15] Pusheando...
git push --force-with-lease origin release

echo [15/15] Volviendo a main...
git checkout main

echo Proceso completado.
pause