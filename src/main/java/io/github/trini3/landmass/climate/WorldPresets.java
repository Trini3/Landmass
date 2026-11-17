package io.github.trini3.landmass.climate;

import io.github.trini3.landmass.continents.CoastErosion;
import io.github.trini3.landmass.continents.CoastFeatures;
import io.github.trini3.landmass.continents.ContinentLayout;
import io.github.trini3.landmass.continents.Shores;
import io.github.trini3.landmass.continents.ContinentLayout.SizeRange;
import io.github.trini3.landmass.world.WorldFeatures;

import java.util.Locale;
import java.util.Optional;

/**
 * The built-in presets on the Customize screen's Presets tab. Each one is the default world resized: every length in the
 * settings is multiplied by a land scale (continents, climate zones, coastal features...), so landmasses, climate bands and
 * the features along their coasts keep sensible proportions to each other at any size.
 *
 * <p>Big things grow with the land scale itself. Small coastal features grow more slowly (by a power of it), the way real
 * sand spits and straits are not proportionally as large as the continents they sit on; the bar and bridge spacing in
 * {@code ContinentShapes} follows those lengths, so how often features appear along a coast also adjusts with them.
 * Chances (bridge, bar and strait amounts) are per landmass pair or per stretch of coast, so they stay at 100%.
 */
public enum WorldPresets {
    // Polar distance is from the equator to the frozen zone; Realistic's frozen zone then reaches the pole 10 million blocks out.
    // Mountain % is 100 everywhere for now: until the landscape overhaul, bigger values only lift
    // land high above coasts and rivers. Earlier values: 400, 300, 500, 800, 2500.
    // land scale, polar distance, humidity zone (blocks), biome %, coast %, mountain %, land share, real-world zone proportions,
    // band waviness, then for L its own sizes (continents, large islands, archipelagos, small islands) in place of the scaled ones
    DEFAULT(1.0, 100_000, 400, 100, 100, 100, 0.35, false, LatitudeLayout.DEFAULT_WAVINESS),
    VANILLA(1.0 / 6.0, 8_000, 400, 100, 100, 100, 0.5, false, LatitudeLayout.DEFAULT_WAVINESS),
    LARGE_BIOMES(4.0 / 6.0, 32_000, 1_600, 400, 400, 100, 0.5, false, LatitudeLayout.DEFAULT_WAVINESS),
    // Between Large Biomes and XL: landmasses 16,000 to 72,000 blocks, islands and archipelagos up to 10,000, small islands up to
    // 3,000. The land scale (2.8, the middle of that continent range against the default's) sizes the coastal features.
    L(2.8, 120_000, 3_000, 500, 400, 100, 0.35, false, 1.5,
            new SizeRange(16_000, 72_000), new SizeRange(2_800, 10_000), new SizeRange(3_000, 10_000), new SizeRange(300, 3_000)),
    // 2026-11-17: biome size, humidity zones and waviness raised for L to 3XL (were 250/2,500/1.0, 400/5,500/1.0, 600/12,000/1.0).
    XL(4.0, 400_000, 5_000, 700, 300, 100, 0.35, false, 1.75),
    // Pole about 1.2 million blocks out (polar distance x 1.25); landmasses scaled to match, between XL and 3XL.
    XXL(9.6, 960_000, 10_000, 900, 500, 100, 0.34, false, 2.0),
    XXXL(25.0, 2_500_000, 20_000, 1_200, 800, 100, 0.33, false, 2.5),
    REALISTIC(200.0, 7_400_000, 150_000, 2_500, 2_500, 100, 0.29, true, LatitudeLayout.DEFAULT_WAVINESS);

    // Default sizes that are scaled.
    private static final ContinentLayout BASE = ContinentLayout.DEFAULT;
    private static final CoastFeatures COAST = CoastFeatures.DEFAULT;

    private final double land;
    private final int polarDistance;
    private final int humidityZone;
    private final int biomePercent;
    private final int coastPercent;
    private final int mountainPercent;
    private final double landShare;
    private final boolean earthZones;
    private final double waviness;
    // Sizes chosen by hand instead of scaled from the default's; null = scaled.
    private final SizeRange continentSize;
    private final SizeRange largeIslandSize;
    private final SizeRange archipelagoSize;
    private final SizeRange islandSize;

