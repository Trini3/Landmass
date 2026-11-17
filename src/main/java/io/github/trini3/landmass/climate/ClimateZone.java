package io.github.trini3.landmass.climate;

/**
 * Minecraft's five temperature bands. Vanilla, TerraBlender, Biomes o' Plenty and Regions Unexplored
 * all pick biomes from these same bands, which is why moving the bands moves their biomes too.
 */
public enum ClimateZone {
    HOT("hot", 'H', 0xE0B04C),
    WARM("warm", 'W', 0x9DBE3C),
    TEMPERATE("temperate", 'T', 0x3F9A4E),
    COLD("cold", 'C', 0x3E7D78),
    FROZEN("frozen", 'F', 0xE9F1F6);

    private final String displayName;
    private final char symbol;
    private final int color;

    ClimateZone(String displayName, char symbol, int color) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.color = color;
    }

    public static ClimateZone of(double temperature) {
        if (temperature >= LatitudeLayout.HOT_WARM) {
            return HOT;
        }
        if (temperature >= LatitudeLayout.WARM_TEMPERATE) {
            return WARM;
        }
        if (temperature >= LatitudeLayout.TEMPERATE_COLD) {
            return TEMPERATE;
        }
        if (temperature >= LatitudeLayout.COLD_FROZEN) {
            return COLD;
        }
        return FROZEN;
    }

    public String displayName() {
        return this.displayName;
    }

    public char symbol() {
        return this.symbol;
    }

    public int color() {
        return this.color;
    }
}
