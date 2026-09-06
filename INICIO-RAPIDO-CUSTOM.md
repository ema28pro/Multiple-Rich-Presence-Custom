# Discord Custom Status (Edicion Independiente)

Version ligera dedicada exclusivamente a estados personalizados de Discord Rich Presence, sin necesidad de Tampermonkey, scripts de navegador ni monitores de juegos.

---

## Requisitos

1. **Discord abierto** (aplicacion de escritorio para Windows/Linux/Mac).
2. **Java 8 o superior** instalado ([Descargar Adoptium/OpenJDK](https://adoptium.net/)).

---

## Inicio Rapido (2 Pasos)

### Paso 1: Ejecutar el Bridge
Haz doble clic sobre `DiscordCustomRPC.jar`.
- El programa se ejecutara silenciosamente en segundo plano.
- Aparecera un icono en la bandeja del sistema (junto al reloj de Windows).

### Paso 2: Abrir el Panel de Control
Haz clic derecho en el icono de la bandeja y selecciona **"Abrir Custom Status"** (o simplemente abre `custom-status/index.html` en cualquier navegador web).

1. Ingresa o selecciona tu **Application ID** de Discord Developer Portal (el archivo `config.json` ya incluye uno predeterminado).
2. Personaliza los campos:
   - **Detalles y Estado**: Texto principal y secundario.
   - **Imagenes**: Imagen grande y pequena (claves de assets o URLs HTTPS).
   - **Tiempos**: Contador transcurrido, tiempo restante o Timeline de musica (barra de progreso).
   - **Botones Interactivos**: Hasta 2 botones con enlaces web externos.
   - **Multijugador / Secrets**: Para invitaciones o grupos de juego.
3. Haz clic en el boton **"Activar"**.
4. Tu estado aparecera de inmediato en tu perfil de Discord.

---

## Archivos del Paquete

- `DiscordCustomRPC.jar`: Ejecutable Java ligero que comunica el panel web con Discord IPC.
- `config.json`: Configuracion de Client ID y puerto WebSocket (default: 6680).
- `custom-status/`: Carpeta con la aplicacion web (HTML, CSS, JS).
- `logs.bat`: Script para iniciar el bridge mostrando la consola en vivo si necesitas depurar.

---

## Preguntas Frecuentes

### ¿Como cerrar la aplicacion?
Haz clic derecho sobre el icono en la bandeja del sistema y pulsa **"Salir"**.

### ¿Puedo hacer que inicie con Windows?
Crea un acceso directo a `DiscordCustomRPC.jar` y colocalo en:
`%appdata%\Microsoft\Windows\Start Menu\Programs\Startup`
*(Asegurate de mantener el archivo jar en su carpeta junto a config.json).*

### ¿Por que no se actualiza el estado en Discord?
- Verifica que la aplicacion de escritorio de Discord este abierta.
- Revisa en Ajustes de Discord > Privacidad de la actividad > "Mostrar la actividad actual como mensaje de estado" este activado.
- Si usas botones interactivos, recuerda que Discord no muestra los botones a ti mismo en tu propio perfil, pero tus amigos si podran verlos y hacer clic en ellos.
