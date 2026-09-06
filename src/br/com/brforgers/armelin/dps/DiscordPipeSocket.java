package br.com.brforgers.armelin.dps;

import club.minnced.discord.rpc.DiscordEventHandlers;
import club.minnced.discord.rpc.DiscordRPC;
import club.minnced.discord.rpc.DiscordRichPresence;
import com.google.gson.Gson;
import java.awt.AWTException;
import java.awt.Desktop;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

public class DiscordPipeSocket {
    private static final Logger logger = Logger.getLogger("DPS");

    static String lastid = "";
    static final Gson gson = new Gson();
    static SourceManager sourceManager;
    static String currentClientId = "";

    public DiscordPipeSocket() {
    }

    public static void main(String[] args) throws AWTException, IOException {
        final DiscordRPC lib = DiscordRPC.INSTANCE;

        // Load configuration
        final Config config = Config.load();
        LoggerConfig.setup(config.logFile);

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception var12) {
            port = config.wsPort;
        }

        // Check if port is already in use (prevent multiple instances)
        try (java.net.ServerSocket ss = new java.net.ServerSocket(port)) {
            // port is free
        } catch (Exception e) {
            logger.severe("[Bridge] Port " + port + " is already in use! Another instance is running. Exiting.");
            System.exit(1);
        }

        // Initialize SourceManager
        sourceManager = new SourceManager(config.sourceTimeout);

        // Restore bridge state from previous session
        final JSONObject savedState = loadBridgeState(sourceManager);

        final DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.ready = (user) -> logger.info("Welcome " + user.username + "#" + user.discriminator + ".");
        handlers.errored = (errorCode, message) -> logger.severe("[Discord] Error " + errorCode + ": " + message);
        handlers.disconnected = (errorCode, message) -> logger
                .severe("[Discord] Disconnected " + errorCode + ": " + message);

        // Initialize Discord RPC once at startup if clientId is configured
        if (!config.clientId.isEmpty()) {
            boolean ipcOk = DiscordIPC.connect(config.clientId);
            if (ipcOk) {
                logger.info("[Bridge] DiscordIPC initialized with clientId from config.json");
            } else {
                lib.Discord_Initialize(config.clientId, handlers, true, "");
                currentClientId = config.clientId;
                lastid = config.clientId;
                logger.info("[Bridge] Discord initialized (fallback) with clientId from config.json");
            }
        }

