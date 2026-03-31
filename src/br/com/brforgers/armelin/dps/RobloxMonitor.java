package br.com.brforgers.armelin.dps;

import java.util.logging.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class RobloxMonitor implements Runnable {
    private static final Logger logger = Logger.getLogger("DPS");

    private final SourceManager sourceManager;
    private final int priority;
    private boolean wasRunning = false;
    private long startTime = 0;
    private volatile boolean enabled = true;

    private static final String GAME_NAME = "RIVALS";
    private static final String RIVALS_UNIVERSE_ID = "6035872082";
    private static final String IMAGE_URL = "https://tr.rbxcdn.com/180DAY-f6cd46848e254e0ab04695b5fa7cd4e7/512/512/Image/Webp/noFilter";
    private static final Pattern UNIVERSE_ID_PATTERN = Pattern.compile("universeid:(\\d+)");

    public RobloxMonitor(SourceManager sourceManager, int priority) {
        this.sourceManager = sourceManager;
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled && wasRunning) {
            sourceManager.removeSource("roblox-desktop");
            wasRunning = false;
            startTime = 0;
            logger.info("[RobloxMonitor] Disabled — cleared source");
        }
    }

    public void run() {
        try {
            if (!enabled) {
                return;
            }

            if (!isRobloxRunning()) {
                if (wasRunning) {
                    logger.info("[RobloxMonitor] Roblox not running, clearing source");
                    sourceManager.removeSource("roblox-desktop");
                    wasRunning = false;
                    startTime = 0;
                }
                return;
            }

            // Roblox is running — check logs for RIVALS PlaceID
            if (!isRivalsInLogs()) {
                if (wasRunning) {
                    logger.info("[RobloxMonitor] Roblox running but not RIVALS, clearing source");
                    sourceManager.removeSource("roblox-desktop");
                    wasRunning = false;
                    startTime = 0;
                }
                return;
            }

            if (!wasRunning) {
                startTime = System.currentTimeMillis();
                wasRunning = true;
                logger.info("[RobloxMonitor] Detected: " + GAME_NAME);
            }

            JSONObject rpc = new JSONObject();
            rpc.put("details", GAME_NAME);
            rpc.put("largeImageKey", IMAGE_URL);
            rpc.put("largeImageText", GAME_NAME);
            rpc.put("startTimestamp", startTime);

            sourceManager.updateSource("roblox-desktop", priority, rpc);

        } catch (Exception e) {
            logger.severe("[RobloxMonitor] Error: " + e.getMessage());
        }
    }

    private boolean isRobloxRunning() {
        try {
            Process process = Runtime.getRuntime().exec(
                    new String[] { "tasklist", "/FI", "IMAGENAME eq RobloxPlayerBeta.exe", "/NH" });
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("RobloxPlayerBeta.exe")) {
                    reader.close();
                    process.waitFor();
                    return true;
                }
            }
            reader.close();
            process.waitFor();
        } catch (Exception e) {
            // tasklist failed
        }
        return false;
    }

    private boolean isRivalsInLogs() {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData == null)
                return false;

            File logsDir = new File(localAppData, "Roblox/logs");
            if (!logsDir.isDirectory())
                return false;

            // Find the most recently modified .log file
            File[] logFiles = logsDir.listFiles((dir, name) -> name.endsWith(".log"));
            if (logFiles == null || logFiles.length == 0)
                return false;

            Arrays.sort(logFiles, Comparator.comparingLong(File::lastModified).reversed());
            File latestLog = logFiles[0];

            // Read log and look for GameJoinLoadTime with RIVALS UniverseID
            BufferedReader reader = new BufferedReader(new FileReader(latestLog));
            String line;
            String lastUniverseId = null;
            while ((line = reader.readLine()) != null) {
                if (line.contains("GameJoinLoadTime") && line.contains("universeid:")) {
                    Matcher m = UNIVERSE_ID_PATTERN.matcher(line);
                    if (m.find()) {
                        lastUniverseId = m.group(1);
                    }
                }
            }
            reader.close();

            return RIVALS_UNIVERSE_ID.equals(lastUniverseId);
        } catch (Exception e) {
            // Log reading failed
        }
        return false;
    }
}
