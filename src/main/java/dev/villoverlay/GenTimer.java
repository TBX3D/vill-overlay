package dev.villoverlay;

/**
 * Diamond/emerald generator countdowns. Best-effort: synced to the detected
 * game-start chat line and counted off the configurable spawn intervals - it
 * doesn't read world item entities, so treat it as a guide, and tune
 * diamondGenSeconds/emeraldGenSeconds per mode in the config.
 */
public final class GenTimer {

    private static final GenTimer INSTANCE = new GenTimer();

    public static GenTimer get() {
        return INSTANCE;
    }

    private volatile long gameStartMs = 0L; // 0 = not started

    private GenTimer() {
    }

    public void onGameStart() {
        gameStartMs = System.currentTimeMillis();
    }

    public void reset() {
        gameStartMs = 0L;
    }

    public String[] lines() {
        if (!BwConfig.showGenTimers) {
            return new String[0];
        }
        if (gameStartMs == 0L) {
            return new String[]{"§b♦ Gens §7waiting for game start"};
        }
        long elapsed = (System.currentTimeMillis() - gameStartMs) / 1000L;
        long diamond = remaining(elapsed, BwConfig.diamondGenSeconds);
        long emerald = remaining(elapsed, BwConfig.emeraldGenSeconds);
        String time = String.format("%d:%02d", elapsed / 60, elapsed % 60);
        return new String[]{
                "§b♦ Diamond §fin " + diamond + "s   §a❖ Emerald §fin " + emerald + "s",
                "§7game time §f" + time
        };
    }

    private static long remaining(long elapsed, int interval) {
        if (interval <= 0) {
            return 0;
        }
        return interval - (elapsed % interval);
    }
}
