package br.com.brforgers.armelin.dps;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Direct Discord IPC Pipe Client (Windows / Named Pipe)
 *
 * Connects directly to \\.\pipe\discord-ipc-0 .. discord-ipc-9 without
 * using legacy C library bindings. This natively supports:
 * - Activity Type (Playing=0, Listening=2, Watching=3, Competing=5)
 * - Clickable Buttons (up to 2 buttons with label and URL)
 * - HTTPS Image URLs for large and small assets
 * - Automatic client ID switching and reconnection
 */
public class DiscordIPC {
    private static final Logger logger = Logger.getLogger("DPS");

    private static RandomAccessFile pipe = null;
    private static volatile boolean connected = false;
    private static String currentClientId = "";
    private static Thread readerThread = null;
    private static final int processId = getProcessId();

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;

    private static int getProcessId() {
        try {
            String jvmName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
            int index = jvmName.indexOf('@');
            if (index > 0) {
                return Integer.parseInt(jvmName.substring(0, index));
            }
        } catch (Exception ignored) { }
        return 1000 + (int) (Math.random() * 50000);
    }

    public static synchronized boolean isConnected() {
        return connected && pipe != null;
    }

    public static synchronized String getCurrentClientId() {
        return currentClientId;
    }

    /**
     * Resolves the required client ID for a given source using Config.
     */
    public static String resolveClientId(String source, Config config) {
        String requiredId = config.clientId;
        if ("tetrio".equals(source) && config.tetrioClientId != null && !config.tetrioClientId.isEmpty()) {
            requiredId = config.tetrioClientId;
        } else if (("roblox".equals(source) || "roblox-desktop".equals(source))
                && config.robloxClientId != null && !config.robloxClientId.isEmpty()) {
            requiredId = config.robloxClientId;
        } else if ("youtube".equals(source) && config.youtubeClientId != null && !config.youtubeClientId.isEmpty()) {
            requiredId = config.youtubeClientId;
        } else if ("wplace".equals(source) && config.wplaceClientId != null && !config.wplaceClientId.isEmpty()) {
            requiredId = config.wplaceClientId;
        } else if ("anime".equals(source) && config.animeClientId != null && !config.animeClientId.isEmpty()) {
            requiredId = config.animeClientId;
        }
        return requiredId;
    }

    /**
     * Ensures Discord IPC is connected with the appropriate client ID for the active source.
     */
    public static synchronized void ensureClientId(String source, Config config) {
        String requiredId = resolveClientId(source, config);
        if (requiredId.isEmpty()) {
            return;
        }
        if (requiredId.equals(currentClientId) && isConnected()) {
            return;
        }
        logger.info("[DiscordIPC] Switching client ID for source '" + source + "' -> " + requiredId);
        disconnect();
        connect(requiredId);
    }

    /**
     * Connects to Discord IPC named pipe with the specified clientId.
     */
    public static synchronized boolean connect(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            return false;
        }
        if (isConnected() && clientId.equals(currentClientId)) {
            return true;
        }

        disconnect();
        currentClientId = clientId;

        // Scan named pipes discord-ipc-0 through discord-ipc-9 (Windows)
        for (int i = 0; i < 10; i++) {
            try {
                File pipeFile = new File("\\\\.\\pipe\\discord-ipc-" + i);
                RandomAccessFile raf = new RandomAccessFile(pipeFile, "rw");
                pipe = raf;

                // Send Handshake (Opcode 0)
                JSONObject handshake = new JSONObject();
                handshake.put("v", 1);
                handshake.put("client_id", clientId);

                sendPacket(OP_HANDSHAKE, handshake.toString());
                connected = true;

                // Start reader thread to drain responses and detect closure
                readerThread = new Thread(() -> readLoop(raf), "DiscordIPC-Reader");
                readerThread.setDaemon(true);
                readerThread.start();

                logger.info("[DiscordIPC] Connected to pipe discord-ipc-" + i + " with clientId=" + clientId);
                return true;
            } catch (Exception e) {
                // Pipe not available, continue scanning
            }
        }

