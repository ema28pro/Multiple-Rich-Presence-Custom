## Análisis Completo del JAR Decompilado

He explorado exhaustivamente el `DiscordPipeSocket - copia/` directorio. Aquí está el análisis completo:

---

## 📂 1. ESTRUCTURA COMPLETA DEL DIRECTORIO

### Recuento de Archivos
- **388 archivos .class** (código compilado de Java)
- **0 archivos .java** (fuente - no disponible)
- **2 clases personalizadas** (el código real de la app)
- **300+ clases de librerías** (WebSocket, JSON, Discord RPC, JNA)

### Árbol de Directorios Clave
```
DiscordPipeSocket - copia/
├── META-INF/
│   ├── MANIFEST.MF
│   └── maven/
│       ├── org.json (v20180130)
│       ├── org.java-websocket (v1.3.8)
│       └── com.google.code.gson (v2.8.5)
├── br/com/brforgers/armelin/dps/
│   ├── DiscordPipeSocket.class ← MAIN CLASS
│   └── DiscordPipeSocket$1.class ← INNER CLASS
├── club/minnced/discord/rpc/ (8 archivos)
│   ├── DiscordRPC.class
│   ├── DiscordRichPresence.class
│   ├── DiscordEventHandlers.class
│   └── 4× event handler inner classes
├── org/java_websocket/ (WebSocket lib)
├── org/json/ (JSON parsing)
├── com/google/gson/ (GSON JSON)
├── com/sun/jna/ (Java Native Access)
└── Imágenes & binarios nativos
```

---

## 🔧 2. ARQUITECTURA DEL SISTEMA

### Flujo de Datos
```
Browser (TETR.IO)
    ↓ (conexión WebSocket a puerto 6680)
DiscordPipeSocket.jar (WebSocket server)
    ↓ (recibe JSON con RPC data)
Parsea JSON (GSON/org.json)
    ↓
DiscordRPC client (biblioteca de minnced)
    ↓ (usa JNA para acceder a Windows APIs)
Windows Named Pipe (\\.\pipe\discord-ipc-*)
    ↓
Discord.exe (local IPC)
```

---

## 📡 3. CÓDIGO DE COMUNICACIÓN (Basado en el userscript + bridge moderno)

### Protocolo WebSocket - Mensaje Enviado

