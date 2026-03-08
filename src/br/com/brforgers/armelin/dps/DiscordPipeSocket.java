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

public class DiscordPipeSocket {

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

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception var12) {
            port = config.wsPort;
        }

        // Initialize SourceManager
        sourceManager = new SourceManager(config.sourceTimeout);

        // Restore bridge state from previous session
        final JSONObject savedState = loadBridgeState(sourceManager);

        final DiscordEventHandlers handlers = new DiscordEventHandlers();
        handlers.ready = (user) -> System.out.println("Welcome " + user.username + "#" + user.discriminator + ".");

        // Initialize Discord RPC once at startup if clientId is configured
        if (!config.clientId.isEmpty()) {
            lib.Discord_Initialize(config.clientId, handlers, true, "");
            currentClientId = config.clientId;
            lastid = config.clientId;
            System.out.println("[Bridge] Discord initialized with clientId from config.json");
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
                if (result.changed) {
                    if (result.activeSource != null) {
                        ensureClientId(lib, handlers, result.activeSource, config);
                    }
                    lib.Discord_RunCallbacks();
                    if (result.presence != null) {
                        lib.Discord_UpdatePresence(result.presence);
                    } else {
                        lib.Discord_ClearPresence();
                    }
                }
            } catch (Exception e) {
                System.err.println("[Bridge] Cleanup error: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Roblox Desktop Monitor — checks process every 10 seconds
        final RobloxMonitor robloxMonitor = new RobloxMonitor(sourceManager, 2);
        if (savedState != null && savedState.has("robloxMonitorEnabled")) {
            boolean rmEnabled = savedState.optBoolean("robloxMonitorEnabled", true);
            robloxMonitor.setEnabled(rmEnabled);
            System.out.println("[Bridge] Roblox Monitor restored: " + (rmEnabled ? "ON" : "OFF"));
        }
        scheduler.scheduleAtFixedRate(() -> {
            try {
                robloxMonitor.run();
            } catch (Exception e) {
                System.err.println("[RobloxMonitor] Scheduled error: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.SECONDS);
        System.out.println("[Bridge] Roblox Desktop Monitor active");

        WebSocketServer server = new WebSocketServer(new InetSocketAddress("localhost", port)) {
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                System.out.println("[Bridge] Client connected: " + conn.getRemoteSocketAddress());
            }

            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                System.out.println("[Bridge] Client disconnected (code: " + code + ")");
                // Do NOT call Discord_Shutdown() here — other sources may still be active.
                // Discord RPC stays alive; sources expire via SourceManager timeout.
            }

            public void onMessage(WebSocket conn, String message) {
                try {
                    JSONObject jsonObject = new JSONObject(message);

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
                        conn.send(response.toString());
                        return;
                    }

                    // New multi-source protocol: {source, priority, rpc}
                    if (jsonObject.has("source")) {
                        String source = jsonObject.getString("source");

                        // Handle remove action: {source, action: "remove"}
                        if (jsonObject.has("action") && "remove".equals(jsonObject.getString("action"))) {
                            System.out.println("[Bridge] Removing source: " + source);
                            sourceManager.removeSource(source);
                            SourceManager.UpdateResult result = sourceManager.checkForChanges();
                            if (result.changed) {
                                if (result.activeSource != null) {
                                    ensureClientId(lib, handlers, result.activeSource, config);
                                }
                                lib.Discord_RunCallbacks();
                                if (result.presence != null) {
                                    lib.Discord_UpdatePresence(result.presence);
                                } else {
                                    System.out.println("[Bridge] Clearing presence (source removed)");
                                    lib.Discord_ClearPresence();
                                }
                            }
                            return;
                        }

                        int priority = jsonObject.getInt("priority");
                        JSONObject rpc = jsonObject.getJSONObject("rpc");
                        boolean persistent = jsonObject.optBoolean("persistent", false);
                        System.out.println("[Bridge] Received from source: " + source + " (priority: " + priority + ", persistent: " + persistent + ")");

                        // Custom source must always be persistent (sent by legitimate HTML page).
                        // Reject phantom custom updates (e.g. cached tabs sending without persistent field).
                        if ("custom".equals(source) && !persistent) {
                            System.out.println("[Bridge] Ignoring non-persistent custom source (phantom client)");
                            return;
                        }

                        if (persistent) {
                            sourceManager.updatePersistentSource(source, priority, rpc);
                        } else {
                            sourceManager.updateSource(source, priority, rpc);
                        }

                        SourceManager.UpdateResult result = sourceManager.checkForChanges();
                        if (result.changed) {
                            if (result.activeSource != null) {
                                ensureClientId(lib, handlers, result.activeSource, config);
                            }
                            lib.Discord_RunCallbacks();
                            if (result.presence != null) {
                                System.out.println("[Bridge] Updating presence -> " + source);
                                lib.Discord_UpdatePresence(result.presence);
                            } else {
                                System.out.println("[Bridge] Clearing presence (all sources expired)");
                                lib.Discord_ClearPresence();
                            }
                        }
                        return;
                    }

                    // Legacy protocol: {cid, rpc}
                    if (!jsonObject.getString("cid").equals(DiscordPipeSocket.lastid)) {
                        if (DiscordPipeSocket.lastid == "") {
                            DiscordPipeSocket.lastid = jsonObject.getString("cid");
                            lib.Discord_Initialize(DiscordPipeSocket.lastid, handlers, true, "");
                        } else {
                            lib.Discord_Shutdown();
                            DiscordPipeSocket.lastid = jsonObject.getString("cid");
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
                } catch (Exception e) {
                    System.err.println("[Bridge] Error processing message: " + e.getMessage());
                }
            }

            public void onError(WebSocket conn, Exception ex) {
                System.err.println("[Bridge] WebSocket error: " + ex.getMessage());
            }

            public void onStart() {
                System.out.println("Websocket started on port: " + this.getPort());
            }
        };

        PopupMenu popMenu = new PopupMenu();
        MenuItem item1 = new MenuItem("Port: " + port);
        item1.setEnabled(false);
        MenuItem item2 = new MenuItem("Exit");
        item2.addActionListener((ex) -> {
            saveBridgeState(sourceManager, robloxMonitor);
            scheduler.shutdown();
            lib.Discord_Shutdown();
            System.exit(0);
        });
        MenuItem item3 = new MenuItem("Custom Status");
        item3.addActionListener((ex) -> {
            try {
                File htmlFile = new File("custom-status/index.html").getAbsoluteFile();
                if (!htmlFile.exists()) {
                    System.err.println("[Bridge] custom-status/index.html not found at: " + htmlFile.getPath());
                    return;
                }
                String url = htmlFile.toURI().toString();
                try {
                    Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "chrome", url});
                } catch (Exception e1) {
                    try {
                        Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "msedge", url});
                    } catch (Exception e2) {
                        Desktop.getDesktop().browse(htmlFile.toURI());
                    }
                }
            } catch (Exception e) {
                System.err.println("[Bridge] Could not open browser: " + e.getMessage());
            }
        });
        MenuItem item4 = new MenuItem("Roblox Monitor: " + (robloxMonitor.isEnabled() ? "ON" : "OFF"));
        item4.addActionListener((ex) -> {
            boolean nowEnabled = !robloxMonitor.isEnabled();
            robloxMonitor.setEnabled(nowEnabled);
            item4.setLabel("Roblox Monitor: " + (nowEnabled ? "ON" : "OFF"));
            System.out.println("[Bridge] Roblox Monitor " + (nowEnabled ? "enabled" : "disabled"));
            saveBridgeState(sourceManager, robloxMonitor);
        });
        popMenu.add(item1);
        popMenu.add(item3);
        popMenu.add(item4);
        popMenu.addSeparator();
        popMenu.add(item2);
        BufferedImage img = ImageIO.read(DiscordPipeSocket.class.getResource("/pipe_right_small.png"));
        int trayiconw = (new TrayIcon(img)).getSize().width;
        TrayIcon trayIcon = new TrayIcon(img.getScaledInstance(trayiconw, -1, 4), "Discord Pipe Socket", popMenu);
        SystemTray.getSystemTray().add(trayIcon);
        server.run();
    }

    static File getJarDir() {
        try {
            return new File(
                DiscordPipeSocket.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParentFile();
        } catch (Exception e) {
            return new File(".");
        }
    }

    static void saveBridgeState(SourceManager sm, RobloxMonitor rm) {
        try {
            File stateFile = new File(getJarDir(), "bridge-state.json");
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
            System.out.println("[Bridge] State saved (custom: " + (custom != null) + ", robloxMonitor: " + rm.isEnabled() + ")");
        } catch (Exception e) {
            System.err.println("[Bridge] Error saving state: " + e.getMessage());
        }
    }

    static JSONObject loadBridgeState(SourceManager sm) {
        try {
            // Try new format first
            File stateFile = new File(getJarDir(), "bridge-state.json");

            // Migrate from old custom-state.json if it exists
            if (!stateFile.exists()) {
                File oldFile = new File(getJarDir(), "custom-state.json");
                if (oldFile.exists()) {
                    oldFile.renameTo(stateFile);
                    System.out.println("[Bridge] Migrated custom-state.json -> bridge-state.json");
                }
            }

            if (!stateFile.exists()) return null;

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
                    System.out.println("[Bridge] Custom state restored from previous session");
                } else {
                    System.out.println("[Bridge] Custom state: inactive");
                }
            } else if (state.has("active")) {
                // Old format: { active, priority, rpc } (from custom-state.json)
                if (state.optBoolean("active", false)) {
                    int priority = state.optInt("priority", 4);
                    JSONObject rpc = state.getJSONObject("rpc");
                    sm.updatePersistentSource("custom", priority, rpc);
                    System.out.println("[Bridge] Custom state restored (old format)");
                } else {
                    System.out.println("[Bridge] Custom state: inactive");
                }
            }

            return state;
        } catch (Exception e) {
            System.err.println("[Bridge] Error loading state: " + e.getMessage());
        }
        return null;
    }

    static void ensureClientId(DiscordRPC lib, DiscordEventHandlers handlers, String source, Config config) {
        String requiredId = config.clientId;
        if ("tetrio".equals(source) && config.tetrioClientId != null && !config.tetrioClientId.isEmpty()) {
            requiredId = config.tetrioClientId;
        }
        if (requiredId.equals(currentClientId)) return;
        if (!currentClientId.isEmpty()) {
            lib.Discord_Shutdown();
        }
        lib.Discord_Initialize(requiredId, handlers, true, "");
        currentClientId = requiredId;
        System.out.println("[Bridge] Switched to client ID for: " + source);
    }
}
