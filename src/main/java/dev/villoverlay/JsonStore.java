package dev.villoverlay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Tiny read/write helper for the small JSON data files the local features keep
 * next to {@code villoverlay.cfg} (tags, session history). Everything is local;
 * nothing here ever touches the network. Best-effort: a missing or corrupt file
 * just yields an empty object rather than throwing.
 */
final class JsonStore {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private JsonStore() {
    }

    static JsonObject read(File f) {
        if (f == null || !f.exists()) {
            return new JsonObject();
        }
        Reader r = null;
        try {
            r = new InputStreamReader(new FileInputStream(f), UTF8);
            JsonElement e = new JsonParser().parse(r);
            return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
        } catch (Exception ex) {
            return new JsonObject();
        } finally {
            close(r);
        }
    }

    static void write(File f, JsonObject o) {
        if (f == null || o == null) {
            return;
        }
        Writer w = null;
        try {
            File parent = f.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            w = new OutputStreamWriter(new FileOutputStream(f), UTF8);
            w.write(o.toString());
        } catch (Exception ignored) {
        } finally {
            close(w);
        }
    }

    private static void close(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }
}
