# TETRIO-RPC Architecture Analysis

## Project Overview
TETRIO-browser-rpc: Discord Rich Presence for TETR.IO (browser-based)

### Two Implementations
1. **DiscordPipeSocket.jar** (Legacy Java) - Standalone Windows app using Java
2. **bridge/server.js** (Modern Node.js) - Multi-source activity bridge

---

## DiscordPipeSocket.jar (Java Implementation)
**Main Class:** `br.com.brforgers.armelin.dps.DiscordPipeSocket`

### Embedded Dependencies (JAR Internals)
- **org.java-websocket 1.3.8** - WebSocket server (port 6680)
- **org.json 20180130** - JSON parsing
- **com.google.gson 2.8.5** - GSON JSON library
- **club.minnced.discord.rpc** - Discord RPC IPC communication (minnced's library)
- **com.sun.jna** - Java Native Access (Windows IPC pipes)

### Custom Classes
- `DiscordPipeSocket.class` - Main app entry point
- `DiscordPipeSocket$1.class` - Inner class (listener/callback)

### Architecture Flow
```
Browser WebSocket Client (TETRIO-RPC.js)
    ↓ (JSON messages on port 6680)
WebSocket Server (org.java-websocket)
    ↓
DiscordPipeSocket Main Class
    ↓ (Parses JSON RPC data)
Discord RPC Library (club.minnced.discord.rpc)
    ↓ (Uses JNA for native pipes)
Windows IPC Named Pipe
    ↓
Discord.exe (Local IPC)
```

### Discord RPC Library Classes
- `DiscordRPC.class` - Main RPC client
- `DiscordRichPresence.class` - Activity data structure
- `DiscordUser.class` - User info
- `DiscordEventHandlers.class` - Callbacks:
  - `OnReady` - Connection established
  - `OnStatus` - Status update
  - `OnGameUpdate` - Activity updated
  - `OnJoinRequest` - Invite request

---

## Node.js Bridge (Modern Implementation)

### Port & Connection
- WebSocket: ws://127.0.0.1:6680
- Discord IPC: Uses discord-rpc npm package (built-in IPC)
- Source timeout: 15 seconds (configurable)

### Multi-Source Priority System
| Priority | Source | Notes |
|----------|--------|-------|
| 4 | Custom Status | User-set status |
| 2 | TETRIO / Anime / Roblox | Game activity |
| 1 | Browsing | Fallback |

### Protocol Message Format
**Client → Server (WebSocket JSON)**
```javascript
{
  source: "tetrio" || "anime" || "roblox" || "custom",
  priority: 2,
  rpc: {
    details: "40 LINES",
    state: "In Game",
    startTimestamp: Date.now(),
    largeImageKey: "logo" || "https://...",
    largeImageText: "Player Name",
    smallImageKey: "mode_40l",
    smallImageText: "40 LINES",
    buttons: [{label: "Join", url: "..."}],
    partySize: 1,
    partyMax: 4
  }
}
```

### Server Processing
1. Receives JSON from WebSocket
2. Parses source & priority
3. Updates source tracker with timestamp
4. Selects highest-priority non-timed-out source
5. Builds Discord activity object (truncates strings to Discord limits)
6. Sends to Discord IPC

### RPC Object Field Limits
- details: 128 chars
- state: 128 chars
- largeImageText: 128 chars
- smallImageText: 128 chars
- buttons: max 2, each label 32 chars, URL 512 chars
- startTimestamp/endTimestamp: epoch milliseconds (converted to Date)

---

## TETRIO-RPC.js (Userscript) Protocol

### WebSocket Connection
- Address: ws://127.0.0.1:6680
- Message interval: 3 seconds

### Detection & State Tracking
Monitors DOM elements:
- `#menus` (data-menu-type) - Menu state (none, lobby, 40l, blitz, zen, custom, tetra, victory)
- `body.ingame` - In-game status
- `#header_text` - Results screen detection
- `#mm_status_header` - Queue/match status
- `#replay` - Replay detection
- `#spectate` - Spectating detection

### Game States Detected
- In Game
- In Menus
- In Lobby / In Queue
- 40 LINES / BLITZ / ZEN / CUSTOM GAME
- TETRA LEAGUE
- Watching Replay
- Spectating
- In Lobby (ZEN while Waiting)
- Results Screen / Game Ending
- Logging in

### Duration Tracking
- `startTs` - Recorded on menu change, cleared on menu entry
- Used for activity startTimestamp

### Idle Detection
- Pauses RPC after 5 minutes of no focus (tab inactive)
- Resumes when tab regains focus
- Sets detail: "Idle", clears gamestate/timestamp

### Image Keys
Large image: "logo" (TETR.IO logo - asset key)
Small images (asset keys):
- mode_40l, mode_blitz, mode_zen, mode_custom
- mode_quickplay, mode_league

### User Info Display
- Username: `#me_username`
- Level: `#me_level`
- Rank: `#me_leaguerank.src` (league rank badge URL → filename → uppercase)
- Anonymous detection: `body.anon` class

---

## Windows IPC Named Pipes

**How JNA + Discord RPC Work:**
1. Discord.exe listens on named pipe: `\\.\pipe\discord-ipc-{0-9}`
2. Application connects via JNA → Windows API
3. Sends OPUS-encoded RPC commands in binary format
4. Discord deserializes and updates presence

**Key JNA Classes Used:**
- `com.sun.jna.Library` - Load native DLL
- `com.sun.jna.Pointer` - Memory pointers
- `com.sun.jna.Structure` - Binary data structures
- `com.sun.jna.Callback` - Event callbacks
- Native libraries: kernel32.dll, ntdll.dll (pipe I/O)

---

## Supported Features

### RPC Fields
✓ State (128 chars max)
✓ Details (128 chars max)
✓ Large image (URL or asset key)
✓ Small image (URL or asset key)
✓ Image text fields (128 chars each)
✓ Start/End timestamps (epoch ms)
✓ Party size/max
✓ Up to 2 buttons (label + URL)
✗ Party ID (not exposed in DiscordRichPresence)
✗ Secrets (join/spectate/match - not exposed)

### Image Handling
- **Asset Keys:** Upload in Discord Developer Portal, use by name
- **Direct URLs:** HTTPS URLs work, Discord caches ~2 min
- Example: `largeImageKey: "logo"` (asset) vs `"https://...png"` (URL)

### Connection Lifecycle

**Startup:**
1. Java app starts → localhost:6680
2. Discord must be running (IPC connection)
3. Userscript connects WebSocket
4. First RPC update sent

**Reconnection:**
- Userscript auto-reconnects on socket close
- Waits for tab to regain focus
- After 5 min idle → explicitly closes socket
- Spring-loads reconnection when focus returns

**Shutdown:**
- Close Discord → IPC disconnects
- Close Java app → WebSocket can't connect
- Node.js bridge auto-reconnects Discord every 10s

---

## Limitations & Design Notes

1. **One client ID per bridge** - Can't mix different Discord apps
2. **5-minute idle timeout** - Hardcoded in userscript
3. **3-second update interval** - Userscript refresh rate
4. **Port hardcoded** - 6680 in userscript & Java jar
5. **WebSocket unencrypted** - Localhost only, safe assumption
6. **No authentication** - Anyone on localhost can update RPC
7. **Single main class** - DiscordPipeSocket does all work (monolithic)
8. **Legacy protocol** - No metadata about source origin in original design
9. **No persistent config** - Java jar has no config file, uses hardcoded values
10. **Timestamps in milliseconds** - JavaScript epoch, needs conversion for Discord

---

## File Structure Summary

```
DiscordPipeSocket - copia/
  META-INF/
    MANIFEST.MF → Main-Class: br.com.brforgers.armelin.dps.DiscordPipeSocket
    maven/ → Dependency versions (org.json, java-websocket, gson, etc.)
  
  br/com/brforgers/armelin/dps/
    DiscordPipeSocket.class (main)
    DiscordPipeSocket$1.class (inner class)
  
  club/minnced/discord/rpc/ (8 files)
    DiscordRPC.class
    DiscordRichPresence.class
    DiscordUser.class
    DiscordEventHandlers.class + 4 inner callback classes
  
  org/java_websocket/ (WebSocket server, 20+ utility classes)
  org/json/ (JSON library, 20+ classes)
  com/google/gson/ (GSON JSON library, 50+ classes)
  com/sun/jna/ (Java Native Access, 100+ classes for Windows API)
  
  Image assets:
    pipe_right_small.png
    pipe_right_small_96.png
  
  Native libraries:
    win32-x86/ (32-bit Windows DLL bindings)
    win32-x86-64/ (64-bit Windows DLL bindings)
    darwin/ (macOS natives, not functional)
    linux-x86-64/ (Linux natives, not functional on Windows)
```

---

## Key Takeaways

1. **The jar is a WebSocket → Discord IPC bridge.** Nothing more.
2. **All game detection is in the userscript**, not the Java app.
3. **Uses native Windows APIs via JNA** to communicate with Discord's pipe.
4. **Simple JSON protocol** for extensibility (other games can send updates).
5. **Legacy protocol has no source identification** (modern bridge adds `source` field).
6. **Java app is stateless** — doesn't know what's running, just forwards RPC data.
7. **Modern Node.js version is more sophisticated** — multi-source, priority system, auto-reconnect.
