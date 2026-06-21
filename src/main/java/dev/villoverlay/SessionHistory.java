package dev.villoverlay;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local, persistent "have I seen this player before?" log. Stored as JSON in
 * {@code config/villoverlay-history.json}, keyed by lowercased name. Each player
 * is counted at most once per game (a per-game set guards against the per-tick
 * roster scan). Drives the HUD's repeat-encounter marker. Local only.
 */
public final class SessionHistory {

    private static final SessionHistory INSTANCE = new SessionHistory();

    public static SessionHistory get() {
        return INSTANCE;
    }

    /** Encounter record for one name. */
    public static final class Seen {
        public int count;
        public long first;
        public long last;
        public String uuid;
    }

    private final ConcurrentHashMap<String, Seen> map = new ConcurrentHashMap<String, Seen>();
    private final Set<String> countedThisGame = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile File file;
    private volatile boolean dirty = false;

    private SessionHistory() {
    }

    public void init(File dir) {
        file = new File(dir, "villoverlay-history.json");
        load();
    }

    /** Client tick while in game: count each present player once for this game. */
    public void observe(List<PlayerRef> roster) {
        if (!BwConfig.trackHistory || roster == null) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PlayerRef p : roster) {
            String key = p.name.toLowerCase();
            if (!countedThisGame.add(key)) {
                continue;
            }
            Seen s = map.get(key);
            if (s == null) {
                s = new Seen();
                s.first = now;
                map.put(key, s);
            }
            s.count++;
            s.last = now;
            if (p.uuid != null) {
                s.uuid = p.uuid;
            }
            dirty = true;
        }
    }

    /** Call when a game ends: reset the per-game guard and flush to disk. */
    public void endGame() {
        countedThisGame.clear();
        if (dirty) {
            save();
            dirty = false;
        }
    }

    /** Total times this name has been seen (including the current game). */
    public int seen(String name) {
        Seen s = name == null ? null : map.get(name.toLowerCase());
        return s == null ? 0 : s.count;
    }

    public Seen info(String name) {
        return name == null ? null : map.get(name.toLowerCase());
    }

    private void load() {
        map.clear();
        JsonObject root = JsonStore.read(file);
        for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
            try {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                Seen s = new Seen();
                s.count = o.has("count") ? o.get("count").getAsInt() : 0;
                s.first = o.has("first") ? o.get("first").getAsLong() : 0L;
                s.last = o.has("last") ? o.get("last").getAsLong() : 0L;
                s.uuid = o.has("uuid") && !o.get("uuid").isJsonNull() ? o.get("uuid").getAsString() : null;
                map.put(e.getKey().toLowerCase(), s);
            } catch (Exception ignored) {
            }
        }
    }

    private synchronized void save() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Seen> e : map.entrySet()) {
            Seen s = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("count", s.count);
            o.addProperty("first", s.first);
            o.addProperty("last", s.last);
            if (s.uuid != null) {
                o.addProperty("uuid", s.uuid);
            }
            root.add(e.getKey(), o);
        }
        JsonStore.write(file, root);
    }
}
