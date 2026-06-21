package dev.villoverlay;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Generic third-party provider for any service that returns Hypixel
 * player-format JSON ({@code {success, player}}) - e.g. Antisniper or Polsu.
 * The URL is a template from config with {key} and {uuid} substituted, so no
 * endpoint is hard-coded/guessed; you point it at whatever your service uses.
 */
public final class ProxyProvider implements StatsProvider {

    @Override
    public String id() {
        return "proxy";
    }

    @Override
    public JsonObject fetchPlayer(String uuid) throws StatsException {
        String template = BwConfig.proxyUrl.trim();
        if (template.isEmpty()) {
            throw new StatsException("no proxyUrl set - edit config/villoverlay.cfg", false);
        }
        String urlStr = template
                .replace("{key}", BwConfig.proxyKey.trim())
                .replace("{uuid}", uuid);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code == 403) {
                throw new StatsException("proxy 403 (bad key?)", false);
            }
            if (code == 429) {
                throw new StatsException("proxy rate limit (429)", true);
            }
            JsonObject root = HypixelProvider.readJson(conn, code);
            if (root == null) {
                throw new StatsException("proxy http " + code, code >= 500);
            }
            if (root.has("success") && !root.get("success").getAsBoolean()) {
                String cause = root.has("cause") ? root.get("cause").getAsString() : "unknown";
                throw new StatsException("proxy: " + cause, cause.toLowerCase().contains("throttle"));
            }
            if (!root.has("player") || root.get("player").isJsonNull()) {
                return null; // nicked / not found
            }
            return root.getAsJsonObject("player");
        } catch (StatsException e) {
            throw e;
        } catch (Exception e) {
            throw new StatsException("proxy: " + e.getMessage(), true);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}
