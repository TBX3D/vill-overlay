package dev.villoverlay;

import com.google.gson.JsonObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort denicking: given a name that the Mojang API says has no account
 * (i.e. a Hypixel nick), ask a configurable third-party endpoint who it really
 * is. Off-thread (called from the fetch pool). Results are cached for the
 * session, negatives included, so a nick is hit at most once.
 *
 * Like {@link ProxyProvider}, NO endpoint is hard-coded as truth: the URL is a
 * template from config with {key}/{name} substituted. The default points at
 * Antisniper's denick route but is UNVERIFIED - confirm the exact path/shape
 * with your service before relying on it. Parsing is deliberately tolerant.
 */
public final class Denicker {

    /** A resolved identity. {@code uuid} is undashed; {@code ign} may be null. */
    public static final class Result {
        public final String uuid;
        public final String ign;

        Result(String uuid, String ign) {
            this.uuid = uuid;
            this.ign = ign;
        }
    }

    private static final Result MISS = new Result(null, null);
    private static final ConcurrentHashMap<String, Result> CACHE = new ConcurrentHashMap<String, Result>();

    private Denicker() {
    }

    /** Blocking; call off the main thread. Returns null when denick is off or fails. */
    public static Result resolve(String nick) {
        if (!BwConfig.denickEnabled || nick == null) {
            return null;
        }
        String template = BwConfig.denickUrl.trim();
        if (template.isEmpty()) {
            return null;
        }
        String key = nick.toLowerCase();
        Result cached = CACHE.get(key);
        if (cached != null) {
            return cached == MISS ? null : cached;
        }
        Result r = query(template, nick);
        CACHE.put(key, r == null ? MISS : r);
        return r;
    }

    private static Result query(String template, String nick) {
        String denickKey = BwConfig.denickKey.trim();
        if (denickKey.isEmpty()) {
            denickKey = BwConfig.proxyKey.trim();
        }
        String urlStr = template
                .replace("{key}", denickKey)
                .replace("{name}", nick)
                .replace("{nick}", nick);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            JsonObject root = HypixelProvider.readJson(conn, code);
            if (root == null) {
                return null;
            }
            if (root.has("success") && !root.get("success").getAsBoolean()) {
                return null;
            }
            // Tolerant: accept {player:{uuid,ign}}, {uuid,ign}, or {nick:{uuid}}.
            JsonObject player = root.has("player") && root.get("player").isJsonObject()
                    ? root.getAsJsonObject("player") : root;
            String uuid = str(player, "uuid");
            if (uuid == null) {
                uuid = str(root, "uuid");
            }
            if (uuid == null) {
                return null;
            }
            String ign = str(player, "ign");
            if (ign == null) {
                ign = str(player, "name");
            }
            if (ign == null) {
                ign = str(root, "ign");
            }
            return new Result(uuid.replace("-", ""), ign);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static String str(JsonObject o, String field) {
        try {
            if (o != null && o.has(field) && o.get(field).isJsonPrimitive()) {
                String v = o.get(field).getAsString();
                return v.isEmpty() ? null : v;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
