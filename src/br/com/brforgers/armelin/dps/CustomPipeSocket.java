package br.com.brforgers.armelin.dps;

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
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONObject;

/**
 * CustomPipeSocket
 * Standalone Discord Rich Presence bridge focused solely on Custom Status.
 * Operates without background scrapers, external browser extension sockets,
 * or game monitors (Roblox/Tetr.io/YouTube).
 */
public class CustomPipeSocket {
    private static final Logger logger = Logger.getLogger("DPS-Custom");

    private static boolean active = false;
    private static JSONObject currentRpc = null;

    public static void main(String[] args) throws AWTException, IOException {
        final Config config = Config.load();
        LoggerConfig.setup(config.logFile);

        logger.info("==========================================");
        logger.info(" Starting Discord Custom Status Bridge   ");
        logger.info(" Mode: Custom-Only (Standalone)          ");
        logger.info("==========================================");

        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception e) {
            port = config.wsPort;
        }

        // Port check to avoid collisions
        try (java.net.ServerSocket ss = new java.net.ServerSocket(port)) {
            // port is available
        } catch (Exception e) {
            logger.severe("[CustomBridge] Port " + port + " is already in use! Another instance is running. Exiting.");
            System.exit(1);
        }

        // Initialize Discord IPC if clientId is present
        if (config.clientId != null && !config.clientId.isEmpty()) {
            boolean connected = DiscordIPC.connect(config.clientId);
            if (connected) {
                logger.info("[CustomBridge] DiscordIPC connected successfully (ClientID: " + config.clientId + ")");
            } else {
                logger.warning("[CustomBridge] DiscordIPC could not connect at startup. Will retry when presence is sent.");
            }
        }

        // Restore saved custom presence from bridge-state.json
        loadState();
        if (active && currentRpc != null) {
            logger.info("[CustomBridge] Restoring previous custom status...");
            DiscordIPC.updatePresence(currentRpc);
        }

        // Ensure custom-status UI files are present or extracted at startup
        ensureHtmlFile();

        // WebSocket Server for custom-status frontend
        WebSocketServer server = new WebSocketServer(new InetSocketAddress("localhost", port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                logger.info("[CustomBridge] Web UI connected: " + conn.getRemoteSocketAddress());
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                logger.info("[CustomBridge] Web UI disconnected (code: " + code + ")");
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                try {
                    JSONObject json = new JSONObject(message);

                    // 1. Query State action
                    if (json.has("action") && "query".equals(json.getString("action"))) {
                        JSONObject response = new JSONObject();
                        response.put("type", "queryResponse");
                        response.put("source", "custom");
                        response.put("active", active);
                        response.put("persistent", true);
                        if (currentRpc != null) {
                            response.put("rpc", currentRpc);
                        }

                        JSONObject configIds = new JSONObject();
                        if (config.clientId != null && !config.clientId.isEmpty()) {
                            configIds.put("Default / General", config.clientId);
                        }
                        response.put("configClientIds", configIds);
                        response.put("currentClientId", DiscordIPC.getCurrentClientId());

                        conn.send(response.toString());
                        return;
                    }

                    // 2. Remove Custom Presence action
                    if (json.has("action") && "remove".equals(json.getString("action"))) {
                        logger.info("[CustomBridge] Removing custom presence");
                        active = false;
                        currentRpc = null;
                        boolean cleared = DiscordIPC.clearPresence();
                        saveState();

                        JSONObject resp = new JSONObject();
                        resp.put("type", "presenceUpdateResult");
                        resp.put("success", cleared);
                        resp.put("clientId", DiscordIPC.getCurrentClientId());
                        conn.send(resp.toString());
                        return;
                    }

                    // 2.5 Save Draft Configuration without activating
                    if (json.has("action") && "save".equals(json.getString("action"))) {
                        if (json.has("rpc")) {
                            currentRpc = json.getJSONObject("rpc");
                            saveState();
                            logger.info("[CustomBridge] Draft configuration saved to bridge-state.json (active=" + active + ")");
                        }
                        JSONObject resp = new JSONObject();
                        resp.put("type", "saveResult");
                        resp.put("success", true);
                        conn.send(resp.toString());
                        return;
                    }

                    // 3. Update Custom Presence
                    if (json.has("rpc")) {
                        JSONObject rpc = json.getJSONObject("rpc");
                        currentRpc = rpc;
                        active = true;

                        logger.info("[CustomBridge] Applying custom presence update");
                        boolean ok = DiscordIPC.updatePresence(rpc);
                        saveState();

                        JSONObject resp = new JSONObject();
                        resp.put("type", "presenceUpdateResult");
                        resp.put("success", ok);
                        resp.put("clientId", DiscordIPC.getCurrentClientId());
                        conn.send(resp.toString());
                    }
                } catch (Exception e) {
                    logger.severe("[CustomBridge] Error processing message: " + e.getMessage());
                }
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                logger.severe("[CustomBridge] WebSocket error: " + ex.getMessage());
            }

            @Override
            public void onStart() {
                logger.info("[CustomBridge] WebSocket started on port: " + this.getPort());
            }
        };

        // System Tray Configuration
        final TrayIcon[] trayRef = new TrayIcon[1];
        PopupMenu popup = new PopupMenu();

        MenuItem itemOpen = new MenuItem("Abrir Custom Status");
        itemOpen.addActionListener((e) -> openCustomStatusInBrowser());

        MenuItem itemPort = new MenuItem("Puerto: " + port);
        itemPort.setEnabled(false);

        MenuItem itemExit = new MenuItem("Salir");
        itemExit.addActionListener((e) -> {
            try {
                if (trayRef[0] != null && SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(trayRef[0]);
                }
            } catch (Throwable ignored) {}

            new Thread(() -> {
                try {
                    saveState();
                } catch (Throwable ignored) {}
                try {
                    DiscordIPC.disconnect();
                } catch (Throwable ignored) {}
                System.exit(0);
            }, "CustomBridge-Exit").start();
        });

