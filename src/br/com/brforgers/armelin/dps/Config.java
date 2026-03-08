package br.com.brforgers.armelin.dps;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

public class Config {
    public final String clientId;
    public final String tetrioClientId;
    public final String robloxClientId;
    public final String youtubeClientId;
    public final int wsPort;
    public final long sourceTimeout;

    private Config(String clientId, String tetrioClientId, String robloxClientId, String youtubeClientId, int wsPort, long sourceTimeout) {
        this.clientId = clientId;
        this.tetrioClientId = tetrioClientId;
        this.robloxClientId = robloxClientId;
        this.youtubeClientId = youtubeClientId;
        this.wsPort = wsPort;
        this.sourceTimeout = sourceTimeout;
    }

    public static Config load() {
        String defaultClientId = "";
        String defaultTetrioClientId = "";
        String defaultRobloxClientId = "";
        String defaultYoutubeClientId = "";
        int defaultPort = 6680;
        long defaultTimeout = 15000;

        try {
            File jarDir = new File(
                Config.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParentFile();
            File configFile = new File(jarDir, "config.json");

            if (!configFile.exists()) {
                System.out.println("[Config] config.json not found next to JAR, using defaults.");
                return new Config(defaultClientId, defaultTetrioClientId, defaultRobloxClientId, defaultYoutubeClientId, defaultPort, defaultTimeout);
            }

            FileInputStream fis = new FileInputStream(configFile);
            byte[] data = new byte[(int) configFile.length()];
            fis.read(data);
            fis.close();

            JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));

            String clientId = json.optString("clientId", defaultClientId);
            String tetrioClientId = json.optString("tetrioClientId", defaultTetrioClientId);
            String robloxClientId = json.optString("robloxClientId", defaultRobloxClientId);
            String youtubeClientId = json.optString("youtubeClientId", defaultYoutubeClientId);
            int wsPort = json.optInt("wsPort", defaultPort);
            long sourceTimeout = json.optLong("sourceTimeout", defaultTimeout);

            System.out.println("[Config] Loaded config.json — clientId: " +
                (clientId.isEmpty() ? "(not set)" : clientId.substring(0, Math.min(6, clientId.length())) + "...") +
                ", tetrioClientId: " +
                (tetrioClientId.isEmpty() ? "(not set)" : tetrioClientId.substring(0, Math.min(6, tetrioClientId.length())) + "...") +
                ", robloxClientId: " +
                (robloxClientId.isEmpty() ? "(not set)" : robloxClientId.substring(0, Math.min(6, robloxClientId.length())) + "...") +
                ", youtubeClientId: " +
                (youtubeClientId.isEmpty() ? "(not set)" : youtubeClientId.substring(0, Math.min(6, youtubeClientId.length())) + "...") +
                ", port: " + wsPort + ", timeout: " + sourceTimeout + "ms");

            return new Config(clientId, tetrioClientId, robloxClientId, youtubeClientId, wsPort, sourceTimeout);
        } catch (Exception e) {
            System.err.println("[Config] Error loading config.json: " + e.getMessage());
            return new Config(defaultClientId, defaultTetrioClientId, defaultRobloxClientId, defaultYoutubeClientId, defaultPort, defaultTimeout);
        }
    }
}
