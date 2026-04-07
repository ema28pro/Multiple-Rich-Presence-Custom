package br.com.brforgers.armelin.dps;

import java.util.logging.Logger;

import club.minnced.discord.rpc.DiscordRichPresence;
import com.google.gson.Gson;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

public class SourceManager {
    private static final Logger logger = Logger.getLogger("DPS");

    public static class SourceEntry {
        public final String source;
        public final int priority;
        public final JSONObject rpcData;
        public final long lastUpdate;
        public final boolean persistent;

        public SourceEntry(String source, int priority, JSONObject rpcData, long lastUpdate, boolean persistent) {
            this.source = source;
            this.priority = priority;
            this.rpcData = rpcData;
            this.lastUpdate = lastUpdate;
            this.persistent = persistent;
        }
    }

    public static class UpdateResult {
        public final boolean changed;
        public final DiscordRichPresence presence;
        public final String activeSource;

        public UpdateResult(boolean changed, DiscordRichPresence presence, String activeSource) {
            this.changed = changed;
            this.presence = presence;
            this.activeSource = activeSource;
        }
    }

    private final ConcurrentHashMap<String, SourceEntry> sources = new ConcurrentHashMap<>();
    private final long timeout;
    private final Gson gson = new Gson();
    private volatile String lastActiveKey = null;
    private volatile int lastRpcHash = 0;

    public SourceManager(long timeout) {
        this.timeout = timeout;
    }

    public void updateSource(String source, int priority, JSONObject rpcData) {
        sources.put(source, new SourceEntry(source, priority, rpcData, System.currentTimeMillis(), false));
    }

    public void updatePersistentSource(String source, int priority, JSONObject rpcData) {
        sources.put(source, new SourceEntry(source, priority, rpcData, System.currentTimeMillis(), true));
    }

    public void removeSource(String source) {
        sources.remove(source);
    }

    public SourceEntry getSource(String source) {
        return sources.get(source);
    }

    public void cleanExpired() {
        long now = System.currentTimeMillis();
        sources.entrySet().removeIf(e -> !e.getValue().persistent && now - e.getValue().lastUpdate > timeout);
    }

    public synchronized UpdateResult checkForChanges() {
        return evaluate();
    }

    public SourceEntry getActiveSource() {
        // Select the most relevant source based on priority and recency, ignoring
        // expired non-persistent sources
        long now = System.currentTimeMillis();
        SourceEntry best = null;
        for (SourceEntry entry : sources.values()) {
            if (!entry.persistent && now - entry.lastUpdate > timeout)
                continue;
            if (best == null) {
                best = entry;
            } else if (entry.priority > best.priority) {
                best = entry;
            } else if (entry.priority == best.priority && entry.lastUpdate > best.lastUpdate) {
                best = entry;
            }
        }
        return best;
    }

    private UpdateResult evaluate() {
        // Get the current active source (the best one to use)
        SourceEntry active = getActiveSource();

        if (active == null) {
            // If there was a previous active source, clear its info and signal a change
            if (lastActiveKey != null) {
                lastActiveKey = null;
                lastRpcHash = 0;
                return new UpdateResult(true, null, null);
            }
            // If there was never an active source, signal no change
            return new UpdateResult(false, null, null);
        }

        // Calculate a hash for the current presence data
        int currentHash = active.rpcData.toString().hashCode();
        // If the active source and its data are the same as before, signal no change
        if (active.source.equals(lastActiveKey) && currentHash == lastRpcHash) {
            return new UpdateResult(false, null, active.source);
        }

        // Update the internal info with the new active source and its data
        lastActiveKey = active.source;
        lastRpcHash = currentHash;
        // Signal that there was a change, and return the new presence and source name
        return new UpdateResult(true, buildPresence(active.rpcData), active.source);
    }

    private DiscordRichPresence buildPresence(JSONObject rpc) {

        // Valid image keys
        String smallKey = rpc.optString("smallImageKey", "");
        String largeKey = rpc.optString("largeImageKey", "");
        if (smallKey.startsWith("data:")) {
            logger.severe(
                    "[SourceManager] smallImageKey is base64 -- Discord will ignore it. Use HTTPS URL or asset name.");
        }
        if (largeKey.startsWith("data:")) {
            logger.severe(
                    "[SourceManager] largeImageKey is base64 -- Discord will ignore it. Use HTTPS URL or asset name.");
        }

        JSONObject truncated = new JSONObject();

        copyTruncated(rpc, truncated, "details", 128);
        copyTruncated(rpc, truncated, "state", 128);
        copyTruncated(rpc, truncated, "largeImageText", 128);
        copyTruncated(rpc, truncated, "smallImageText", 128);

        // Image keys — pass as-is (asset key or HTTPS URL)
        copyIfPresent(rpc, truncated, "largeImageKey");
        copyIfPresent(rpc, truncated, "smallImageKey");

        // Timestamps — convert ms (from Date.now()) to seconds (Discord C SDK)
        copyTimestamp(rpc, truncated, "startTimestamp");
        copyTimestamp(rpc, truncated, "endTimestamp");

        // Party fields
        copyIfPresent(rpc, truncated, "partyId");
        if (rpc.has("partySize") && !rpc.isNull("partySize")) {
            truncated.put("partySize", rpc.getInt("partySize"));
        }
        if (rpc.has("partyMax") && !rpc.isNull("partyMax")) {
            truncated.put("partyMax", rpc.getInt("partyMax"));
        }

        // buttons field is intentionally ignored — DiscordRichPresence has no such
        // field,
        // Gson will skip unknown keys during deserialization

        return gson.fromJson(truncated.toString(), DiscordRichPresence.class);
    }

    private static void copyTruncated(JSONObject src, JSONObject dst, String key, int maxLen) {
        // This method copies a string field from src to dst, truncating it to maxLen
        // characters if necessary.
        // src = source JSON object
        // dst = destination JSON object
        if (src.has(key) && !src.isNull(key)) {
            String val = src.getString(key);
            if (val.length() > maxLen) {
                val = val.substring(0, maxLen);
            }
            dst.put(key, val);
        }
    }

    private static void copyIfPresent(JSONObject src, JSONObject dst, String key) {
        // This method copies a field from src to dst if it exists and is not null,
        // without any modification.
        if (src.has(key) && !src.isNull(key)) {
            dst.put(key, src.get(key));
        }
    }

    private static void copyTimestamp(JSONObject src, JSONObject dst, String key) {
        // This method copies a timestamp field from src to dst, converting it from
        // milliseconds to seconds.
        if (!src.has(key) || src.isNull(key)) {
            dst.put(key, 0); // Discord treats 0 as "no timestamp", so if the field is missing or null, we
                             // set it to 0
            return;
        }
        try {
            Object val = src.get(key);
            long ms;
            if (val instanceof Number) {
                ms = ((Number) val).longValue();
            } else {
                ms = Long.parseLong(val.toString().trim());
            }
            if (ms > 0) {
                dst.put(key, ms / 1000);
            } else {
                dst.put(key, 0);
            }
        } catch (Exception ignored) {
            dst.put(key, 0); // If there's any error parsing the timestamp, we set it to 0 to avoid sending
                             // invalid data to Discord
        }
    }
}