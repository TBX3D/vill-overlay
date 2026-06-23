package dev.villoverlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Looks a player's uuid up against a shared community cheater/sniper blacklist
 * (Urchin and friends) and returns the tag if they're flagged. Off-thread
 * (called from the fetch pool). Results are cached for the session, misses
 * included, so each uuid is hit at most once.
 *
 * Like {@link Denicker}, NO endpoint is hard-coded as truth: the URL is a
 * template from config with {key}/{uuid} substituted. The default points at
 * Urchin's player route, whose shape is {@code {uuid, tags:[{type,reason}], ...}}
 * - a non-empty {@code tags} array means flagged. Parsing is deliberately
 * tolerant so a differently-shaped service still works.
 */
public final class Blacklist {

    /** A blacklist hit. {@code label} is the tag category, e.g. "cheater". */
    public static final class Result {
        public final String label;

        Result(String label) {
            this.label = label;
        }
    }

    private static final Result MISS = new Result(null);
    private static final ConcurrentHashMap<String, Result> CACHE = new ConcurrentHashMap<String, Result>();

    private Blacklist() {
    }

    /** Blocking; call off the main thread. Returns null when off, missed or failed. */
    public static Result resolve(String uuid) {
        if (!BwConfig.blacklistEnabled || uuid == null || uuid.isEmpty()) {
            return null;
        }
        String template = BwConfig.blacklistUrl.trim();
        if (template.isEmpty()) {
            return null;
        }
        Result cached = CACHE.get(uuid);
        if (cached != null) {
            return cached == MISS ? null : cached;
        }
        Result r = query(template, uuid);
        if (r == null) {
            return null; // transient failure: don't cache, retry on a later pass
        }
        CACHE.put(uuid, r);
        return r == MISS ? null : r;
    }

    private static Result query(String template, String uuid) {
        String key = BwConfig.blacklistKey.trim();
        if (key.isEmpty()) {
            key = BwConfig.proxyKey.trim();
        }
        String urlStr = template
                .replace("{key}", key)
                .replace("{uuid}", uuid);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            if (code == 429 || code >= 500) {
                return null; // transient: rate-limited or server error, retry later
            }
            JsonObject root = HypixelProvider.readJson(conn, code);
            if (root == null) {
                return null;
            }
            if (root.has("success") && !root.get("success").getAsBoolean()) {
                return null; // service error (e.g. bad key); retry rather than cache a miss
            }
            Result hit = parse(root);
            return hit != null ? hit : MISS;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Tolerant flag extraction. Primary shape is Urchin's {@code tags} array
     * (flagged when non-empty, label from the most severe tag's {@code type});
     * also accepts a top-level {@code type} string or a true {@code flagged}/
     * {@code cheater}/{@code sniper} boolean.
     */
    private static Result parse(JsonObject root) {
        if (root.has("tags") && root.get("tags").isJsonArray()) {
            JsonArray tags = root.getAsJsonArray("tags");
            if (tags.size() == 0) {
                return null;
            }
            String label = severestTag(tags);
            return new Result(label != null ? label : "flagged");
        }
        String type = clean(str(root, "type"));
        if (type != null) {
            return new Result(type);
        }
        if (boolTrue(root, "flagged") || boolTrue(root, "cheater") || boolTrue(root, "sniper")) {
            String label = boolTrue(root, "sniper") ? "sniper" : boolTrue(root, "cheater") ? "cheater" : "flagged";
            return new Result(label);
        }
        return null;
    }

    /**
     * The most severe tag's category: a cheater-class tag outranks a sniper,
     * which outranks anything else. Players carry several tags in no fixed
     * order, so surface the worst rather than whichever happens to be first.
     */
    private static String severestTag(JsonArray tags) {
        String best = null;
        int bestRank = -1;
        for (int i = 0; i < tags.size(); i++) {
            if (!tags.get(i).isJsonObject()) {
                continue;
            }
            String type = clean(str(tags.get(i).getAsJsonObject(), "type"));
            if (type == null) {
                continue;
            }
            int rank = rankOf(type);
            if (rank > bestRank) {
                bestRank = rank;
                best = type;
            }
        }
        return best;
    }

    private static int rankOf(String type) {
        String t = type.toLowerCase();
        if (t.contains("cheat")) {
            return 2;
        }
        if (t.contains("snip")) {
            return 1;
        }
        return 0;
    }

    /**
     * The label is third-party text rendered into chat/HUD and the AI prompt, so
     * drop Minecraft formatting codes (a section sign plus its following code
     * char) and control chars, and cap the length.
     */
    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length() && sb.length() < 24; i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++; // skip the section sign and the format code that follows it
                continue;
            }
            if (c < ' ') {
                continue;
            }
            sb.append(c);
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static boolean boolTrue(JsonObject o, String field) {
        try {
            return o.has(field) && o.get(field).isJsonPrimitive() && o.get(field).getAsBoolean();
        } catch (Exception ignored) {
            return false;
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
