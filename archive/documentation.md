# Discord RPC Multi-Activity Bridge

Bridge de Discord Rich Presence basado en Java que soporta múltiples fuentes de actividad simultáneamente con sistema de prioridades.

Basado en [TETRIO-browser-rpc](https://github.com/PATXS/TETRIO-browser-rpc) de PATXS.

## Funciones

| Función           | Descripción                                                                                   |
| ----------------- | --------------------------------------------------------------------------------------------- |
| **TETRIO RPC**    | Muestra tu actividad en TETR.IO (modos, partidas, cola, replays) con su propia app de Discord |
| **Anime RPC**     | Detecta anime y episodio en VerAnimes, Crunchyroll, AnimeFLV y JKAnime                        |
| **Roblox RPC**    | Detecta automáticamente el juego de Roblox desde los logs del escritorio                      |
| **Custom Status** | Panel HTML para configurar un estado personalizado                                            |

## Estructura

```
├── DiscordPipeSocket.jar    ← Ejecutable principal (bridge Java)
├── config.json              ← Configuración (Client IDs, puerto, timeout)
├── build.bat                ← Script para recompilar el JAR
│
├── src/                     ← Código fuente Java
│   └── br/com/brforgers/armelin/dps/
│       ├── Config.java              ← Carga config.json
│       ├── DiscordPipeSocket.java   ← WebSocket server + Discord RPC
│       ├── SourceManager.java       ← Sistema de prioridades multi-fuente
│       └── RobloxMonitor.java       ← Monitor de Roblox Desktop
│
├── userscripts/             ← Scripts para Tampermonkey
│   ├── TETRIO-RPC.js        ← RPC para TETR.IO
│   └── Anime-RPC.js         ← RPC para sitios de anime
│
├── custom-status/           ← Panel de estado personalizado
│   └── index.html           ← Abrir en el navegador
│
└── archive/                 ← Archivos legacy/respaldo (no se usan)
```

## Requisitos

- **Java 8+** instalado (para ejecutar el JAR)
- **Discord** abierto (escritorio)
- **Tampermonkey** en el navegador ([Chrome](https://chrome.google.com/webstore/detail/tampermonkey/dhdgffkkebhmkfjojejmpbldmpobfkfo) / [Firefox](https://addons.mozilla.org/en-US/firefox/addon/tampermonkey/))

## Instalación

### 1. Configurar Discord Developer Portal

1. Ir a https://discord.com/developers/applications
2. Crear una **New Application** (el nombre aparecerá como "Playing **nombre**")
3. Copiar el **Application ID**
4. *(Opcional)* Si quieres TETRIO con su app original, tener un segundo Application ID

### 2. Configurar `config.json`

```json
{
  "clientId": "TU_APPLICATION_ID",
  "tetrioClientId": "688741895307788290",
  "wsPort": 6680,
  "sourceTimeout": 15000
}
```

| Campo            | Descripción                                                  |
| ---------------- | ------------------------------------------------------------ |
| `clientId`       | Application ID principal (Roblox, Anime, Custom Status)      |
| `tetrioClientId` | Application ID para TETRIO (usa la app original con assets)  |
| `wsPort`         | Puerto WebSocket (default: 6680)                             |
| `sourceTimeout`  | Tiempo en ms antes de que una fuente expire (default: 15000) |

### 3. Ejecutar el bridge

Doble clic en `DiscordPipeSocket.jar` (se ejecuta en segundo plano con icono en la bandeja del sistema).

### 4. Instalar userscripts

Abrir en el navegador con Tampermonkey activo:

- **TETRIO**: Copiar contenido de `userscripts/TETRIO-RPC.js` → Tampermonkey → Nuevo script → Pegar → Guardar
- **Anime**: Copiar contenido de `userscripts/Anime-RPC.js` → Tampermonkey → Nuevo script → Pegar → Guardar

### 5. Custom Status (opcional)

Abrir `custom-status/index.html` en el navegador mientras el bridge está corriendo.

## Sitios de Anime soportados

| Sitio           | Detección              | Imagen                             |
| --------------- | ---------------------- | ---------------------------------- |
| **JKAnime**     | URL + alt de imagen    | CDN de JKAnime                     |
| **VerAnimes**   | URL + título de página | CDN de VerAnimes                   |
| **AnimeFLV**    | URL                    | og:image (screenshot del episodio) |
| **Crunchyroll** | meta tags              | og:image                           |

> **Nota**: Si usas uBlock Origin Lite, puede bloquear los userscripts en AnimeFLV. Desactívalo en ese sitio o agrégalo a la lista de excepciones.

## Roblox Desktop

El `RobloxMonitor` detecta automáticamente cuando estás jugando un juego de Roblox:

- Monitorea los logs de `%LOCALAPPDATA%\Roblox\logs`
- Detecta el `PlaceId` del juego actual
- Consulta la API pública de Roblox para obtener el nombre del juego
- Se actualiza cada 10 segundos

No requiere configuración adicional.

## Sistema de prioridades

Cuando hay múltiples fuentes activas, se muestra la de mayor prioridad:

| Prioridad | Fuente                  |
| --------- | ----------------------- |
| 4         | Custom Status           |
| 2         | TETRIO / Anime / Roblox |

Si dos fuentes tienen la misma prioridad, se muestra la más reciente.

## Recompilar (desarrollo)

Si modificas el código fuente Java:

1. Tener **JDK 8+** instalado con `javac` en el PATH
2. Ejecutar `build.bat`

```bat
build.bat
```

Esto compila los `.java`, actualiza el JAR y crea un backup automático.

## Notas

- El bridge usa `discord-rpc.dll` (C SDK) vía JNA — no soporta botones en el Rich Presence
- TETRIO usa su propio Client ID (`688741895307788290`) con assets subidos (logo, iconos de modos)
- Las demás fuentes usan el Client ID principal configurado en `config.json`
- El cambio de Client ID es automático al cambiar de fuente activa
