package io.github.trini3.landmasslite.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmasslite.util.CodecUtil;

import java.util.Locale;

/**
 * Coastal erosion: stretches of coast worn into jagged headlands, coves, bays and narrow inlets, so landmasses look less
 * rounded. Mainland (continents) and islands (large islands, archipelagos and scattered islands) have their own settings.
 *
 * @param mainlandRate      the part of the continents' coastline, 0 to 1, that is eroded
 * @param mainlandIntensity how deep and rugged the erosion is on continents, compared with the default (1); 0 for none
 * @param mainlandVariation how much the intensity changes along the continents' coasts, 0 to 1: at 0.5 some stretches get
 *                          half the set intensity and others one and a half times it
 * @param islandRate        the part of the islands' coastline, 0 to 1, that is eroded
 * @param islandIntensity   how deep and rugged the erosion is on islands, compared with the default (1); 0 for none
 * @param islandVariation   how much the intensity changes along the islands' coasts, 0 to 1
 */
public record CoastErosion(double mainlandRate, double mainlandIntensity, double mainlandVariation, double islandRate, double islandIntensity,
                           double islandVariation) {
    public static final double MAX_INTENSITY = 4.0;
    public static final double LIMIT_MAX_INTENSITY = 10.0;
    public static final double DEFAULT_VARIATION = 0.5;

    /** What new worlds get. */
    public static final CoastErosion DEFAULT = new CoastErosion(0.6, 1.0, DEFAULT_VARIATION, 0.5, 1.0, DEFAULT_VARIATION);
    /** What worlds made before 0.14.0 get: no erosion, so their land stays exactly as it was. */
    public static final CoastErosion NONE = new CoastErosion(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    private static final Codec<Double> RATE = Codec.doubleRange(0.0, 1.0);
    private static final Codec<Double> INTENSITY = Codec.doubleRange(0.0, LIMIT_MAX_INTENSITY);

    public static final Codec<CoastErosion> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(RATE, "mainland_rate", DEFAULT.mainlandRate).forGetter(CoastErosion::mainlandRate),
            CodecUtil.field(INTENSITY, "mainland_intensity", DEFAULT.mainlandIntensity).forGetter(CoastErosion::mainlandIntensity),
            // From 0.17.0; 0.14.0 to 0.16.0 files keep an even intensity, so their coasts stay as they were.
            CodecUtil.field(RATE, "mainland_variation", 0.0).forGetter(CoastErosion::mainlandVariation),
            CodecUtil.field(RATE, "island_rate", DEFAULT.islandRate).forGetter(CoastErosion::islandRate),
            CodecUtil.field(INTENSITY, "island_intensity", DEFAULT.islandIntensity).forGetter(CoastErosion::islandIntensity),
            CodecUtil.field(RATE, "island_variation", 0.0).forGetter(CoastErosion::islandVariation)
    ).apply(instance, CoastErosion::new));

    public boolean mainland() {
        return this.mainlandRate > 0.0 && this.mainlandIntensity > 0.0;
    }

    public boolean islands() {
        return this.islandRate > 0.0 && this.islandIntensity > 0.0;
    }

    public CoastErosion withMainlandRate(double v) {
        return new CoastErosion(v, mainlandIntensity, mainlandVariation, islandRate, islandIntensity, islandVariation);
    }

    public CoastErosion withMainlandIntensity(double v) {
        return new CoastErosion(mainlandRate, v, mainlandVariation, islandRate, islandIntensity, islandVariation);
    }

    public CoastErosion withMainlandVariation(double v) {
        return new CoastErosion(mainlandRate, mainlandIntensity, v, islandRate, islandIntensity, islandVariation);
    }

    public CoastErosion withIslandRate(double v) {
        return new CoastErosion(mainlandRate, mainlandIntensity, mainlandVariation, v, islandIntensity, islandVariation);
    }

    public CoastErosion withIslandIntensity(double v) {
        return new CoastErosion(mainlandRate, mainlandIntensity, mainlandVariation, islandRate, v, islandVariation);
    }

    public CoastErosion withIslandVariation(double v) {
        return new CoastErosion(mainlandRate, mainlandIntensity, mainlandVariation, islandRate, islandIntensity, v);
    }

    /** For the log. */
    public String describe() {
        return String.format(Locale.ROOT, "Coastal erosion: mainland %d%% of the coast at %d%% intensity (varying %d%%), islands %d%% at %d%% (varying %d%%)",
                Math.round(mainlandRate * 100), Math.round(mainlandIntensity * 100), Math.round(mainlandVariation * 100),
                Math.round(islandRate * 100), Math.round(islandIntensity * 100), Math.round(islandVariation * 100));
    }
}
