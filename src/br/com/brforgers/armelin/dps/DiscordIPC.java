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
     * Ensures Discord IPC is connected with the specified client ID.
     */
    public static synchronized void ensureClientId(String source, String requiredId) {
        if (requiredId == null || requiredId.trim().isEmpty()) {
            return;
        }
        requiredId = requiredId.trim();
        if (requiredId.equals(currentClientId) && isConnected()) {
            return;
        }
        logger.info("[DiscordIPC] Switching client ID for source '" + source + "' -> " + requiredId);
        disconnect();
        connect(requiredId);
    }

    /**
     * Ensures Discord IPC is connected with the appropriate client ID for the active source.
     */
    public static synchronized void ensureClientId(String source, Config config) {
        ensureClientId(source, resolveClientId(source, config));
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

                JSONObject resp = sendAndReceive(OP_HANDSHAKE, handshake.toString());
                if (resp != null && "DISPATCH".equals(resp.optString("cmd")) && "READY".equals(resp.optString("evt"))) {
                    JSONObject user = resp.optJSONObject("data") != null ? resp.getJSONObject("data").optJSONObject("user") : null;
                    if (user != null) {
                        logger.info("[DiscordIPC] Handshake OK. Welcome " + user.optString("username", "user") + ".");
                    }
                }

                connected = true;
                logger.info("[DiscordIPC] Connected to pipe discord-ipc-" + i + " with clientId=" + clientId);
                return true;
            } catch (Exception e) {
                if (pipe != null) {
                    try { pipe.close(); } catch (Exception ignored) { }
                    pipe = null;
                }
            }
        }

        logger.severe("[DiscordIPC] Could not connect to any Discord IPC pipe (is Discord running?)");
        connected = false;
        return false;
    }

    /**
     * Sends a raw packet and synchronously reads the response from Discord.
     */
    private static synchronized JSONObject sendAndReceive(int opcode, String json) throws IOException {
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

        // Read response header
        byte[] respHeader = new byte[8];
        pipe.readFully(respHeader);
        int respLen = (respHeader[4] & 0xFF) | ((respHeader[5] & 0xFF) << 8) | ((respHeader[6] & 0xFF) << 16) | ((respHeader[7] & 0xFF) << 24);

        if (respLen > 0) {
            byte[] body = new byte[respLen];
            pipe.readFully(body);
            String respJson = new String(body, StandardCharsets.UTF_8);
            try {
                return new JSONObject(respJson);
            } catch (Exception ignored) { }
        }
        return null;
    }

    /**
     * Updates presence using raw JSON rpcData from SourceManager / Custom status / Userscripts.
     */
    public static synchronized boolean updatePresence(JSONObject rpc) {
        if (rpc == null) {
            return clearPresence();
        }

        // Ensure custom clientId if specified in rpc
        String customCid = rpc.optString("clientId", rpc.optString("client_id", "")).trim();
        if (!customCid.isEmpty()) {
            if (!customCid.equals(currentClientId) || !isConnected()) {
                ensureClientId("custom", customCid);
            }
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

            // 1. Details & State with optional URLs
            if (rpc.has("details") && !rpc.isNull("details")) {
                String d = rpc.getString("details").trim();
                if (!d.isEmpty()) activity.put("details", truncate(d, 128));
            }
            if (rpc.has("details_url") && !rpc.isNull("details_url")) {
                String u = rpc.getString("details_url").trim();
                if (!u.isEmpty()) activity.put("details_url", u);
            }
            if (rpc.has("state") && !rpc.isNull("state")) {
                String s = rpc.getString("state").trim();
                if (!s.isEmpty()) activity.put("state", truncate(s, 128));
            }
            if (rpc.has("state_url") && !rpc.isNull("state_url")) {
                String u = rpc.getString("state_url").trim();
                if (!u.isEmpty()) activity.put("state_url", u);
            }

            // Name override
            if (rpc.has("name") && !rpc.isNull("name")) {
                String n = rpc.getString("name").trim();
                if (!n.isEmpty()) activity.put("name", n);
            }

            // 2. Activity Type (0 = Playing, 2 = Listening, 3 = Watching, 5 = Competing)
            // Note: Discord Desktop local RPC strictly rejects type 1 (Streaming) with error 4000: ["type" must be one of [0, 2, 3, 5]]
            int type = 0;
            if (rpc.has("type") && !rpc.isNull("type")) {
                type = rpc.getInt("type");
            } else if (rpc.has("activityType") && !rpc.isNull("activityType")) {
                String at = rpc.getString("activityType").toLowerCase().trim();
                if ("listening".equals(at)) type = 2;
                else if ("watching".equals(at)) type = 3;
                else if ("competing".equals(at)) type = 5;
                else type = 0;
            } else if (rpc.has("activity_type") && !rpc.isNull("activity_type")) {
                type = rpc.getInt("activity_type");
            }

            if (type != 0 && type != 2 && type != 3 && type != 5) {
                logger.warning("[DiscordIPC] Activity type " + type + " is not supported by Discord local RPC (allowed: [0, 2, 3, 5]). Falling back to 0 (Playing).");
                type = 0;
            }
            activity.put("type", type);

            // Stream URL (Discord requires a valid stream URL like Twitch/YouTube when activity_type is 1 / Streaming)
            if (rpc.has("url") && !rpc.isNull("url")) {
                String u = rpc.getString("url").trim();
                if (!u.isEmpty()) activity.put("url", u);
            } else if (rpc.has("stream_url") && !rpc.isNull("stream_url")) {
                String u = rpc.getString("stream_url").trim();
                if (!u.isEmpty()) activity.put("url", u);
            }

            // Status Display Type (0 = Name, 1 = State, 2 = Details)
            if (rpc.has("status_display_type") && !rpc.isNull("status_display_type")) {
                activity.put("status_display_type", rpc.getInt("status_display_type"));
            }

            // 3. Timestamps (seconds)
            JSONObject timestamps = new JSONObject();
            if (rpc.has("startTimestamp") && !rpc.isNull("startTimestamp")) {
                long start = parseTimestamp(rpc.get("startTimestamp"));
                if (start > 0) timestamps.put("start", start);
            } else if (rpc.has("start") && !rpc.isNull("start")) {
                long start = parseTimestamp(rpc.get("start"));
                if (start > 0) timestamps.put("start", start);
            }
            if (rpc.has("endTimestamp") && !rpc.isNull("endTimestamp")) {
                long end = parseTimestamp(rpc.get("endTimestamp"));
                if (end > 0) timestamps.put("end", end);
            } else if (rpc.has("end") && !rpc.isNull("end")) {
                long end = parseTimestamp(rpc.get("end"));
                if (end > 0) timestamps.put("end", end);
            }
            if (timestamps.length() > 0) {
                activity.put("timestamps", timestamps);
            }

            // 4. Assets (large_image, large_text, large_url, small_image, small_text, small_url)
            JSONObject assets = new JSONObject();
            String largeKey = rpc.optString("largeImageKey", rpc.optString("large_image", "")).trim();
            String largeText = rpc.optString("largeImageText", rpc.optString("large_text", "")).trim();
            String largeUrl = rpc.optString("large_url", "").trim();
            String smallKey = rpc.optString("smallImageKey", rpc.optString("small_image", "")).trim();
            String smallText = rpc.optString("smallImageText", rpc.optString("small_text", "")).trim();
            String smallUrl = rpc.optString("small_url", "").trim();

            if (!largeKey.isEmpty()) assets.put("large_image", largeKey);
            if (!largeText.isEmpty()) assets.put("large_text", truncate(largeText, 128));
            if (!largeUrl.isEmpty()) assets.put("large_url", largeUrl);
            if (!smallKey.isEmpty()) assets.put("small_image", smallKey);
            if (!smallText.isEmpty()) assets.put("small_text", truncate(smallText, 128));
            if (!smallUrl.isEmpty()) assets.put("small_url", smallUrl);
            if (assets.length() > 0) {
                activity.put("assets", assets);
            }

            // 5. Party (id, size [cur, max])
            String pid = rpc.optString("partyId", rpc.optString("party_id", "")).trim();
            if (!pid.isEmpty() || rpc.has("partySize") || rpc.has("party_size")) {
                JSONObject party = new JSONObject();
                if (!pid.isEmpty()) party.put("id", pid);
                if (rpc.has("partySize") && rpc.has("partyMax") && !rpc.isNull("partySize") && !rpc.isNull("partyMax")) {
                    JSONArray sizeArr = new JSONArray();
                    sizeArr.put(rpc.getInt("partySize"));
                    sizeArr.put(rpc.getInt("partyMax"));
                    party.put("size", sizeArr);
                } else if (rpc.has("party_size") && !rpc.isNull("party_size")) {
                    party.put("size", rpc.getJSONArray("party_size"));
                }
                if (party.length() > 0) {
                    activity.put("party", party);
                }
            }

            // 6. Secrets (join, spectate, match)
            JSONObject secrets = new JSONObject();
            if (rpc.has("join") && !rpc.isNull("join")) {
                String j = rpc.getString("join").trim();
                if (!j.isEmpty()) secrets.put("join", j);
            }
            if (rpc.has("spectate") && !rpc.isNull("spectate")) {
                String s = rpc.getString("spectate").trim();
                if (!s.isEmpty()) secrets.put("spectate", s);
            }
            if (rpc.has("match") && !rpc.isNull("match")) {
                String m = rpc.getString("match").trim();
                if (!m.isEmpty()) secrets.put("match", m);
            }
            if (secrets.length() > 0) {
                activity.put("secrets", secrets);
            }

            // 7. Instance
            if (rpc.has("instance") && !rpc.isNull("instance")) {
                activity.put("instance", rpc.getBoolean("instance"));
            }

            // 8. Buttons (up to 2 buttons with label & url)
            if (rpc.has("buttons") && !rpc.isNull("buttons")) {
                JSONArray rawButtons = rpc.getJSONArray("buttons");
                JSONArray buttons = new JSONArray();
                for (int i = 0; i < Math.min(rawButtons.length(), 2); i++) {
                    JSONObject b = rawButtons.getJSONObject(i);
                    String label = b.optString("label", "").trim();
                    String url = b.optString("url", "").trim();
                    if (!label.isEmpty() && !url.isEmpty()) {
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://" + url;
                        }
                        JSONObject btnObj = new JSONObject();
                        btnObj.put("label", truncate(label, 32));
                        btnObj.put("url", truncate(url, 512));
                        buttons.put(btnObj);
                    }
                }
                if (buttons.length() > 0) {
                    activity.put("buttons", buttons);
                    if (activity.has("secrets")) {
                        logger.warning("[DiscordIPC] Discord Error 5005 prevention: secrets cannot be sent with buttons. Omitting secrets.");
                        activity.remove("secrets");
                    }
                }
            }

            // 9. Payload Override (allows injecting or overriding any arbitrary fields into activity)
            if (rpc.has("payload_override") && !rpc.isNull("payload_override")) {
                JSONObject override = rpc.getJSONObject("payload_override");
                for (String key : override.keySet()) {
                    activity.put(key, override.get(key));
                }
            }

            // Custom PID if provided
            int targetPid = processId;
            if (rpc.has("pid") && !rpc.isNull("pid")) {
                targetPid = rpc.getInt("pid");
            }

            // Frame payload
            JSONObject frame = new JSONObject();
            frame.put("cmd", "SET_ACTIVITY");
            JSONObject args = new JSONObject();
            args.put("pid", targetPid);
            args.put("activity", activity);
            frame.put("args", args);
            frame.put("nonce", UUID.randomUUID().toString());

            JSONObject resp = sendAndReceive(OP_FRAME, frame.toString());
            if (resp != null) {
                if ("ERROR".equals(resp.optString("evt"))) {
                    logger.severe("[DiscordIPC] Error from Discord: " + resp.opt("data"));
                } else {
                    logger.info("[DiscordIPC] Presence updated successfully on Discord.");
                }
            }
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

            sendAndReceive(OP_FRAME, frame.toString());
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
