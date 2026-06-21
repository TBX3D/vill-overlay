package dev.villoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;

/**
 * Decides whether the overlay should be doing anything at all. Everything here
 * reads live client objects, so it must be called from the main (client) thread.
 *
 * The overlay is only "active" while you are connected to Hypixel AND the
 * sidebar scoreboard says BED WARS - that's what keeps it from running 24/7.
 */
public final class GameDetector {

    private GameDetector() {
    }

    public static boolean onHypixel(Minecraft mc) {
        if (mc.getCurrentServerData() == null) {
            return false;
        }
        String ip = mc.getCurrentServerData().serverIP;
        return ip != null && ip.toLowerCase().contains("hypixel");
    }

    public static boolean inBedwars(Minecraft mc) {
        return sidebarTitle(mc).toUpperCase().contains("BED WARS");
    }

    public static boolean active(Minecraft mc) {
        return mc.theWorld != null && onHypixel(mc) && inBedwars(mc);
    }

    /** Sidebar objective title with §-codes stripped, or "" if none. */
    public static String sidebarTitle(Minecraft mc) {
        if (mc.theWorld == null) {
            return "";
        }
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) {
            return "";
        }
        ScoreObjective obj = sb.getObjectiveInDisplaySlot(1);
        if (obj == null) {
            return "";
        }
        return strip(obj.getDisplayName());
    }

    static String strip(String s) {
        return s == null ? "" : s.replaceAll("§.", "");
    }
}
