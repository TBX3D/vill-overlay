package dev.villoverlay;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local, persistent per-player tags / notes - your own blacklist. Stored as
 * plain JSON in {@code config/villoverlay-tags.json}, keyed by lowercased name.
 * Nothing leaves your machine. Tags colour the player in the HUD and "danger"
 * labels raise a {@link ThreatAlert}. All access is from the client thread; the
 * map is concurrent only out of caution.
 */
public final class PlayerTags {

    /** One tag: a short label, a §-colour code and an optional free-text note. */
    public static final class Tag {
        public final String label;
        public final String color;
        public final String note;
        public final long added;

        Tag(String label, String color, String note, long added) {
            this.label = label;
            this.color = color;
            this.note = note;
            this.added = added;
        }
    }

    // Known labels -> §-colour. Anything else falls back to yellow.
    private static final Map<String, String> COLORS = new ConcurrentHashMap<String, String>();
    // Labels that raise a sound/banner alert when this player is in the lobby.
    private static final Set<String> DANGER = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            "sniper", "cheater", "hacker", "blacklist", "bl", "toxic", "troll", "annoying")));

    static {
        COLORS.put("sniper", "§c");
        COLORS.put("cheater", "§4");
        COLORS.put("hacker", "§4");
        COLORS.put("blacklist", "§4");
        COLORS.put("bl", "§4");
        COLORS.put("toxic", "§6");
        COLORS.put("troll", "§6");
        COLORS.put("annoying", "§6");
        COLORS.put("friend", "§a");
        COLORS.put("mate", "§a");
        COLORS.put("party", "§b");
        COLORS.put("noob", "§a");
    }

    private static final ConcurrentHashMap<String, Tag> MAP = new ConcurrentHashMap<String, Tag>();
    private static volatile File file;

    private PlayerTags() {
    }

    public static void init(File dir) {
        file = new File(dir, "villoverlay-tags.json");
        load();
    }

    /** §-colour for a label (defaults to yellow for custom labels). */
    public static String colorFor(String label) {
        if (label == null) {
            return "§e";
        }
        String c = COLORS.get(label.toLowerCase());
        return c != null ? c : "§e";
    }

    /** True if this label should raise an alert when the player is present. */
    public static boolean isDanger(String label) {
        return label != null && DANGER.contains(label.toLowerCase());
    }

    public static Tag get(String name) {
        return name == null ? null : MAP.get(name.toLowerCase());
    }

    /** Add or replace a tag, preserving an existing note unless a new one is given. */
    public static void set(String name, String label, String note) {
        if (name == null || label == null) {
            return;
        }
        String key = name.toLowerCase();
        Tag old = MAP.get(key);
        String keptNote = note != null ? note : (old != null ? old.note : null);
        long added = old != null ? old.added : System.currentTimeMillis();
        MAP.put(key, new Tag(label, colorFor(label), keptNote, added));
        save();
    }

    /** Attach/replace only the note, keeping (or defaulting) the label. */
    public static void note(String name, String note) {
        if (name == null) {
            return;
        }
        Tag old = MAP.get(name.toLowerCase());
        String label = old != null ? old.label : "note";
        MAP.put(name.toLowerCase(), new Tag(label, colorFor(label), note, old != null ? old.added : System.currentTimeMillis()));
        save();
    }

    public static boolean remove(String name) {
        if (name == null) {
            return false;
        }
        boolean had = MAP.remove(name.toLowerCase()) != null;
        if (had) {
            save();
        }
        return had;
    }

    /** name -> Tag, sorted by name, for listing. */
    public static Map<String, Tag> all() {
        return new TreeMap<String, Tag>(MAP);
    }

    public static int size() {
        return MAP.size();
    }

    static void load() {
        MAP.clear();
        JsonObject root = JsonStore.read(file);
        for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
            try {
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject o = e.getValue().getAsJsonObject();
                String label = o.has("label") ? o.get("label").getAsString() : "note";
                String color = o.has("color") ? o.get("color").getAsString() : colorFor(label);
                String note = o.has("note") && !o.get("note").isJsonNull() ? o.get("note").getAsString() : null;
                long added = o.has("added") ? o.get("added").getAsLong() : 0L;
                MAP.put(e.getKey().toLowerCase(), new Tag(label, color, note, added));
            } catch (Exception ignored) {
            }
        }
    }

    private static synchronized void save() {
        if (file == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Tag> e : MAP.entrySet()) {
            Tag t = e.getValue();
            JsonObject o = new JsonObject();
            o.addProperty("label", t.label);
            o.addProperty("color", t.color);
            if (t.note != null) {
                o.addProperty("note", t.note);
            }
            o.addProperty("added", t.added);
            root.add(e.getKey(), o);
        }
        JsonStore.write(file, root);
    }
}