        logger.severe("[DiscordIPC] Could not connect to any Discord IPC pipe (is Discord running?)");
        connected = false;
        return false;
    }

    /**
     * Background thread reading responses from the pipe.
     */
    private static void readLoop(RandomAccessFile raf) {
        byte[] header = new byte[8];
        try {
            while (!Thread.currentThread().isInterrupted()) {
                raf.readFully(header);
                int opcode = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8) | ((header[2] & 0xFF) << 16) | ((header[3] & 0xFF) << 24);
                int length = (header[4] & 0xFF) | ((header[5] & 0xFF) << 8) | ((header[6] & 0xFF) << 16) | ((header[7] & 0xFF) << 24);

                if (length > 0) {
                    byte[] body = new byte[length];
                    raf.readFully(body);
                    String json = new String(body, StandardCharsets.UTF_8);

                    if (opcode == OP_FRAME) {
                        try {
                            JSONObject obj = new JSONObject(json);
                            if ("DISPATCH".equals(obj.optString("cmd")) && "READY".equals(obj.optString("evt"))) {
                                JSONObject user = obj.optJSONObject("data") != null ? obj.getJSONObject("data").optJSONObject("user") : null;
                                if (user != null) {
                                    logger.info("[DiscordIPC] Handshake OK. Welcome " + user.optString("username", "user") + ".");
                                }
                            } else if ("ERROR".equals(obj.optString("evt"))) {
                                logger.severe("[DiscordIPC] Error from Discord: " + obj.optString("data"));
                            }
                        } catch (Exception ignored) { }
                    } else if (opcode == OP_PING) {
                        sendPacket(OP_PONG, json);
                    } else if (opcode == OP_CLOSE) {
                        logger.info("[DiscordIPC] Received close from Discord: " + json);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Pipe disconnected or closed
        } finally {
            synchronized (DiscordIPC.class) {
                if (pipe == raf) {
                    connected = false;
                    try { raf.close(); } catch (Exception ignored) { }
                    pipe = null;
                }
            }
        }
    }

    /**
     * Sends a raw opcode + length + json packet to the pipe.
     */
    private static synchronized void sendPacket(int opcode, String json) throws IOException {
        if (pipe == null) {
            throw new IOException("Discord IPC pipe not connected");
        }
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        byte[] header = new byte[8];

        header[0] = (byte) (opcode & 0xFF);
        header[1] = (byte) ((opcode >> 8) & 0xFF);
        header[2] = (byte) ((opcode >> 16) & 0xFF);
        header[3] = (byte) ((opcode >> 24) & 0xFF);

        int len = data.length;
        header[4] = (byte) (len & 0xFF);
        header[5] = (byte) ((len >> 8) & 0xFF);
        header[6] = (byte) ((len >> 16) & 0xFF);
        header[7] = (byte) ((len >> 24) & 0xFF);

        pipe.write(header);
        pipe.write(data);
    }

    /**
     * Updates presence using raw JSON rpcData from SourceManager / Custom status / Userscripts.
     */
    public static synchronized boolean updatePresence(JSONObject rpc) {
        if (rpc == null) {
            return clearPresence();
        }

        if (!isConnected()) {
            if (!currentClientId.isEmpty()) {
                connect(currentClientId);
            }
            if (!isConnected()) {
                return false;
            }
        }

        try {
            JSONObject activity = new JSONObject();

            // 1. Details & State
            if (rpc.has("details") && !rpc.isNull("details")) {
                String d = rpc.getString("details").trim();
                if (!d.isEmpty()) activity.put("details", truncate(d, 128));
            }
            if (rpc.has("state") && !rpc.isNull("state")) {
                String s = rpc.getString("state").trim();
                if (!s.isEmpty()) activity.put("state", truncate(s, 128));
            }

            // 2. Activity Type (0 = Playing, 2 = Listening, 3 = Watching, 5 = Competing)
            int type = 0;
            if (rpc.has("type") && !rpc.isNull("type")) {
                type = rpc.getInt("type");
            } else if (rpc.has("activityType") && !rpc.isNull("activityType")) {
                String at = rpc.getString("activityType").toLowerCase().trim();
                if ("listening".equals(at)) type = 2;
                else if ("watching".equals(at)) type = 3;
                else if ("competing".equals(at)) type = 5;
                else type = 0;
            }
            activity.put("type", type);

            // 3. Timestamps (seconds)
            JSONObject timestamps = new JSONObject();
            if (rpc.has("startTimestamp") && !rpc.isNull("startTimestamp")) {
                long start = parseTimestamp(rpc.get("startTimestamp"));
                if (start > 0) timestamps.put("start", start);
            }
            if (rpc.has("endTimestamp") && !rpc.isNull("endTimestamp")) {
                long end = parseTimestamp(rpc.get("endTimestamp"));
                if (end > 0) timestamps.put("end", end);
            }
            if (timestamps.length() > 0) {
                activity.put("timestamps", timestamps);
            }

            // 4. Assets (large_image, large_text, small_image, small_text)
            JSONObject assets = new JSONObject();
            if (rpc.has("largeImageKey") && !rpc.isNull("largeImageKey")) {
                String k = rpc.getString("largeImageKey").trim();
                if (!k.isEmpty()) assets.put("large_image", k);
            }
            if (rpc.has("largeImageText") && !rpc.isNull("largeImageText")) {
                String t = rpc.getString("largeImageText").trim();
                if (!t.isEmpty()) assets.put("large_text", truncate(t, 128));
            }
            if (rpc.has("smallImageKey") && !rpc.isNull("smallImageKey")) {
                String k = rpc.getString("smallImageKey").trim();
                if (!k.isEmpty()) assets.put("small_image", k);
            }
            if (rpc.has("smallImageText") && !rpc.isNull("smallImageText")) {
                String t = rpc.getString("smallImageText").trim();
                if (!t.isEmpty()) assets.put("small_text", truncate(t, 128));
            }
            if (assets.length() > 0) {
                activity.put("assets", assets);
            }

            // 5. Party
            if (rpc.has("partyId") && !rpc.isNull("partyId")) {
                JSONObject party = new JSONObject();
                party.put("id", rpc.getString("partyId"));
                if (rpc.has("partySize") && rpc.has("partyMax") && !rpc.isNull("partySize") && !rpc.isNull("partyMax")) {
                    JSONArray sizeArr = new JSONArray();
                    sizeArr.put(rpc.getInt("partySize"));
                    sizeArr.put(rpc.getInt("partyMax"));
                    party.put("size", sizeArr);
                }
                activity.put("party", party);
            }

            // 6. Buttons (up to 2 buttons with label & url)
            if (rpc.has("buttons") && !rpc.isNull("buttons")) {
                JSONArray rawButtons = rpc.getJSONArray("buttons");
                JSONArray buttons = new JSONArray();
                for (int i = 0; i < Math.min(rawButtons.length(), 2); i++) {
                    JSONObject b = rawButtons.getJSONObject(i);
                    String label = b.optString("label", "").trim();
                    String url = b.optString("url", "").trim();
                    if (!label.isEmpty() && !url.isEmpty()) {
                        JSONObject btnObj = new JSONObject();
                        btnObj.put("label", truncate(label, 32));
                        btnObj.put("url", truncate(url, 512));
                        buttons.put(btnObj);
                    }
                }
                if (buttons.length() > 0) {
                    activity.put("buttons", buttons);
                }
            }

            // Frame payload
            JSONObject frame = new JSONObject();
            frame.put("cmd", "SET_ACTIVITY");
            JSONObject args = new JSONObject();
            args.put("pid", processId);
            args.put("activity", activity);
            frame.put("args", args);
            frame.put("nonce", UUID.randomUUID().toString());

            sendPacket(OP_FRAME, frame.toString());
            return true;
        } catch (Exception e) {
            logger.severe("[DiscordIPC] Error sending presence: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Clears presence on Discord.
     */
    public static synchronized boolean clearPresence() {
        if (!isConnected()) {
            return false;
        }
        try {
            JSONObject frame = new JSONObject();
            frame.put("cmd", "SET_ACTIVITY");
            JSONObject args = new JSONObject();
            args.put("pid", processId);
            args.put("activity", JSONObject.NULL);
            frame.put("args", args);
            frame.put("nonce", UUID.randomUUID().toString());

            sendPacket(OP_FRAME, frame.toString());
            return true;
        } catch (Exception e) {
            logger.severe("[DiscordIPC] Error clearing presence: " + e.getMessage());
            return false;
        }
    }

    /**
     * Closes the IPC pipe connection.
     */
    public static synchronized void disconnect() {
        connected = false;
        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }
        if (pipe != null) {
            try {
                pipe.close();
            } catch (Exception ignored) { }
            pipe = null;
        }
    }

    private static String truncate(String val, int maxLen) {
        if (val == null) return "";
        return val.length() > maxLen ? val.substring(0, maxLen) : val;
    }

    private static long parseTimestamp(Object val) {
        if (val == null) return 0;
        try {
            long ms;
            if (val instanceof Number) {
                ms = ((Number) val).longValue();
            } else {
                ms = Long.parseLong(val.toString().trim());
            }
            if (ms <= 0) return 0;
            return ms > 100000000000L ? (ms / 1000) : ms;
        } catch (Exception e) {
            return 0;
        }
    }
}