        popup.add(itemOpen);
        popup.add(itemPort);
        popup.addSeparator();
        popup.add(itemExit);

        if (SystemTray.isSupported()) {
            try {
                BufferedImage img = null;
                // Try to load custom icon or pipe icon
                try {
                    File customImg = new File("img/Custom.png");
                    if (customImg.exists()) {
                        img = ImageIO.read(customImg);
                    }
                } catch (Exception ignored) {}

                if (img == null) {
                    try {
                        img = ImageIO.read(CustomPipeSocket.class.getResource("/pipe_right_small.png"));
                    } catch (Exception ignored) {}
                }

                if (img != null) {
                    int trayWidth = (new TrayIcon(img)).getSize().width;
                    TrayIcon trayIcon = new TrayIcon(img.getScaledInstance(trayWidth, -1, 4),
                            "Discord Custom Status Bridge", popup);
                    trayIcon.addActionListener((e) -> openCustomStatusInBrowser());
                    SystemTray.getSystemTray().add(trayIcon);
                    trayRef[0] = trayIcon;
                }
            } catch (Exception e) {
                logger.warning("[CustomBridge] Could not setup System Tray: " + e.getMessage());
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (trayRef[0] != null && SystemTray.isSupported()) {
                    SystemTray.getSystemTray().remove(trayRef[0]);
                }
            } catch (Throwable ignored) {}
        }));

        server.run();
    }

    private static void openCustomStatusInBrowser() {
        try {
            File htmlFile = ensureHtmlFile();
            if (htmlFile == null || !htmlFile.exists()) {
                logger.severe("[CustomBridge] custom-status/index.html could not be located or extracted.");
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
        } catch (Exception ex) {
            logger.severe("[CustomBridge] Could not open browser: " + ex.getMessage());
        }
    }

    private static File ensureHtmlFile() {
        // 1. Look next to the running JAR
        File jarHtml = new File(getJarDir(), "custom-status/index.html");
        if (jarHtml.exists()) {
            return jarHtml;
        }

        // 2. Look in the current working directory
        File cwdHtml = new File("custom-status/index.html").getAbsoluteFile();
        if (cwdHtml.exists()) {
            return cwdHtml;
        }

        // 3. Fallback: extract embedded files from the JAR
        try {
            File targetDir = new File(getJarDir(), "custom-status");
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                // If directory next to JAR is not writable, fallback to system temp folder
                targetDir = new File(System.getProperty("java.io.tmpdir"), "discord-custom-status");
                targetDir.mkdirs();
            }

            extractResource("/custom-status/index.html", new File(targetDir, "index.html"));
            extractResource("/custom-status/app.js", new File(targetDir, "app.js"));
            extractResource("/custom-status/style.css", new File(targetDir, "style.css"));

            File extractedIndex = new File(targetDir, "index.html");
            if (extractedIndex.exists()) {
                logger.info("[CustomBridge] Extracted embedded UI to: " + extractedIndex.getAbsolutePath());
                return extractedIndex;
            }
        } catch (Exception e) {
            logger.warning("[CustomBridge] Error extracting embedded UI: " + e.getMessage());
        }

        return null;
    }

    private static void extractResource(String resourcePath, File destination) {
        try (java.io.InputStream in = CustomPipeSocket.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                logger.warning("[CustomBridge] Embedded resource not found in JAR: " + resourcePath);
                return;
            }
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(destination)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            logger.warning("[CustomBridge] Failed to extract " + resourcePath + ": " + e.getMessage());
        }
    }

    private static File getJarDir() {
        try {
            return new File(CustomPipeSocket.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParentFile();
        } catch (Exception e) {
            return new File(".");
        }
    }

    private static void saveState() {
        try {
            File stateFile = new File(getJarDir(), "bridge-state.json");
            JSONObject state = new JSONObject();

            JSONObject customState = new JSONObject();
            customState.put("active", active);
            customState.put("priority", 4);
            if (currentRpc != null) {
                customState.put("rpc", currentRpc);
            }
            state.put("custom", customState);

            FileWriter writer = new FileWriter(stateFile);
            writer.write(state.toString(2));
            writer.close();
            logger.info("[CustomBridge] State saved (active: " + active + ")");
        } catch (Exception e) {
            logger.severe("[CustomBridge] Error saving state: " + e.getMessage());
        }
    }

    private static void loadState() {
        try {
            File stateFile = new File(getJarDir(), "bridge-state.json");
            if (!stateFile.exists()) {
                File oldFile = new File(getJarDir(), "custom-state.json");
                if (oldFile.exists()) {
                    oldFile.renameTo(stateFile);
                }
            }
            if (!stateFile.exists()) return;

            FileInputStream fis = new FileInputStream(stateFile);
            byte[] data = new byte[(int) stateFile.length()];
            fis.read(data);
            fis.close();

            JSONObject state = new JSONObject(new String(data, StandardCharsets.UTF_8));
            JSONObject custom = state.optJSONObject("custom");
            if (custom != null) {
                active = custom.optBoolean("active", false);
                if (custom.has("rpc")) {
                    currentRpc = custom.getJSONObject("rpc");
                }
            } else if (state.has("active")) {
                active = state.optBoolean("active", false);
                if (state.has("rpc")) {
                    currentRpc = state.getJSONObject("rpc");
                }
            }
            logger.info("[CustomBridge] Loaded previous state: active=" + active);
        } catch (Exception e) {
            logger.warning("[CustomBridge] Could not load state: " + e.getMessage());
        }
    }
}
