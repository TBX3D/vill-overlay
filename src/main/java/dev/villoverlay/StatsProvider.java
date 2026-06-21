package dev.villoverlay;

import com.google.gson.JsonObject;

/** A source of Hypixel player data. Implementations do blocking HTTP off the main thread. */
public interface StatsProvider {
    String id();

    /** The Hypixel "player" object for this uuid, or null when the player isn't found (nicked). */
    JsonObject fetchPlayer(String uuid) throws StatsException;
}
