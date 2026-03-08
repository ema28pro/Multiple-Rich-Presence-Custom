@echo off

echo [1] Cambiando a main...
git checkout main

echo [2] Pulling main...
git pull

echo [3] Cambiando a release...
git checkout release

echo [4] Reset hard...
git reset --hard

echo [5] Copiando README.md...
git checkout main -- README.md

echo [6] Copiando README-EN.md...
git checkout main -- README-EN.md

echo [7] Copiando bridge-state.json...
git checkout main -- bridge-state.json

echo [8] Copiando DiscordPipeSocket.jar...
git checkout main -- DiscordPipeSocket.jar

echo [9] Copiando config.json...
git checkout main -- config.json

echo [10] Copiando custom-status/...
git checkout main -- custom-status/

echo [11] Copiando userscripts/...
git checkout main -- userscripts/

echo [12] Copiando img/...
git checkout main -- img/

echo [13] Chequeando diff...
git diff --cached --quiet
if %errorlevel% neq 0 (
    git commit -m "Actualizados archivos principales desde main"
    echo Cambios commiteados.
) else (
    echo Sin cambios nuevos.
)

echo [14] Pusheando...
git push --force-with-lease origin release

echo [15] Volviendo a main...
git checkout main

echo Proceso completado.
pause