package dev.villoverlay;

/**
 * Bed Wars prestige colouring for a star. Hypixel paints the star one fixed
 * colour per 100-level prestige (Stone grey, Iron white, Gold, Diamond, Emerald,
 * Sapphire, Ruby, Crystal, Opal, Amethyst), then rainbow from 1000 on.
 * {@link #format} returns the star number wrapped in the matching section colour
 * code(s); the HUD column header already carries the ✫, so no symbol is added.
 *
 * The 1100+ "prime" prestiges have their own per-digit palettes upstream; here
 * everything from 1000 up renders rainbow, which still reads as a four-digit
 * star without hand-coding the higher prestige tables.
 */
public final class Prestige {

    /** Section colour per prestige bracket; index = star / 100, capped at 900. */
    private static final String[] COLORS = {
            "§7", // 0   Stone    grey
            "§f", // 100 Iron     white
            "§6", // 200 Gold     gold
            "§b", // 300 Diamond  aqua
            "§2", // 400 Emerald  dark green
            "§3", // 500 Sapphire dark aqua
            "§4", // 600 Ruby     dark red
            "§d", // 700 Crystal  light purple
            "§9", // 800 Opal     blue
            "§5", // 900 Amethyst dark purple
    };

    /** Per-digit palette for the 1000+ rainbow prestige. */
    private static final char[] RAINBOW = {'c', '6', 'e', 'a', 'b', 'd'};

    private Prestige() {
    }

    /** The star number coloured by its prestige bracket; no trailing symbol. */
    public static String format(int star) {
        if (star >= 1000) {
            return rainbow(star);
        }
        int bracket = star / 100;
        if (bracket < 0) {
            bracket = 0;
        } else if (bracket >= COLORS.length) {
            bracket = COLORS.length - 1;
        }
        return COLORS[bracket] + star;
    }

    /** Colour each digit through the palette in turn, as Hypixel does at 1000+. */
    private static String rainbow(int star) {
        String digits = Integer.toString(star);
        StringBuilder sb = new StringBuilder(digits.length() * 3);
        for (int i = 0; i < digits.length(); i++) {
            sb.append('§').append(RAINBOW[i % RAINBOW.length]).append(digits.charAt(i));
        }
        return sb.toString();
    }
}
