@echo off

echo [1] Cambiando a main...
git checkout main

echo [2] Pulling...
git pull

echo [3] Cambiando a release y reseteando...
git checkout release & git reset --hard HEAD

echo [4] Copiando archivos...
git checkout main -- DiscordPipeSocket.jar
git checkout main -- README.md
git checkout main -- README-EN.md
git checkout main -- bridge-state.json
git checkout main -- config.json
git checkout main -- custom-status/
git checkout main -- userscripts/
git checkout main -- img/

echo [5] Commiteando...
git diff --cached --quiet
if %errorlevel% neq 0 (
    git commit -m "Actualizados archivos principales desde main"
    echo Cambios commiteados.
) else (
    echo Sin cambios nuevos.
)

echo [6] Pusheando...
git push --force-with-lease origin release

echo [7] Volviendo a main...
git checkout main

echo Completado.
pause