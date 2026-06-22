package dev.villoverlay;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * Settings, persisted through Forge's config system to
 * {@code config/villoverlay.cfg}. Everything can also be changed in-game with
 * {@code /vill} (see {@link BwCommand}).
 */
public final class BwConfig {

    private static final String CAT = Configuration.CATEGORY_GENERAL;

    public static final String DEFAULT_AI_PROMPT =
            "You are a Hypixel Bed Wars scout. Below are the players in my current game with their "
                    + "stats (star, FKDR, WLR, winstreak). In 2-3 short plain-text lines, no markdown and "
                    + "no emoji, call out who the biggest threats are, who is free, and any nicked players. "
                    + "Be punchy.";

    // --- master ---
    public static boolean enabled = true;

    // --- stats source ---
    /** "hypixel" = official api (your own key). "proxy" = any third-party that returns
     *  Hypixel player-format JSON (Antisniper/Polsu/etc) at a configurable URL. */
    public static String provider = "hypixel";
    public static String hypixelKey = "";
    /** URL template for the proxy provider; {key} and {uuid} are substituted. */
    public static String proxyUrl = "https://api.antisniper.net/v2/player?key={key}&uuid={uuid}";
    public static String proxyKey = "";

    // --- timing ---
    public static int refreshSeconds = 30;

    // --- hud ---
    public static int hudX = 2;
    public static int hudY = 2;
    public static int hudScalePct = 100;
    public static int maxRows = 16;
    public static boolean showGenTimers = true;
    public static boolean showCommentary = true;

    // --- generators (approximate spawn intervals, seconds) ---
    public static int diamondGenSeconds = 30;
    public static int emeraldGenSeconds = 65;

    // --- ai commentary (runs on your Claude subscription via the CLI, no api credits) ---
    public static String claudePath = "claude";
    public static String aiPrompt = DEFAULT_AI_PROMPT;

    // --- denicking (resolve nicked players to a real account; off by default) ---
    public static boolean denickEnabled = false;
    /** URL template; {key} and {name} are substituted. UNVERIFIED default - confirm with your service. */
    public static String denickUrl = "https://api.antisniper.net/v2/denick?key={key}&nick={name}";
    /** Denick service key. Falls back to proxyKey when blank. */
    public static String denickKey = "";

    // --- shared blacklist (community cheater/sniper db; off by default) ---
    public static boolean blacklistEnabled = false;
    /** URL template; {key} and {uuid} are substituted. Default = Urchin's player route. */
    public static String blacklistUrl = "https://urchin.ws/player/{uuid}?key={key}&sources=GAME,CHAT";
    /** Blacklist service key. Falls back to proxyKey when blank. */
    public static String blacklistKey = "";

    // --- threat alerts (banner + one-shot sound for scary players) ---
    public static boolean alertEnabled = true;
    public static boolean alertSound = true;
    public static String alertSoundName = "note.pling";
    public static int alertStar = 300;       // 0 = off
    public static int alertFkdr = 8;         // whole-number FKDR; 0 = off
    public static int alertWinstreak = 0;    // 0 = off
    public static boolean alertOnTagged = true;
    public static boolean alertOnNick = false;

    // --- session history (local "seen this player before" log) ---
    public static boolean trackHistory = true;

    private static Configuration config;

    private BwConfig() {
    }

    public static void load(File file) {
        config = new Configuration(file);
        sync();
    }

