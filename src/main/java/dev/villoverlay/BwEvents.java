package dev.villoverlay;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.List;

/** Client tick (roster scan + throttled refresh + keybinds) and chat parsing (game start). */
public final class BwEvents {

    private boolean wasActive = false;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        BwOverlayMod.handleKeys();

        if (!BwConfig.enabled) {
            return;
        }
        boolean active = GameDetector.active(mc);
        if (active) {
            List<PlayerRef> roster = RosterTracker.scanTabList(mc);
            StatsService.get().updateRoster(roster);
            StatsService.get().maybeRefresh(BwOverlayMod.pool(), BwOverlayMod.provider());
            SessionHistory.get().observe(roster);
            ThreatAlert.get().evaluate(StatsService.get().sorted());
        } else if (wasActive) {
            GenTimer.get().reset();
            ThreatAlert.get().resetGame();
            SessionHistory.get().endGame();
        }
        wasActive = active;
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent e) {
        if (!BwConfig.enabled || e.message == null) {
            return;
        }
        // Only honor the game-start line while we're actually in a live Bed Wars
        // game (world + Hypixel + BED WARS sidebar). Without this, a lobby tip,
        // another gamemode, or someone just typing the phrase in chat could start
        // the gen countdown early. ClientChatReceivedEvent runs on the client
        // thread, so the live-object reads in GameDetector are safe here.
        if (!GameDetector.active(Minecraft.getMinecraft())) {
            return;
        }
        if (isGameStartLine(e.message.getUnformattedText())) {
            GenTimer.get().onGameStart();
        }
    }

    /**
     * True only for Hypixel's centered game-start broadcast. On Hypixel every
     * player message is prefixed with a "[rank] name: " sender, so it can never
     * be the first thing on the line - anchoring the phrase to the start of the
     * trimmed text rejects anyone typing it in the pre-game lobby, while the real
     * (space-centered) server broadcast still matches once trimmed.
     */
    private static boolean isGameStartLine(String text) {
        return text != null && text.trim().startsWith("Protect your bed");
    }
}