    WorldPresets(double land, int polarDistance, int humidityZone, int biomePercent, int coastPercent, int mountainPercent, double landShare,
                 boolean earthZones, double waviness) {
        this(land, polarDistance, humidityZone, biomePercent, coastPercent, mountainPercent, landShare, earthZones, waviness, null, null, null, null);
    }

    WorldPresets(double land, int polarDistance, int humidityZone, int biomePercent, int coastPercent, int mountainPercent, double landShare,
                 boolean earthZones, double waviness, SizeRange continentSize, SizeRange largeIslandSize, SizeRange archipelagoSize,
                 SizeRange islandSize) {
        this.waviness = waviness;
        this.continentSize = continentSize;
        this.largeIslandSize = largeIslandSize;
        this.archipelagoSize = archipelagoSize;
        this.islandSize = islandSize;
        this.land = land;
        this.polarDistance = polarDistance;
        this.humidityZone = humidityZone;
        this.biomePercent = biomePercent;
        this.coastPercent = coastPercent;
        this.mountainPercent = mountainPercent;
        this.landShare = landShare;
        this.earthZones = earthZones;
    }

    public String id() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** How much bigger than the default world's this preset's landmasses are. */
    public double landScale() {
        return this.land;
    }

    /**
     * These settings with this preset's climate, sizes, land and features. What the player set elsewhere (the dimensions,
     * the axis and equator, tropics and other config-only values) is kept.
     */
    public WorldClimateSettings apply(WorldClimateSettings base) {
        LatitudeLayout old = base.latitude();
        int[] widths = this.zoneWidths();
        LatitudeLayout zoned = old.withZoneWidths(widths[0], widths[1], widths[2], widths[3], widths[4]);
        // Borders wander and mix over the same share of the polar distance as in the default world (6% and 2%).
        double bandScale = this.polarDistance / (double) LatitudeLayout.DEFAULT_POLAR_DISTANCE;
        LatitudeLayout latitude = new LatitudeLayout(zoned.axis(), zoned.equator(), zoned.polarDistance(), zoned.poles(), zoned.zones(),
                zoned.tropics(), zoned.desertStrength(), LatitudeLayout.DEFAULT_BORDER_WANDER * bandScale,
                LatitudeLayout.DEFAULT_BORDER_MIXING * bandScale, zoned.localVariation(), zoned.customProfile(), this.waviness,
                LatitudeLayout.DEFAULT_MEANDER, LatitudeLayout.DEFAULT_DESERT_SHARE);
        // Landmass leaves coast size to vanilla or the terrain mod, so a preset keeps the Coast Size Override as it was, and keeps
        // Custom Landmass Generation and the coastline features switch too.
        return new WorldClimateSettings(base.enabled(), base.dimensions(), latitude,
                this.humidityZone / (double) WorldClimateSettings.VANILLA_HUMIDITY_ZONE, this.biomePercent / 100.0,
                this.continents().withEnabled(base.continents().enabled()), base.coastline(), base.coast(), WorldFeatures.DEFAULT,
                this.deviation(), base.biomeTerrain());
    }

    /** How much wider than vanilla's this preset's coasts are (its coast_scale). */
    public double coastScale() {
        return this.coastPercent / 100.0;
    }

    /** Climate deviation patches: as common as by default, sized like straits (they grow a little more slowly than the land). */
    public ClimateDeviation deviation() {
        ClimateDeviation base = ClimateDeviation.DEFAULT;
        return base.withSize(clamp(base.size() * this.grow(0.75), 200, 200_000));
    }

    /** Zone widths in blocks: hot, warm, temperate, cold, frozen. */
    public int[] zoneWidths() {
        // Earth: tropics to 23.5 degrees, subtropics to 35, temperate to 55, subpolar to 66.5, polar to 90.
        double[] shares = this.earthZones ? new double[]{23.5, 11.5, 20, 11.5, 23.5} : new double[]{20, 25, 30, 25, 25};
        double fourZones = shares[0] + shares[1] + shares[2] + shares[3];
        int[] widths = new int[5];
        for (int i = 0; i < 5; i++) {
            widths[i] = (int) Math.round(this.polarDistance * shares[i] / fourZones);
        }
        return widths;
    }

