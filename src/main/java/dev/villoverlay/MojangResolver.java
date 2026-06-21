package dev.villoverlay;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;

/**
 * name -> undashed uuid, via the Mojang API, cached forever (incl. negatives).
 * Only used as a fallback when the tab list didn't already hand us a uuid.
 */
public final class MojangResolver {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final ConcurrentHashMap<String, String> CACHE = new ConcurrentHashMap<String, String>();

    private MojangResolver() {
    }

    /** Blocking; call off the main thread. Returns null if the name has no account. */
    public static String uuidFor(String name) {
        String key = name.toLowerCase();
        String cached = CACHE.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String uuid = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://api.mojang.com/users/profiles/minecraft/" + name);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            if (conn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), UTF8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line);
                }
                r.close();
                JsonObject o = new JsonParser().parse(sb.toString()).getAsJsonObject();
                if (o.has("id")) {
                    uuid = o.get("id").getAsString();
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        CACHE.put(key, uuid == null ? "" : uuid);
        return uuid;
    }
}
