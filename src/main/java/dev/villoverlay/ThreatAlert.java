package dev.villoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides who in the current lobby deserves a heads-up and surfaces it two ways:
 *  - a coloured banner line list the HUD draws above the table, and
 *  - a one-shot sound the first time a new "scary" player shows up this game.
 *
 * A player trips an alert when they're on the shared blacklist, on a danger tag
 * (sniper/cheater/...), when nicked (optional), or when they pass the
 * star/FKDR/winstreak thresholds in {@link BwConfig}. Recomputed each client
 * tick; sound fires at most once per player per game. All main-thread.
 */
public final class ThreatAlert {

    private static final ThreatAlert INSTANCE = new ThreatAlert();

    public static ThreatAlert get() {
        return INSTANCE;
    }

    private final Set<String> alertedThisGame = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private volatile List<String> lines = new ArrayList<String>();

    private ThreatAlert() {
    }

    /** Client tick: rebuild banner lines and ping once for any newly-seen alert. */
    public void evaluate(List<BwStats> sorted) {
        if (!BwConfig.alertEnabled) {
            if (!lines.isEmpty()) {
                lines = new ArrayList<String>();
            }
            return;
        }
        List<String> out = new ArrayList<String>();
        boolean newAlert = false;
        for (BwStats s : sorted) {
            if (s.loading || s.error != null) {
                continue;
            }
            String reason = reasonFor(s);
            if (reason == null) {
                continue;
            }
            out.add(banner(s, reason));
            if (alertedThisGame.add(s.name.toLowerCase())) {
                newAlert = true;
            }
        }
        lines = out;
        if (newAlert && BwConfig.alertSound) {
            playPing();
        }
    }

    /** A short reason string if this player should alert, else null. */
    private String reasonFor(BwStats s) {
        if (s.flagged) {
            return s.flagLabel != null ? s.flagLabel.toUpperCase() : "BLACKLISTED";
        }
        PlayerTags.Tag tag = PlayerTags.get(s.name);
        if (tag != null && BwConfig.alertOnTagged && PlayerTags.isDanger(tag.label)) {
            return tag.label.toUpperCase();
        }
        if (s.nicked) {
            return BwConfig.alertOnNick ? "NICK" : null;
        }
        if (BwConfig.alertStar > 0 && s.star >= BwConfig.alertStar) {
            return s.star + "✫";
        }
        if (BwConfig.alertFkdr > 0 && s.fkdr >= BwConfig.alertFkdr) {
            return Commentary.fmt(s.fkdr) + " FKDR";
        }
        if (BwConfig.alertWinstreak > 0 && s.winstreakKnown && s.winstreak >= BwConfig.alertWinstreak) {
            return s.winstreak + " WS";
        }
        return null;
    }

    private String banner(BwStats s, String reason) {
        PlayerTags.Tag tag = PlayerTags.get(s.name);
        String col = tag != null ? tag.color : "§c";
        String who = (s.denicked && s.realName != null) ? s.realName : s.name;
        return col + "⚠ " + who + " §7" + reason;
    }

    public List<String> lines() {
        return lines;
    }

    /** Call when a game ends so the next game pings fresh. */
    public void resetGame() {
        alertedThisGame.clear();
        lines = new ArrayList<String>();
    }

    private void playPing() {
        try {
            Minecraft mc = Minecraft.getMinecraft();
            String name = BwConfig.alertSoundName.trim();
            if (name.isEmpty()) {
                name = "note.pling";
            }
            mc.getSoundHandler().playSound(
                    PositionedSoundRecord.create(new ResourceLocation(name), 1.0F));
        } catch (Exception ignored) {
        }
    }
}