    public ContinentLayout continents() {
        // Small islands are the small end of the scale, so they grow more slowly than continents.
        CoastFeatures coast = new CoastFeatures(COAST.bridgeAmount(),
                range(COAST.bridgeWidth(), this.grow(0.75), 16, CoastFeatures.MAX_BRIDGE_WIDTH),
                // Isthmuses join landmasses across gaps that grow with the land, so their reach does too.
                clamp(COAST.bridgeLength() * this.land, CoastFeatures.MIN_BRIDGE_LENGTH, CoastFeatures.MAX_BRIDGE_LENGTH),
                COAST.chainShare(), COAST.barAmount(),
                clamp(COAST.barWidth() * this.grow(0.5), 8, CoastFeatures.MAX_BAR_WIDTH),
                clamp(COAST.barLength() * this.grow(0.5), 300, CoastFeatures.MAX_BAR_LENGTH),
                COAST.bridgeIslets(), COAST.lagoonShare(), COAST.barIslets(), COAST.straitAmount(),
                range(COAST.straitWidth(), this.grow(0.75), 30, CoastFeatures.MAX_STRAIT_WIDTH));
        return new ContinentLayout(true,
                this.continentSize != null ? this.continentSize : range(BASE.continentSize(), this.land, ContinentLayout.MIN_CONTINENT, ContinentLayout.MAX_CONTINENT),
                this.landShare, BASE.islandBiomeScale(),
                this.largeIslandSize != null ? this.largeIslandSize
                        : range(BASE.largeIslandSize(), this.land, ContinentLayout.MIN_LARGE_ISLAND, ContinentLayout.MAX_LARGE_ISLAND),
                BASE.largeIslandAmount(),
                this.archipelagoSize != null ? this.archipelagoSize
                        : range(BASE.archipelagoSize(), this.land, ContinentLayout.MIN_ARCHIPELAGO, ContinentLayout.MAX_ARCHIPELAGO),
                BASE.archipelagoAmount(),
                this.islandSize != null ? this.islandSize : range(BASE.islandSize(), this.grow(0.6), 30, ContinentLayout.MAX_ISLAND),
                coast, this.erosion())
                // Stretches of one kind of shore grow with the coastline detail.
                .withShores(Shores.DEFAULT.withStretch(clamp(Shores.DEFAULT.stretch() * this.grow(0.75), 300, Shores.MAX_STRETCH)));
    }

    /**
     * Coastal erosion. Erosion already grows with each landmass (since 0.17.0 without a size cap), but Minecraft's own
     * continentalness adds coastline detail a few thousand blocks across, which is a lot on small landmasses and next to
     * nothing on huge ones, so bigger worlds get a little more erosion to make up for it.
     */
    private CoastErosion erosion() {
        CoastErosion base = CoastErosion.DEFAULT;
        if (this.land <= 1.0) {
            return base;
        }
        double steps = Math.log10(this.land);
        // Whole percent, as the Coasts tab shows them.
        double intensity = percent(Math.min(2.0, 1.0 + 0.15 * steps));
        return new CoastErosion(percent(Math.min(0.9, base.mainlandRate() + 0.06 * steps)), intensity, base.mainlandVariation(),
                percent(Math.min(0.9, base.islandRate() + 0.06 * steps)), intensity, base.islandVariation());
    }

    private static double percent(double value) {
        return Math.round(value * 100) / 100.0;
    }

    /** The land scale to a power: how much a feature that grows more slowly than the land grows. */
    private double grow(double power) {
        return Math.pow(this.land, power);
    }

    private static SizeRange range(SizeRange base, double scale, int lowest, int highest) {
        return new SizeRange(clamp(base.min() * scale, lowest, highest), clamp(base.max() * scale, lowest, highest));
    }

    /** Rounded to two significant figures (like the sliders) and kept inside the recommended range. */
    private static int clamp(double value, int lowest, int highest) {
        double unit = value < 100 ? 1 : Math.pow(10, Math.floor(Math.log10(value)) - 1);
        long rounded = Math.round(Math.round(value / unit) * unit);
        return Math.clamp(rounded, lowest, highest);
    }
}
