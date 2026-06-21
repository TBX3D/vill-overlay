package dev.villoverlay;

/**
 * Bed Wars star (prestige level) from raw {@code stats.Bedwars.Experience}.
 *
 * Ported 1:1 from Amund211/prism (src/prism/calc.py, public domain), which is
 * the accepted accurate formula - much better than the {@code bedwars_level}
 * achievement, which lags and rounds.
 */
public final class BwLevel {

    private static final int LEVELS_PER_PRESTIGE = 100;
    private static final int LEVEL_COST = 5000;
    // Exp for the first four (cheaper) levels of every prestige.
    private static final int[] EASY_LEVEL_COSTS = {500, 1000, 2000, 3500};
    private static final int EASY_EXP = 500 + 1000 + 2000 + 3500; // 7000
    private static final int PRESTIGE_EXP =
            EASY_EXP + (LEVELS_PER_PRESTIGE - EASY_LEVEL_COSTS.length) * LEVEL_COST; // 487000

    private BwLevel() {
    }

    /** Integer star for the given total bedwars experience. */
    public static int fromExp(long exp) {
        if (exp < 0) {
            exp = 0;
        }
        long levels = (exp / PRESTIGE_EXP) * LEVELS_PER_PRESTIGE;
        long rest = exp % PRESTIGE_EXP;

        for (int i = 0; i < EASY_LEVEL_COSTS.length; i++) {
            if (rest >= EASY_LEVEL_COSTS[i]) {
                levels++;
                rest -= EASY_LEVEL_COSTS[i];
            } else {
                break;
            }
        }
        levels += rest / LEVEL_COST;
        return (int) levels;
    }
}
