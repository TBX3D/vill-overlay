package dev.villoverlay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/** Draws the stats table + gen timers + commentary, only while a game is active. */
public final class OverlayHud {

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (!BwConfig.enabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (!GameDetector.active(mc)) {
            return;
        }
        FontRenderer fr = mc.fontRendererObj;
        if (fr == null) {
            return;
        }

        List<BwStats> players = StatsService.get().sorted();
        int rows = Math.min(players.size(), BwConfig.maxRows);
        String[] gen = GenTimer.get().lines();
        boolean showComment = BwConfig.showCommentary;
        String commentary = StatsService.get().commentary();
        List<String> alerts = ThreatAlert.get().lines();
        int alertRows = Math.min(alerts.size(), 5);

        float scale = BwConfig.hudScalePct / 100f;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1f);
        GlStateManager.enableBlend();

        final int x = BwConfig.hudX;
        final int y = BwConfig.hudY;
        final int cName = x + 2;
        final int cStar = x + 86;
        final int cFkdr = x + 114;
        final int cWlr = x + 150;
        final int cWs = x + 184;
        final int width = 210;

        int lines = alertRows + 1 + rows + gen.length + (showComment && commentary != null && !commentary.isEmpty() ? 1 : 0);
        int height = lines * 10 + 5;
        Gui.drawRect(x, y, x + width, y + height, 0x90000000);

        int ty = y + 3;
        for (int i = 0; i < alertRows; i++) {
            fr.drawStringWithShadow(alerts.get(i), cName, ty, 0xFFFFFF);
            ty += 10;
        }
        fr.drawStringWithShadow("§7§lPlayer", cName, ty, 0xFFFFFF);
        fr.drawStringWithShadow("§7✫", cStar, ty, 0xFFFFFF);
        fr.drawStringWithShadow("§7FKDR", cFkdr, ty, 0xFFFFFF);
        fr.drawStringWithShadow("§7WLR", cWlr, ty, 0xFFFFFF);
        fr.drawStringWithShadow("§7WS", cWs, ty, 0xFFFFFF);
        ty += 10;

        for (int i = 0; i < rows; i++) {
            BwStats s = players.get(i);
            String c = s.color();
            fr.drawStringWithShadow(nameCell(s, c, teamColor(mc, s.name)), cName, ty, 0xFFFFFF);
            if (s.loading) {
                fr.drawStringWithShadow("§7...", cStar, ty, 0xFFFFFF);
            } else if (s.error != null) {
                fr.drawStringWithShadow("§8err", cStar, ty, 0xFFFFFF);
            } else if (s.nicked) {
                fr.drawStringWithShadow("§dNICK", cStar, ty, 0xFFFFFF);
            } else {
                fr.drawStringWithShadow(c + s.star, cStar, ty, 0xFFFFFF);
                fr.drawStringWithShadow(c + Commentary.fmt(s.fkdr), cFkdr, ty, 0xFFFFFF);
                fr.drawStringWithShadow(c + Commentary.fmt(s.wlr), cWlr, ty, 0xFFFFFF);
                fr.drawStringWithShadow(c + (s.winstreakKnown ? String.valueOf(s.winstreak) : "?"), cWs, ty, 0xFFFFFF);
            }
            ty += 10;
        }

        for (String g : gen) {
            fr.drawStringWithShadow(g, cName, ty, 0xFFFFFF);
            ty += 10;
        }
        if (showComment && commentary != null && !commentary.isEmpty()) {
            fr.drawStringWithShadow(commentary, cName, ty, 0xFFFFFF);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n);
    }

    /**
     * Name column: a §4☠ for a blacklisted player, an optional coloured tag
     * bullet, the real ign for denicked players (with a §d* marker), and an "xN"
     * count for players seen before. The name itself takes the Bed Wars team
     * colour when the player is on a team, so teams read at a glance; otherwise
     * it falls back to a manual tag colour, then the threat (sweat) tier colour -
     * which keeps carrying the sweat signal on the stat columns. The truncation
     * budget shrinks as markers are added so the cell stays in column.
     */
    private static String nameCell(BwStats s, String threatColor, String teamColor) {
        PlayerTags.Tag tag = PlayerTags.get(s.name);
        String shown = (s.denicked && s.realName != null) ? s.realName : s.name;
        String nameColor = teamColor != null ? teamColor : (tag != null ? tag.color : threatColor);

        int budget = 12;
        StringBuilder sb = new StringBuilder();
        if (s.flagged) {
            sb.append("§4☠ ");
            budget -= 2;
        }
        if (tag != null) {
            sb.append(tag.color).append("● ");
            budget -= 2;
        }
        String denickMark = s.denicked ? "§d*" : "";
        if (s.denicked) {
            budget -= 1;
        }
        int seen = SessionHistory.get().seen(s.name);
        String seenMark = seen >= 2 ? " §8x" + seen : "";
        if (seen >= 2) {
            budget -= 2;
        }
        if (budget < 4) {
            budget = 4;
        }
        sb.append(nameColor).append(truncate(shown, budget)).append(denickMark).append(seenMark);
        return sb.toString();
    }

    /**
     * This player's Bed Wars team colour from the client scoreboard, or null if
     * they aren't on a team yet (e.g. pre-game lobby). Hypixel colours nametags
     * through the team prefix, so that's the authoritative team colour; the
     * team's chat format is only a fallback.
     */
    private static String teamColor(Minecraft mc, String name) {
        if (mc.theWorld == null) {
            return null;
        }
        Scoreboard sb = mc.theWorld.getScoreboard();
        if (sb == null) {
            return null;
        }
        ScorePlayerTeam team = sb.getPlayersTeam(name);
        if (team == null) {
            return null;
        }
        String fromPrefix = firstColorCode(team.getColorPrefix());
        if (fromPrefix != null) {
            return fromPrefix;
        }
        EnumChatFormatting f = team.getChatFormat();
        return (f != null && f.isColor()) ? f.toString() : null;
    }

    /** First §-colour code in a string (e.g. a team's nametag prefix), or null. */
    private static String firstColorCode(String s) {
        if (s == null) {
            return null;
        }
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) == '§') {
                char code = Character.toLowerCase(s.charAt(i + 1));
                if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                    return "§" + code;
                }
            }
        }
        return null;
    }
}
