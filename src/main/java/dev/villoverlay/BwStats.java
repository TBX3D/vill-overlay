package dev.villoverlay;

/**
 * One lobby player's normalized Bed Wars stats, plus a derived "threat" tier
 * used for colouring and sorting. Immutable; built through the static factories.
 */
public final class BwStats {

    /** Display name (as seen in tab). */
    public final String name;
    /** Undashed uuid, or null if we never resolved one. */
    public final String uuid;

    public final boolean loading;   // fetch not finished yet
    public final boolean nicked;    // Hypixel returned player == null
    public final String error;      // non-null => failed to fetch

    public final int star;
    public final double fkdr;
    public final double wlr;
    public final double kdr;
    public final double bblr;
    public final int finalKills;
    public final int finalDeaths;
    public final int wins;
    public final int losses;
    public final int bedsBroken;
    public final int winstreak;       // -1 if unknown / hidden
    public final boolean winstreakKnown;

    /** 0 (easy) .. 4 (deadly). 5 used as a sentinel for nick/unknown. */
    public final int threat;
    /** Sort key: higher = scarier. */
    public final double index;

    /** True when a nicked player was resolved to a real account via {@link Denicker}. */
    public final boolean denicked;
    /** Real ign behind a nick, when {@link #denicked}; otherwise null. */
    public final String realName;

    private BwStats(String name, String uuid, boolean loading, boolean nicked, String error,
                    int star, double fkdr, double wlr, double kdr, double bblr,
                    int finalKills, int finalDeaths, int wins, int losses, int bedsBroken,
                    int winstreak, boolean winstreakKnown, boolean denicked, String realName) {
        this.name = name;
        this.uuid = uuid;
        this.loading = loading;
        this.nicked = nicked;
        this.error = error;
        this.star = star;
        this.fkdr = fkdr;
        this.wlr = wlr;
        this.kdr = kdr;
        this.bblr = bblr;
        this.finalKills = finalKills;
        this.finalDeaths = finalDeaths;
        this.wins = wins;
        this.losses = losses;
        this.bedsBroken = bedsBroken;
        this.winstreak = winstreak;
        this.winstreakKnown = winstreakKnown;
        this.threat = threatOf(nicked, error != null, star, fkdr);
        this.index = (error != null || loading) ? -1 : star * (fkdr * fkdr);
        this.denicked = denicked;
        this.realName = realName;
    }

    public static BwStats loading(String name, String uuid) {
        return new BwStats(name, uuid, true, false, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, false, false, null);
    }

    public static BwStats nick(String name) {
        return new BwStats(name, null, false, true, null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, false, false, null);
    }

    public static BwStats error(String name, String uuid, String msg) {
        return new BwStats(name, uuid, false, false, msg, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, false, false, null);
    }

    public static BwStats of(String name, String uuid, int star,
                             int finalKills, int finalDeaths, int kills, int deaths,
                             int wins, int losses, int bedsBroken, int bedsLost,
                             int winstreak, boolean winstreakKnown) {
        return new BwStats(name, uuid, false, false, null, star,
                div(finalKills, finalDeaths), div(wins, losses), div(kills, deaths), div(bedsBroken, bedsLost),
                finalKills, finalDeaths, wins, losses, bedsBroken, winstreak, winstreakKnown, false, null);
    }

    /** This same stat line, re-flagged as a denicked player with their real ign. */
    public BwStats asDenicked(String realName) {
        return new BwStats(name, uuid, loading, nicked, error, star, fkdr, wlr, kdr, bblr,
                finalKills, finalDeaths, wins, losses, bedsBroken, winstreak, winstreakKnown, true, realName);
    }

    /** prism-style ratio: x/y, but x when y == 0 (avoids div-by-zero, treats as "all"). */
    public static double div(int x, int y) {
        return y == 0 ? x : (double) x / (double) y;
    }

    private static int threatOf(boolean nicked, boolean err, int star, double fkdr) {
        if (nicked || err) {
            return 5;
        }
        // Threat from FKDR, bumped a tier for high prestige.
        int t;
        if (fkdr < 1) {
            t = 0;
        } else if (fkdr < 3) {
            t = 1;
        } else if (fkdr < 6) {
            t = 2;
        } else if (fkdr < 10) {
            t = 3;
        } else {
            t = 4;
        }
        if (star >= 500 && t < 4) {
            t++;
        }
        return t;
    }

    /** §-colour for this player's threat tier. */
    public String color() {
        if (loading) {
            return "§7";
        }
        if (error != null) {
            return "§8";
        }
        if (nicked) {
            return "§d";
        }
        switch (threat) {
            case 0: return "§a";
            case 1: return "§f";
            case 2: return "§e";
            case 3: return "§6";
            default: return "§c";
        }
    }
}