        // Scheduled cleanup thread — checks for expired sources every 5 seconds
        final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "SourceCleanup");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sourceManager.cleanExpired();
                SourceManager.UpdateResult result = sourceManager.checkForChanges();
                applyPresenceUpdate(result, config, lib, handlers);
            } catch (Exception e) {
                logger.severe("[Bridge] Cleanup error: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Roblox Desktop Monitor — checks process every 10 seconds
        final RobloxMonitor robloxMonitor = new RobloxMonitor(sourceManager, 2);
        if (savedState != null && savedState.has("robloxMonitorEnabled")) {
            boolean rmEnabled = savedState.optBoolean("robloxMonitorEnabled", true);
            robloxMonitor.setEnabled(rmEnabled);
            logger.info("[Bridge] Roblox Monitor restored: " + (rmEnabled ? "ON" : "OFF"));
        }
        scheduler.scheduleAtFixedRate(() -> {
            try {
                robloxMonitor.run();
            } catch (Exception e) {
                logger.severe("[RobloxMonitor] Scheduled error: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
        logger.info("[Bridge] Roblox Desktop Monitor active");

        WebSocketServer server = new WebSocketServer(new InetSocketAddress("localhost", port)) {
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                logger.info("[Bridge] Client connected: " + conn.getRemoteSocketAddress());
            }

            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                logger.info("[Bridge] Client disconnected (code: " + code + ")");
                // Do NOT call Discord_Shutdown() here — other sources may still be active.
                // Discord RPC stays alive; sources expire via SourceManager timeout.
            }

            public void onMessage(WebSocket conn, String message) {
                try {
                    JSONObject jsonObject = new JSONObject(message);
                    logger.info(
                            "[Bridge] WebSocket message from source: " + jsonObject.optString("source", "legacy"));

                    // Handle query: {action: "query", source: "custom"}
                    if (jsonObject.has("action") && "query".equals(jsonObject.getString("action"))) {
                        String source = jsonObject.optString("source", "");
                        JSONObject response = new JSONObject();
                        response.put("type", "queryResponse");
                        response.put("source", source);
                        SourceManager.SourceEntry entry = sourceManager.getSource(source);
                        if (entry != null) {
                            response.put("active", true);
                            response.put("rpc", entry.rpcData);
                            response.put("persistent", entry.persistent);
                        } else {
                            response.put("active", false);
                        }

                        // Provide all configured Client IDs so the web client dropdown can list them
                        JSONObject configIds = new JSONObject();
                        if (config.clientId != null && !config.clientId.isEmpty()) configIds.put("Default / General", config.clientId);
                        if (config.tetrioClientId != null && !config.tetrioClientId.isEmpty()) configIds.put("TETR.IO", config.tetrioClientId);
                        if (config.robloxClientId != null && !config.robloxClientId.isEmpty()) configIds.put("Roblox", config.robloxClientId);
                        if (config.youtubeClientId != null && !config.youtubeClientId.isEmpty()) configIds.put("YouTube", config.youtubeClientId);
                        if (config.wplaceClientId != null && !config.wplaceClientId.isEmpty()) configIds.put("WPlace", config.wplaceClientId);
                        if (config.animeClientId != null && !config.animeClientId.isEmpty()) configIds.put("Anime", config.animeClientId);
                        response.put("configClientIds", configIds);
                        response.put("currentClientId", DiscordIPC.getCurrentClientId());

                        conn.send(response.toString());
                        return;
                    }

                    // New multi-source protocol: {source, priority, rpc}
                    if (jsonObject.has("source")) {
                        String source = jsonObject.getString("source");

                        // Handle remove action: {source, action: "remove"}
                        if (jsonObject.has("action") && "remove".equals(jsonObject.getString("action"))) {
                            logger.info("[Bridge] Removing source: " + source);
                            sourceManager.removeSource(source);
                            SourceManager.UpdateResult result = sourceManager.checkForChanges();
                            applyPresenceUpdate(result, config, lib, handlers);
                            return;
                        }

                        // When Custom Status is active, ignore incoming messages from other sources
                        if (!"custom".equals(source) && sourceManager.getSource("custom") != null) {
                            logger.info("[Bridge] Custom Status is active — ignoring outside source: " + source);
                            return;
                        }

                        int priority = jsonObject.getInt("priority");
                        JSONObject rpc = jsonObject.getJSONObject("rpc");
                        boolean persistent = jsonObject.optBoolean("persistent", false);
                        logger.info("[Bridge] Received from source: " + source + " (priority: " + priority
                                + ", persistent: " + persistent + ")");

                        // Custom source must always be persistent (sent by legitimate HTML page).
                        // Reject phantom custom updates (e.g. cached tabs sending without persistent
                        // field).
                        if ("custom".equals(source) && !persistent) {
                            logger.info("[Bridge] Ignoring non-persistent custom source (phantom client)");
                            return;
                        }

                        if (persistent) {
                            sourceManager.updatePersistentSource(source, priority, rpc);
                        } else {
                            sourceManager.updateSource(source, priority, rpc);
                        }

                        SourceManager.UpdateResult result = sourceManager.checkForChanges();
                        boolean ok = applyPresenceUpdate(result, config, lib, handlers);
                        try {
                            JSONObject statusResp = new JSONObject();
                            statusResp.put("type", "presenceUpdateResult");
                            statusResp.put("success", ok);
                            statusResp.put("clientId", DiscordIPC.getCurrentClientId());
                            conn.send(statusResp.toString());
                        } catch (Exception ignored) { }
                        return;
                    }

                    // Legacy protocol: {cid, rpc}
                    if (sourceManager.getSource("custom") != null) {
                        logger.info("[Bridge] Custom Status is active — ignoring legacy outside message");
                        return;
                    }
                    String cid = jsonObject.getString("cid");
                    JSONObject rpcObj = jsonObject.getJSONObject("rpc");
                    if (DiscordIPC.isConnected() || DiscordIPC.connect(cid)) {
                        DiscordIPC.updatePresence(rpcObj);
                    } else {
                        if (!cid.equals(DiscordPipeSocket.lastid)) {
                            if ("".equals(DiscordPipeSocket.lastid)) {
                                DiscordPipeSocket.lastid = cid;
                                lib.Discord_Initialize(DiscordPipeSocket.lastid, handlers, true, "");
                            } else {
                                lib.Discord_Shutdown();
                                DiscordPipeSocket.lastid = cid;
                                lib.Discord_Initialize(DiscordPipeSocket.lastid, handlers, true, "");
                            }
                        }
                        lib.Discord_RunCallbacks();
                        DiscordRichPresence discordRichPresence = gson.fromJson(
                                new String(
                                        jsonObject.get("rpc").toString().getBytes(StandardCharsets.UTF_8),
                                        StandardCharsets.UTF_8
                                ),
                                DiscordRichPresence.class
                        );
                        lib.Discord_UpdatePresence(discordRichPresence);
                    }
                } catch (Exception e) {
                    logger.severe("[Bridge] Error processing message: " + e.getMessage());
                }
            }

            public void onError(WebSocket conn, Exception ex) {
                logger.severe("[Bridge] WebSocket error: " + ex.getMessage());
            }

            public void onStart() {
                logger.info("Websocket started on port: " + this.getPort());
            }
        };

        final TrayIcon[] trayRef = new TrayIcon[1];

        PopupMenu popMenu = new PopupMenu();
        MenuItem item1 = new MenuItem("Port: " + port);
        item1.setEnabled(false);
        MenuItem item2 = new MenuItem("Exit");
        item2.addActionListener((ex) -> {
            // Immediately remove tray icon so UI feels instant
            try {
                if (trayRef[0] != null && SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(trayRef[0]);
                }
            } catch (Throwable ignored) { }

            // Launch fallback force-exit timer in 800ms
            Thread forceHalt = new Thread(() -> {
                try {
                    Thread.sleep(800);
                } catch (InterruptedException ignored) { }
                Runtime.getRuntime().halt(0);
            }, "Force-Halt");
            forceHalt.setDaemon(true);
            forceHalt.start();

            // Run graceful cleanup in background thread
            new Thread(() -> {
                try {
                    saveBridgeState(sourceManager, robloxMonitor);
                } catch (Throwable ignored) { }
                try {
                    scheduler.shutdownNow();
                } catch (Throwable ignored) { }
                try {
                    DiscordIPC.disconnect();
                } catch (Throwable ignored) { }
                try {
                    if (!currentClientId.isEmpty()) {
                        lib.Discord_Shutdown();
                    }
                } catch (Throwable ignored) { }
                System.exit(0);
            }, "Exit-Cleanup").start();
        });
        MenuItem item3 = new MenuItem("Custom Status");
        item3.addActionListener((ex) -> {
            try {
                File htmlFile = new File("custom-status/index.html").getAbsoluteFile();
                if (!htmlFile.exists()) {
                    logger.severe("[Bridge] custom-status/index.html not found at: " + htmlFile.getPath());
                    return;
                }
                String url = htmlFile.toURI().toString();
                try {
                    Runtime.getRuntime().exec(new String[] { "cmd", "/c", "start", "chrome", url });
                } catch (Exception e1) {
                    try {
                        Runtime.getRuntime().exec(new String[] { "cmd", "/c", "start", "msedge", url });
                    } catch (Exception e2) {
                        Desktop.getDesktop().browse(htmlFile.toURI());
                    }
                }
            } catch (Exception e) {
                logger.severe("[Bridge] Could not open browser: " + e.getMessage());
            }
        });
        MenuItem item4 = new MenuItem("Roblox Monitor: " + (robloxMonitor.isEnabled() ? "ON" : "OFF"));
        item4.addActionListener((ex) -> {
            boolean nowEnabled = !robloxMonitor.isEnabled();
            robloxMonitor.setEnabled(nowEnabled);
            item4.setLabel("Roblox Monitor: " + (nowEnabled ? "ON" : "OFF"));
            logger.info("[Bridge] Roblox Monitor " + (nowEnabled ? "enabled" : "disabled"));
            saveBridgeState(sourceManager, robloxMonitor);
        });
        popMenu.add(item1);
        popMenu.add(item3);
        popMenu.add(item4);
        popMenu.addSeparator();
        popMenu.add(item2);
        if (SystemTray.isSupported()) {
            try {
                BufferedImage img = ImageIO.read(DiscordPipeSocket.class.getResource("/pipe_right_small.png"));
                int trayiconw = (new TrayIcon(img)).getSize().width;
                TrayIcon trayIcon = new TrayIcon(img.getScaledInstance(trayiconw, -1, 4), "Discord Pipe Socket",
                        popMenu);
                SystemTray.getSystemTray().add(trayIcon);
                trayRef[0] = trayIcon;
            } catch (Exception e) {
                logger.severe("[Bridge] Could not initialize System Tray: " + e.getMessage());
            }
        } else {
            logger.info(
                    "[Bridge] System Tray is not supported on this OS/Desktop environment. Running without tray icon.");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (trayRef[0] != null && SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(trayRef[0]);
                }
            } catch (Throwable ignored) { }
        }));

        server.run();
    }

    static File getJarDir() {
        try {
            return new File(
                    DiscordPipeSocket.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                    .getParentFile();
        } catch (Exception e) {
            return new File(".");
        }
    }

    static File getStateFile() {
        File jarDir = getJarDir();
        File customDir = new File(jarDir, "custom-status");
        File stateInCustom = new File(customDir, "bridge-state.json");
        if (stateInCustom.exists()) {
            return stateInCustom;
        }
        File stateInRoot = new File(jarDir, "bridge-state.json");
        if (stateInRoot.exists()) {
            return stateInRoot;
        }
        if (customDir.exists()) {
            return stateInCustom;
        }
        return stateInRoot;
    }

    static void saveBridgeState(SourceManager sm, RobloxMonitor rm) {
        try {
            File stateFile = getStateFile();
            JSONObject state = new JSONObject();

            // Custom status
            SourceManager.SourceEntry custom = sm.getSource("custom");
            JSONObject customState = new JSONObject();
            if (custom != null) {
                customState.put("active", true);
                customState.put("priority", custom.priority);
                customState.put("rpc", custom.rpcData);
            } else {
                customState.put("active", false);
            }
            state.put("custom", customState);

            // Roblox Monitor
            state.put("robloxMonitorEnabled", rm.isEnabled());

            FileWriter writer = new FileWriter(stateFile);
            writer.write(state.toString(2));
            writer.close();
            logger.info(
                    "[Bridge] State saved to " + stateFile.getName() + " (custom: " + (custom != null) + ", robloxMonitor: " + rm.isEnabled() + ")");
        } catch (Exception e) {
            logger.severe("[Bridge] Error saving state: " + e.getMessage());
        }
    }

    static JSONObject loadBridgeState(SourceManager sm) {
        try {
            File stateFile = getStateFile();

            // Migrate from old custom-state.json if it exists
            if (!stateFile.exists()) {
                File oldFile = new File(getJarDir(), "custom-state.json");
                if (oldFile.exists()) {
                    oldFile.renameTo(stateFile);
                    logger.info("[Bridge] Migrated custom-state.json -> " + stateFile.getName());
                }
            }

            if (!stateFile.exists())
                return null;

            FileInputStream fis = new FileInputStream(stateFile);
            byte[] data = new byte[(int) stateFile.length()];
            fis.read(data);
            fis.close();

            JSONObject state = new JSONObject(new String(data, StandardCharsets.UTF_8));

            // Load custom status (supports both old and new format)
            JSONObject customState = state.optJSONObject("custom");
            if (customState != null) {
                // New format: { custom: { active, priority, rpc } }
                if (customState.optBoolean("active", false)) {
                    int priority = customState.optInt("priority", 4);
                    JSONObject rpc = customState.getJSONObject("rpc");
                    sm.updatePersistentSource("custom", priority, rpc);
                    logger.info("[Bridge] Custom state restored from previous session");
                } else {
                    logger.info("[Bridge] Custom state: inactive");
                }
            } else if (state.has("active")) {
                // Old format: { active, priority, rpc } (from custom-state.json)
                if (state.optBoolean("active", false)) {
                    int priority = state.optInt("priority", 4);
                    JSONObject rpc = state.getJSONObject("rpc");
                    sm.updatePersistentSource("custom", priority, rpc);
                    logger.info("[Bridge] Custom state restored (old format)");
                } else {
                    logger.info("[Bridge] Custom state: inactive");
                }
            }

            return state;
        } catch (Exception e) {
            logger.severe("[Bridge] Error loading state: " + e.getMessage());
        }
        return null;
    }

    static boolean applyPresenceUpdate(SourceManager.UpdateResult result, Config config, DiscordRPC lib, DiscordEventHandlers handlers) {
        if (!result.changed) return true;

        if (result.activeSource != null) {
            String customCid = null;
            if (result.rpcData != null) {
                customCid = result.rpcData.optString("clientId", result.rpcData.optString("client_id", "")).trim();
            }
            if (customCid != null && !customCid.isEmpty()) {
                DiscordIPC.ensureClientId(result.activeSource, customCid);
            } else {
                DiscordIPC.ensureClientId(result.activeSource, config);
            }
            if (!DiscordIPC.isConnected()) {
                ensureClientId(lib, handlers, result.activeSource, config);
            }
        }

        if (DiscordIPC.isConnected()) {
            if (result.rpcData != null) {
                logger.info("[Bridge] Updating presence via DiscordIPC -> " + result.activeSource);
                return DiscordIPC.updatePresence(result.rpcData);
            } else {
                logger.info("[Bridge] Clearing presence via DiscordIPC (all sources expired/removed)");
                return DiscordIPC.clearPresence();
            }
        } else {
            lib.Discord_RunCallbacks();
            if (result.presence != null) {
                logger.info("[Bridge] Updating presence via Minnced fallback -> " + result.activeSource);
                lib.Discord_UpdatePresence(result.presence);
                return true;
            } else {
                logger.info("[Bridge] Clearing presence via Minnced fallback (all sources expired)");
                lib.Discord_ClearPresence();
                return true;
            }
        }
    }

    static void ensureClientId(DiscordRPC lib, DiscordEventHandlers handlers, String source, Config config) {
        String requiredId = config.clientId;
        if ("tetrio".equals(source) && config.tetrioClientId != null && !config.tetrioClientId.isEmpty()) {
            requiredId = config.tetrioClientId;
        } else if (("roblox".equals(source) || "roblox-desktop".equals(source)) && config.robloxClientId != null
                && !config.robloxClientId.isEmpty()) {
            requiredId = config.robloxClientId;
        } else if ("youtube".equals(source) && config.youtubeClientId != null && !config.youtubeClientId.isEmpty()) {
            requiredId = config.youtubeClientId;
        } else if ("wplace".equals(source) && config.wplaceClientId != null && !config.wplaceClientId.isEmpty()) {
            requiredId = config.wplaceClientId;
        } else if ("anime".equals(source) && config.animeClientId != null && !config.animeClientId.isEmpty()) {
            requiredId = config.animeClientId;
        }
        if (requiredId.equals(currentClientId)) {
            logger.info("[Bridge] Client ID unchanged, skipping Discord_Initialize.");
            return;
        }
        if (!currentClientId.isEmpty()) {
            logger.info("[Bridge] Shutting down previous Discord instance.");
            lib.Discord_Shutdown();
        }
        logger.info("[Bridge] Initializing Discord with clientId=" + requiredId);
        lib.Discord_Initialize(requiredId, handlers, true, "");
        currentClientId = requiredId;
        logger.info("[Bridge] Switched to client ID for: " + source);
    }
}