El userscript [TETRIO-RPC.js](TETRIO-RPC.js#L1) envía cada 3 segundos:

```javascript
{
  cid: "688741895307788290",  // Discord App Client ID
  rpc: {
    state: "In Game" || "In Menus" || "In Queue" || null,
    details: "40 LINES" || "BLITZ" || "TETRA LEAGUE" || null,
    
    largeImageKey: "logo",
    largeImageText: "PlayerName - Lv. 50 - SS",
    
    smallImageKey: "mode_40l" || "mode_blitz" || "mode_zen" || "mode_custom" ||
                   "mode_quickplay" || "mode_league",
    smallImageText: "40 LINES",
    
    startTimestamp: 1699564800000  // epoch ms (cuando empezó el gamestate actual)
  }
}
```

### Procesamiento en el Server (DiscordPipeSocket/Bridge)

1. **Recibe JSON** vía WebSocket
2. **Parsea campos** usando GSON/org.json
3. **Construye DiscordRichPresence** object
4. **Conecta a Discord IPC** via JNA + Windows named pipes
5. **Envía OPUS-encoded data** a Discord

```
JSON String
  ↓ (GSON JsonElement.parse)
DiscordRichPresence POJO
  ↓ (JNA memory marshaling)
Binary RPC Protocol
  ↓ (Windows pipe write)
Discord.exe reads & displays
```

---

## 🔌 4. COMUNICACIÓN DISCORD IPC (Via JNA + Windows Pipes)

### Cómo Funciona Discord RPC en Windows

**La biblioteca `club.minnced.discord.rpc` hace esto:**

1. **Busca Discord IPC pipes:**
   ```
   \\.\pipe\discord-ipc-0
   \\.\pipe\discord-ipc-1
   \\.\pipe\discord-ipc-2
   ...\\.\pipe\discord-ipc-9
   ```

2. **Conecta usando JNA** (Java Native Access):
   - Carga `kernel32.dll` (Windows API)
   - Llama `CreateFileA()` para abrir la pipe
   - Usa `ReadFile()`/`WriteFile()` para I/O

3. **Envía en formato OPUS:**
   ```
   [opcode: 1][length: 4-byte int][JSON data][opcode: 1]
   ```
   Ejemplo:
   ```
   \x01\x00\x00\x00\x3c{"cmd":"SET_ACTIVITY","args":{"pid":12345,"activity":{...}}}
   ```

4. **Discord.exe recibe y procesa:**
   - Deserializa JSON
   - Actualiza User Presence
   - Muestra en Discord UI

### Clases JNA Utilizadas
```java
// from com.sun.jna.*
Library.class          // Carga DLLs nativas
Pointer.class          // Punteros de memoria
Structure.class        // Estructuras binarias C
Callback.class         // Callbacks para eventos
Native.class           // Bootstrap de librerías

// from com.sun.jna.win32.*
Win32LibraryPath       // Detecta ruta de DLLs
KernelFunc             // kernel32.dll bindings
Pipe I/O               // CreateFile, ReadFile, WriteFile
```

---

## 📊 5. MANEJO DE CARACTERÍSTICAS

### Datos Soportados en RPC

| Campo | Límite | Tipo | Notas |
|-------|--------|------|-------|
| `state` | 128 chars | string | "In Game", "In Menus", etc. |
| `details` | 128 chars | string | "40 LINES", "TETRA LEAGUE" |
| `largeImageKey` | N/A | string | Asset key o URL HTTPS |
| `largeImageText` | 128 chars | string | Tooltip texto |
| `smallImageKey` | N/A | string | Asset key o URL |
| `smallImageText` | 128 chars | string | Tooltip texto |
| `startTimestamp` | N/A | long | Epoch milisegundos |
| `endTimestamp` | N/A | long | Epoch milisegundos |
| `partySize` | N/A | int | Tamaño actual party |
| `partyMax` | N/A | int | Tamaño máximo party |
| `buttons` | max 2 | array | Label (32 chars) + URL (512 chars) |

### Imágenes: Asset Keys vs URLs

**Asset Keys** (Recomendado):
- Subidas en Discord Developer Portal: Aplicación → Rich Presence → Art Assets
- Uso: `largeImageKey: "logo"`
- Ventaja: Carga instantánea, sin latencia

**URLs Directas**:
- `largeImageKey: "https://site.com/image.png"`
- Discord descarga y cachea (~2 minutos)
- Requiere HTTPS + acceso público
- Más lento (latencia de 1-2 segundos)

**TETRIO RPC actual usa:**
```javascript
largeImageKey: "logo"          // Asset key
smallImageKey: "mode_40l"      // Asset key
```

---

## ⏱️ 6. MANEJO DE TIMESTAMPS Y CICLO DE VIDA

### Cálculo del Timestamp

La [TETRIO-RPC.js](TETRIO-RPC.js#L80-L95) rastrea:

```javascript
var startTs = 0;  // Grabado cuando cambias de menú a juego

// Cuando está en juego:
startTimestamp: startTs  // Cálculo: ahora - (Date.now() - startTs)

// Cuando está en menú/inactivo:
startTimestamp: null     // Sin temporizador en Discord
```

**Lógica:**
- Usuario selecciona "40 LINES" → `updateTimestamp()` graba `Date.now()`
- Empieza a jugar → envía same `startTimestamp` cada 3 segundos
- Discord calcula: "jugando desde X tiempo atrás"
- Si vuelve a menú → limpia `startTs`

### Detección de Inactividad (Idle)

```javascript
// 5 minutos (300,000 ms) sin focus del navegador
if (idleStart + 300000 <= Date.now()) {
    Socket.close();  // Cierra la conexión
    filled: true;
}
// Cuando vuelve el focus → Socket se reconecta
```

---

## 📡 7. CICLO DE VIDA DE CONNECTION

### Startup (Arranque)
1. **Usuario lanza DiscordPipeSocket.jar**
   - JVM inicia `br.com.brforgers.armelin.dps.DiscordPipeSocket`
   - Lee puerto 6680 (hardcoded)
   - Inicia WebSocket server

2. **Discord debe estar running**
   - DiscordRPC intenta conectar a `\\.\pipe\discord-ipc-0` → `discord-ipc-9`
   - Si falla → reintentos automáticos (cada 10s en el bridge Node.js)

3. **Userscript conecta**
   ```javascript
   Socket = new WebSocket("ws://127.0.0.1:6680");
   ```
   - Si falla → espera a que el app esté listo
   - console.log: "It seems the DPS is not running on port 6680."

### Runtime - Detección de Juego

[TETRIO-RPC.js](TETRIO-RPC.js#L22-L68) monitorea cada 1 segundo:
```javascript
currMenu = document.querySelector("#menus").getAttribute("data-menu-type");
// "none" → jugando | "lobby" → lobby | "40l", "blitz", etc → en menú
```

### Reconnection

- **Si cierra DiscordPipeSocket.jar:** WebSocket cierra, userscript reconecta cada 3s
- **Si cierra Discord.exe:** IPC pipe muere, pero WebSocket sigue abierto (datos en cola)
- **Si inactivo 5 mins:** Userscript cierra socket propositadamente, luego reconecta al hacer focus

---

## 🔑 8. PROTOCOLO: DETECCIÓN DE ESTADOS

La app NO conoce el estado del juego. El **userscript** lo detecta en el DOM:

```javascript
// ============ MENÚ DETECTION ============
if (currMenu != "none") {
    detail = "In Menus";
    if (currMenu == "tetra" || currMenu == "tetra_records") 
        detail = "Tetra Channel";
}

// ============ LOBBY ============
if (currMenu == "lobby") {
    gamestate = "In Lobby";
    if (document.querySelector("#roomview").className.includes("sysroom"))
        detail = "QUICK PLAY";
    else
        detail = "CUSTOM ROOM";
}

// ============ EN JUEGO ============
if ((currMenu == "none" || currMenu == "lobby") && document.body.classList.contains("ingame")) {
    if (prev_menu == "40l")
        detail = "40 LINES";
    else if (prev_menu == "blitz")
        detail = "BLITZ";
    // ... etc
}

// ============ REPLAY ============
if (!document.querySelector("#replay").className.includes("hidden"))
    gamestate = "Watching Replay";

// ============ QUEUE ============
if (document.querySelector("#mm_status_header").innerText == "FINDING MATCH")
    detail = "TETRA LEAGUE",
    gamestate = "In Queue";
```

**Estados Soportados:**
- In Game
- In Menus
- Tetra Channel
- In Lobby / In Queue
- Results Screen / Game Ending
- Watching Replay
- Spectating
- 40 LINES / BLITZ / ZEN / CUSTOM GAME / TETRA LEAGUE
- Logging in...
- Idle (después de 5 mins sin focus)

---

## 📚 9. LIBRERÍAS EMBEBIDAS EN EL JAR

| Librería | Versión | Propósito |
|----------|---------|-----------|
| **org.java-websocket** | 1.3.8 | WebSocket server (recibe JSON) |
| **org.json** | 20180130 | JSON parsing (org.json.JSONObject) |
| **com.google.gson** | 2.8.5 | Serialización JSON ↔ POJO |
| **club.minnced.discord.rpc** | unknown | Discord IPC client (minnced's lib) |
| **com.sun.jna** | unknown | Java Native Access (Windows APIs) |

### Detalles Técnicos

**WebSocket:**
- puerto `6680` hardcoded
- descodifica JSON mensajes
- no hay autenticación (localhost only)

**JSON Libraries:**
- `org.json` - parsing manual de strings
- `GSON` - reflection-based object mapping
- Ambas usadas para máxima flexibilidad

**JNA (Java Native Access):**
- Accede a `kernel32.dll`
- Funciones: `CreateFileA`, `ReadFile`, `WriteFile`
- Lee/escribe en named pipes Windows
- 100+ clases para bindings C ↔ Java

---

## ⚠️ 10. LIMITACIONES Y FEATURES

### Lo que SÍ soporta:
✅ State + Details (2 líneas de texto)
✅ Imágenes grandes y pequeñas (asset keys o URLs)
✅ Timestamps/cronómetro (startTimestamp)
✅ Party size/max
✅ Hasta 2 botones (label + URL)
✅ Auto-reconnect en WebSocket
✅ Idle timeout después de 5 mins
✅ Detección multiforma de juego (menú, lobby, juego, replay, etc.)

### Lo que NO soporta:
❌ Party ID (Discord game session linking)
❌ Secrets (join/spectate tokens)
❌ Activity flags (instancia, sync, play, compete)
❌ Emoji customizados
❌ Configuración persistente (todo hardcoded)
❌ Multi-usuario en mismo PC (un solo client ID)
❌ Múltiples apps de Discord simultáneamente
❌ Proxy HTTP/SOCKS

---

## 📝 RESUMEN EJECUTIVO

**DiscordPipeSocket.jar es un puente simple:**

1. **Escucha** en WebSocket localhost:6680
2. **Parsea** JSON con campos RPC
3. **Conecta** a Discord via Windows IPC pipes (JNA)
4. **Envía** datos de presencia en formato OPUS
5. **Todo lo demás** (detección de juego, timestamps, imágenes) **está en el userscript**

La app **NO necesita conocer** qué está pasando en el juego. Solo es un puente JSON → IPC. Otros juegos pueden usar el mismo puerto si envían JSON compatible.

---

**Análisis completo guardado en memoria de repositorio para referencia futura.**