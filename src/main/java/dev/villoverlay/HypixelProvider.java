package dev.villoverlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/**
 * Official Hypixel API: {@code GET https://api.hypixel.net/v2/player?uuid=...}
 * with the key in the {@code API-Key} header. Same HttpURLConnection style as
 * the existing claudecode ApiBackend.
 */
public final class HypixelProvider implements StatsProvider {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    @Override
    public String id() {
        return "hypixel";
    }

    @Override
    public JsonObject fetchPlayer(String uuid) throws StatsException {
        String key = BwConfig.hypixelKey.trim();
        if (key.isEmpty()) {
            throw new StatsException("no Hypixel key - set one with /vill key <key>", false);
        }
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.hypixel.net/v2/player?uuid=" + uuid);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("API-Key", key);

            int code = conn.getResponseCode();
            if (code == 403) {
                throw new StatsException("Hypixel key rejected (403)", false);
            }
            if (code == 429) {
                throw new StatsException("Hypixel rate limit (429)", true);
            }
            JsonObject root = readJson(conn, code);
            if (root == null) {
                throw new StatsException("Hypixel http " + code, code >= 500);
            }
            if (!root.has("success") || !root.get("success").getAsBoolean()) {
                String cause = root.has("cause") ? root.get("cause").getAsString() : "unknown";
                throw new StatsException("Hypixel: " + cause, cause.toLowerCase().contains("throttle"));
            }
            if (!root.has("player") || root.get("player").isJsonNull()) {
                return null; // nicked / no such player
            }
            return root.getAsJsonObject("player");
        } catch (StatsException e) {
            throw e;
        } catch (Exception e) {
            throw new StatsException("Hypixel: " + e.getMessage(), true);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /** Read either the body or the error stream into a JSON object, or null. */
    static JsonObject readJson(HttpURLConnection conn, int code) {
        InputStream is = null;
        try {
            is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) {
                return null;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(is, UTF8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            if (sb.length() == 0) {
                return null;
            }
            return new JsonParser().parse(sb.toString()).getAsJsonObject();
        } catch (Exception e) {
            return null;
        }
    }
}