    private static void sync() {
        enabled = config.getBoolean("enabled", CAT, true,
                "Master switch. The overlay is also only ever active while you're in a Bed Wars game.");
        provider = config.getString("provider", CAT, "hypixel",
                "\"hypixel\" = official Hypixel API with your own key (recommended; free key at "
                        + "developer.hypixel.net). \"proxy\" = a third-party service that returns Hypixel "
                        + "player-format JSON (set proxyUrl + proxyKey).");
        hypixelKey = config.getString("hypixelKey", CAT, "",
                "Your Hypixel API key (developer.hypixel.net). Stored in plain text - keep this file private.");
        proxyUrl = config.getString("proxyUrl", CAT, proxyUrl,
                "Proxy provider URL template. {key} and {uuid} are substituted. Must return Hypixel "
                        + "player-format JSON ({success, player}). The default points at Antisniper but is "
                        + "UNVERIFIED - confirm the exact path with your service before relying on it.");
        proxyKey = config.getString("proxyKey", CAT, "",
                "API key for the proxy provider (e.g. an Antisniper key from their Discord).");
        refreshSeconds = config.getInt("refreshSeconds", CAT, 30, 10, 600,
                "Minimum seconds between stat refreshes per player. Keeps you well under API limits.");
        hudX = config.getInt("hudX", CAT, 2, 0, 10000, "HUD top-left X (before scaling).");
        hudY = config.getInt("hudY", CAT, 2, 0, 10000, "HUD top-left Y (before scaling).");
        hudScalePct = config.getInt("hudScalePct", CAT, 100, 30, 300, "HUD scale, percent.");
        maxRows = config.getInt("maxRows", CAT, 16, 1, 16, "Max player rows to draw.");
        showGenTimers = config.getBoolean("showGenTimers", CAT, true, "Draw diamond/emerald gen timers.");
        showCommentary = config.getBoolean("showCommentary", CAT, true, "Draw the heuristic commentary line.");
        diamondGenSeconds = config.getInt("diamondGenSeconds", CAT, 30, 1, 600,
                "Approx seconds between diamond spawns (best-effort; tune to your modes).");
        emeraldGenSeconds = config.getInt("emeraldGenSeconds", CAT, 65, 1, 600,
                "Approx seconds between emerald spawns (best-effort; tune to your modes).");
        claudePath = config.getString("claudePath", CAT, claudePath,
                "Path to the claude CLI for AI commentary. Runs on your Claude subscription login "
                        + "(claude -p), so it uses ZERO metered API credits.");
        aiPrompt = config.getString("aiPrompt", CAT, DEFAULT_AI_PROMPT,
                "Prompt prepended to the lobby stats for AI commentary.");

        denickEnabled = config.getBoolean("denickEnabled", CAT, false,
                "Try to resolve nicked players to a real account via denickUrl. Off by default; "
                        + "requires a third-party denick service.");
        denickUrl = config.getString("denickUrl", CAT, denickUrl,
                "Denick URL template. {key} and {name} are substituted. The default points at "
                        + "Antisniper but is UNVERIFIED - confirm the exact path/response shape with your "
                        + "service. Parsing accepts {player:{uuid,ign}} or top-level {uuid,ign}.");
        denickKey = config.getString("denickKey", CAT, "",
                "Key for the denick service. Falls back to proxyKey when blank.");

        blacklistEnabled = config.getBoolean("blacklistEnabled", CAT, false,
                "Flag known cheaters/snipers from a shared community blacklist via blacklistUrl. "
                        + "Off by default; requires a third-party blacklist service.");
        blacklistUrl = config.getString("blacklistUrl", CAT, blacklistUrl,
                "Blacklist URL template. {key} and {uuid} are substituted. The default points at "
                        + "Urchin (urchin.ws), whose response is {uuid, tags:[{type,reason}], ...} - a "
                        + "non-empty tags array means flagged. Parsing also accepts a top-level type "
                        + "string or a true flagged/cheater/sniper boolean.");
        blacklistKey = config.getString("blacklistKey", CAT, "",
                "Key for the blacklist service (e.g. an Urchin key from their Discord). "
                        + "Falls back to proxyKey when blank.");

        alertEnabled = config.getBoolean("alertEnabled", CAT, true,
                "Master switch for threat alerts (the HUD banner + alert sound).");
        alertSound = config.getBoolean("alertSound", CAT, true,
                "Play a sound the first time a scary player appears in a game.");
        alertSoundName = config.getString("alertSoundName", CAT, "note.pling",
                "Sound event id for the alert (e.g. note.pling, random.orb, mob.wither.spawn).");
        alertStar = config.getInt("alertStar", CAT, 300, 0, 100000,
                "Alert when a player's star is >= this. 0 disables the star trigger.");
        alertFkdr = config.getInt("alertFkdr", CAT, 8, 0, 100000,
                "Alert when a player's FKDR is >= this (whole number). 0 disables the FKDR trigger.");
        alertWinstreak = config.getInt("alertWinstreak", CAT, 0, 0, 100000,
                "Alert when a player's known winstreak is >= this. 0 disables the winstreak trigger.");
        alertOnTagged = config.getBoolean("alertOnTagged", CAT, true,
                "Alert when a player carries a danger tag (sniper/cheater/blacklist/...).");
        alertOnNick = config.getBoolean("alertOnNick", CAT, false,
                "Also alert on any nicked player (can be noisy).");

        trackHistory = config.getBoolean("trackHistory", CAT, true,
                "Keep a local count of players you've seen before (config/villoverlay-history.json).");

        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void setString(String name, String value) {
        if (config == null) {
            return;
        }
        config.get(CAT, name, "").set(value);
        config.save();
        sync();
    }

    public static void setBool(String name, boolean value) {
        if (config == null) {
            return;
        }
        config.get(CAT, name, false).set(value);
        config.save();
        sync();
    }

    public static void setInt(String name, int value) {
        if (config == null) {
            return;
        }
        config.get(CAT, name, 0).set(value);
        config.save();
        sync();
    }
}
