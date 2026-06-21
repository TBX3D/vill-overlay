package dev.villoverlay;

import com.google.gson.JsonObject;

/**
 * Turns a Hypixel "player" object into {@link BwStats}. Field paths and the
 * star-from-Experience approach are verified against Amund211/prism. Shared by
 * every provider, since Antisniper/Polsu-style proxies mirror this same shape.
 */
public final class HypixelParse {

    private HypixelParse() {
    }

    public static BwStats parse(JsonObject player, String name, String uuid) {
        if (player == null) {
            return BwStats.nick(name);
        }
        JsonObject stats = obj(player, "stats");
        JsonObject bw = stats == null ? null : obj(stats, "Bedwars");
        if (bw == null) {
            // Real account, but never played bedwars.
            return BwStats.of(name, uuid, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, true);
        }

        int star = BwLevel.fromExp(getInt(bw, "Experience", 500));
        int finalKills = getInt(bw, "final_kills_bedwars", 0);
        int finalDeaths = getInt(bw, "final_deaths_bedwars", 0);
        int kills = getInt(bw, "kills_bedwars", 0);
        int deaths = getInt(bw, "deaths_bedwars", 0);
        int wins = getInt(bw, "wins_bedwars", 0);
        int losses = getInt(bw, "losses_bedwars", 0);
        int bedsBroken = getInt(bw, "beds_broken_bedwars", 0);
        int bedsLost = getInt(bw, "beds_lost_bedwars", 0);

        int winstreak;
        boolean winstreakKnown;
        if (bw.has("winstreak") && bw.get("winstreak").isJsonPrimitive()) {
            winstreak = getInt(bw, "winstreak", 0);
            winstreakKnown = true;
        } else if (wins == 0) {
            // Field only appears after a first win, so no wins => streak 0 (known).
            winstreak = 0;
            winstreakKnown = true;
        } else {
            winstreak = -1;
            winstreakKnown = false;
        }

        return BwStats.of(name, uuid, star, finalKills, finalDeaths, kills, deaths,
                wins, losses, bedsBroken, bedsLost, winstreak, winstreakKnown);
    }

    private static JsonObject obj(JsonObject parent, String field) {
        return parent.has(field) && parent.get(field).isJsonObject() ? parent.getAsJsonObject(field) : null;
    }

    private static int getInt(JsonObject o, String field, int def) {
        try {
            if (o.has(field) && o.get(field).isJsonPrimitive()) {
                return o.get(field).getAsInt();
            }
        } catch (Exception ignored) {
        }
        return def;
    }
}
