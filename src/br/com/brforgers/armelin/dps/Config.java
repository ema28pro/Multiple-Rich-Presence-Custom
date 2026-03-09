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
    public final String wplaceClientId;
    public final String animeClientId;
    public final int wsPort;
    public final long sourceTimeout;

    private Config(String clientId, String tetrioClientId, String robloxClientId, String youtubeClientId,
            String wplaceClientId, String animeClientId, int wsPort, long sourceTimeout) {
        this.clientId = clientId;
        this.tetrioClientId = tetrioClientId;
        this.robloxClientId = robloxClientId;
        this.youtubeClientId = youtubeClientId;
        this.wplaceClientId = wplaceClientId;
        this.animeClientId = animeClientId;
        this.wsPort = wsPort;
        this.sourceTimeout = sourceTimeout;
    }
    
        String defaultClientId = "1479761532412887040"; // This clientId replaces the specific clientIds (tetrioClientId, robloxClientId, youtubeClientId, wplaceClientId, animeClientId) when they are not found
        return val.isEmpty() ? "(not set)" : val.substring(0, Math.min(6, val.length())) + "...";
    }

    public static Config load() {
        String defaultClientId = "1479761532412887040"; // This clientId replaces the other clientId's(tetrioClientId, robloxClientId, youtubeClientId) when they are not found
        int defaultPort = 6680;
        long defaultTimeout = 15000;

        try {
            File jarDir = new File(
                Config.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).getParentFile();
            File configFile = new File(jarDir, "config.json");

            if (!configFile.exists()) {
                System.out.println("[Config] config.json not found next to JAR, using defaults.");
                return new Config(defaultClientId, defaultClientId, defaultClientId, defaultClientId, defaultClientId, defaultClientId, defaultPort, defaultTimeout);
            }

            FileInputStream fis = new FileInputStream(configFile);
            byte[] data = new byte[(int) configFile.length()];
            fis.read(data);
            fis.close();

            JSONObject json = new JSONObject(new String(data, StandardCharsets.UTF_8));

            String clientId = json.optString("clientId", defaultClientId);
            String tetrioClientId = json.optString("tetrioClientId", defaultClientId);
            String robloxClientId = json.optString("robloxClientId", defaultClientId);
            String youtubeClientId = json.optString("youtubeClientId", defaultClientId);
            String wplaceClientId = json.optString("wplaceClientId", defaultClientId);
            String animeClientId = json.optString("animeClientId", defaultClientId);
            int wsPort = json.optInt("wsPort", defaultPort);
            long sourceTimeout = json.optLong("sourceTimeout", defaultTimeout);

            String fmt = "  %-18s %s";
            System.out.println("[Config] Loaded config.json");
            System.out.printf((fmt) + "%n", "clientId:",      mask(clientId));
            System.out.printf((fmt) + "%n", "tetrioClientId:", mask(tetrioClientId));
            System.out.printf((fmt) + "%n", "robloxClientId:", mask(robloxClientId));
            System.out.printf((fmt) + "%n", "youtubeClientId:", mask(youtubeClientId));
            System.out.printf((fmt) + "%n", "wplaceClientId:", mask(wplaceClientId));
            System.out.printf((fmt) + "%n", "animeClientId:", mask(animeClientId));
            System.out.printf((fmt) + "%n", "port:",          wsPort);
            System.out.printf((fmt) + "%n", "timeout:",       sourceTimeout + "ms");

            return new Config(clientId, tetrioClientId, robloxClientId, youtubeClientId, wplaceClientId, animeClientId, wsPort, sourceTimeout);
        } catch (Exception e) {
            System.err.println("[Config] Error loading config.json: " + e.getMessage());
            return new Config(defaultClientId, defaultClientId, defaultClientId, defaultClientId, defaultClientId, defaultClientId, defaultPort, defaultTimeout);
        }
    }
}