# Discord RPC Multi-Activity Bridge



<div align="center">
  <br>
  <img alt="Java" src="https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=black" class="inline-block mx-1" style="margin: 0px 2px;">
  <img alt="JavaScript" src="https://img.shields.io/badge/JavaScript-F7DF1E?logo=javascript&logoColor=black" class="inline-block mx-1" style="margin: 0px 2px;">
  <img alt="Tampermonkey" src="https://img.shields.io/badge/Tampermonkey-black?logo=Tampermonkey" class="inline-block mx-1" style="margin: 0px 2px;">
  <img alt="Visitors" src="https://visitor-badge.laobi.icu/badge?page_id=ema28pro/Multiple-Rich-Presence-Custom" class="inline-block mx-1" style="margin: 0px 2px;">
  <img alt="Github Stars" src="https://img.shields.io/github/stars/ema28pro/Multiple-Rich-Presence-Custom" class="inline-block mx-1" style="margin: 0px 2px;">


  <a href="INICIO-RAPIDO.md"><img alt="Inicio Rapido" src="https://img.shields.io/badge/Inicio Rapido-Click me-blue" class="inline-block mx-1" style="margin: 0px 2px;"></a>
  <img alt="Status Paused" src="https://img.shields.io/badge/Status-Paused-white" class="inline-block mx-1" style="margin: 0px 2px;">
</div>


Discord Rich Presence para múltiples actividades: Tetr.io, YouTube, Anime, Roblox y estados personalizados.

Basado en [TETRIO-browser-rpc](https://github.com/PATXS/TETRIO-browser-rpc) de PATXS.

**[English version](README-EN.md)**

## Qué hace

Muestra en tu perfil de Discord lo que estás haciendo — jugando Tetr.io, viendo youtube, viendo anime, jugando Roblox, o un estado personalizado. Solo se muestra una actividad a la vez (la de mayor prioridad).

| Actividad       | Preview                            |
| --------------- | ---------------------------------- |
| Tetr.io         | ![Tetr.io](img/Tetr.io.png)        |
| Anime           | ![Anime](img/Anime.png)            |
| YouTube         | ![YouTube](img/YouTube.png)        |
| Roblox (Rivals) | ![Roblox](img/Roblox%20Rivals.png) |
| Custom Status   | ![Custom](img/Custom.png)          |

No necesitas usar todas las funciones. Instala solo los userscripts que quieras.

## Instalación

1. **[Descarga el ZIP](https://github.com/ema28pro/Multiple-Rich-Presence-Custom/archive/refs/heads/release.zip)** (rama `release` — solo los archivos necesarios)
2. Extrae el ZIP en una carpeta cualquiera
3. Listo — ya puedes ejecutar `DiscordPipeSocket.jar`

El ZIP contiene:
- `DiscordPipeSocket.jar` — el bridge
- `config.json` — configuración
- `custom-status/` — panel de estado personalizado
- `userscripts/` — scripts de Tampermonkey

## Requisitos

- **Java 8+** instalado
- **Discord** abierto (app de escritorio)
- **Tampermonkey** en tu navegador ([Chrome](https://chrome.google.com/webstore/detail/tampermonkey/dhdgffkkebhmkfjojejmpbldmpobfkfo) / [Firefox](https://addons.mozilla.org/en-US/firefox/addon/tampermonkey/) / [Edge](https://microsoftedge.microsoft.com/addons/detail/tampermonkey/iikmkjmpaadaobahmlepeloendndfphd)) — solo si vas a usar los userscripts

## Uso

### 1. Ejecutar el bridge

Doble clic en `DiscordPipeSocket.jar`. Se ejecuta en segundo plano con un icono en la bandeja del sistema.

Tiene que estar corriendo mientras uses cualquiera de las funciones. Es silencioso — ejecútalo una vez y olvídate. Si quieres que arranque con Windows, crea un **acceso directo** al `.jar` y pónlo en `%appdata%\Microsoft\Windows\Start Menu\Programs\Startup` (no muevas el JAR — necesita `config.json` y `bridge-state.json` en la misma carpeta).

### 2. Instalar userscripts (los que quieras)

Con Tampermonkey instalado:

- **TETR.IO** → Instala `userscripts/TETRIO-RPC.js`
- **Anime** → Instala `userscripts/Anime-RPC.js`
- **YouTube** → Instala `userscripts/YouTube-RPC.js`

Para instalar: abre Tampermonkey → Nuevo script → pega el contenido del archivo → Guardar.

Solo instala los que necesites. Si solo te interesa TETRIO, ignora el de anime y viceversa.

> **Nota:** Algunos adblockers pueden bloquear los scripts.  
> Si ves que el RPC no actualiza, recarga la página.

### 3. Roblox (automático)

El bridge detecta automáticamente cuando estás jugando Roblox desde los logs del juego. No necesitas instalar nada extra.

> **Importante:** Para que se muestre el estado de Rivals, debes **desactivar la detección de Roblox propia de Discord** en Ajustes → Actividades registradas → desactiva Roblox. Si no, Discord muestra su propia actividad y no la del bridge.

Por ahora solo detecta y muestra **Rivals**, pero en el futuro se podrá detectar cualquier juego de Roblox.

### 4. Custom Status (opcional)

Haz clic derecho en el icono de la bandeja → **Custom Status**. Se abre un panel en el navegador para configurar un estado personalizado con imágenes, texto y timer.

## Configuración

El archivo `config.json` viene preconfigurado y listo para usar. Solo necesitas cambiarlo si quieres personalizar el nombre de la app en Discord.

```json
{
  "clientId": "1479761532412887040",
  "tetrioClientId": "688741895307788290",
  "wsPort": 6680,
  "sourceTimeout": 15000
}
```

### Cambiar el nombre que aparece en Discord

Por defecto aparece "Playing **[nombre de la app]**" en tu perfil. Si quieres un nombre diferente:

1. Ve a https://discord.com/developers/applications
2. Crea una **New Application** — el nombre que le pongas es lo que aparecerá en Discord
3. Copia el **Application ID** y ponlo como `clientId` en `config.json`

El `tetrioClientId` ya viene configurado con la app oficial de TETR.IO (tiene los iconos de los modos). No lo cambies a menos que sepas lo que haces.

## Firefox

Si usas Firefox, ve a `about:config` y cambia `network.websocket.allowInsecureFromHTTPS` a `true`.

## Solución de problemas

- **No aparece el RPC**: Asegúrate de que `DiscordPipeSocket.jar` está corriendo y Discord está abierto.
- **Roblox no se detecta**: El bridge necesita que Roblox haya terminado de cargar el juego. Espera unos segundos.
- **El custom status se activa solo**: Cierra las pestañas viejas del panel de custom status en tu navegador.
- **Anime-RPC no funciona en AnimeFLV**: Desactiva el adblocker para que el script pueda conectarse.
- **Errores al cambiar entre userscripts**: Si el estado no se actualiza, recarga la página.

## Créditos

- [PATXS](https://github.com/PATXS) — Proyecto original TETRIO-browser-rpc
- [Jinzulen](https://github.com/Jinzulen) — Mangadex-RPC (base del DiscordPipeSocket)

---

⭐ Si te resulta útil, se agradece una estrella al repositorio.

¿Tienes sugerencias, recomendaciones o ideas para nuevas funciones (como un YouTube RPC)? ¡Abre un issue! y ¡Escribeme!
