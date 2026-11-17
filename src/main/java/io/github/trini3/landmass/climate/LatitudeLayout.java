package io.github.trini3.landmass.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.CodecUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * How temperature changes with distance from the equator. All distances are in blocks.
 *
 * <p>Temperature is a piecewise-linear function of distance from the equator. By default the
 * pieces come from the zone sizes: each zone covers its share of {@code polarDistance}, and within
 * a zone the temperature slides across that zone's whole range, so biome mods that split a band
 * into finer steps still get every step. Past {@code polarDistance} it's frozen, getting colder up to
 * the pole at the end of the profile, the frozen zone's size further out. Past the pole the zones either repeat in reverse or stay frozen;
 * see {@link Poles}.
 *
 * <p>Before the lookup, the distance is nudged by two noises so the borders aren't ruler-straight:
 * a large, slow "wander" noise and the world's own temperature noise ("mixing"), which keeps
 * whatever character a terrain or biome pack gave to temperature.
 *
 * <p>With {@link Tropics#MIXED} the zone-based profile keeps the equator just below the hot band and
 * dry land in the tropics is made hotter, so humidity decides between desert and savanna, forest or
 * jungle. See {@link Tropics}.
 */
public final class LatitudeLayout {
    // Minecraft's temperature band edges, hottest to coldest.
    public static final double HOT_MAX = 1.0;
    public static final double HOT_WARM = 0.55;
    public static final double WARM_TEMPERATE = 0.2;
    public static final double TEMPERATE_COLD = -0.15;
    public static final double COLD_FROZEN = -0.45;
    public static final double FROZEN_MIN = -1.0;

    /**
     * How far past the polar distance the frozen zone keeps getting colder, as a share of the polar distance,
     * when the zone sizes don't give a frozen size (as in files from before version 0.7.0).
     */
    public static final double POLAR_RAMP = 0.25;

    // Mixed tropics: the profile temperature at the equator and at the outer edge of the hot zone. Both are
    // warm, so only dry land reaches the hot (desert) band, and it does so most easily at the outer edge.
    public static final double MIXED_EQUATOR = 0.4;
    public static final double MIXED_SUBTROPICS = 0.5;
    /** Profile temperature from which dry land gets the full desert effect. It fades out toward the warm/temperate border. */
    public static final double TROPICAL = 0.4;
    public static final double DEFAULT_DESERT_STRENGTH = 0.5;
    /**
     * Desert share (0.20.0): roughly how much of the hot zone is desert with mixed tropics. The legacy share is what the mod made
     * before (the self-test measures about 19%); it maps to a humidity offset of exactly 0 and is what files without the setting
     * read as, so older worlds stay the same. New worlds and the built-in presets use the default (chosen in 0.20.1)
     * because deserts looked small next to savannas.
     */
    public static final double LEGACY_DESERT_SHARE = 0.20;
    public static final double DEFAULT_DESERT_SHARE = 0.45;
    public static final double MAX_DESERT_SHARE = 0.95;
    // The tropics' humidity distribution, as measured by the self-test (hot-zone percentiles), for turning a share into an offset.
    private static final double[] HUMIDITY_AT = {-1.0, -0.31, -0.16, -0.05, 0.01, 0.07, 0.17, 0.31, 1.0};
    private static final double[] SHARE_BELOW = {0.0, 0.10, 0.25, 0.40, 0.50, 0.60, 0.75, 0.90, 1.0};

    public static final int MIN_POLAR_DISTANCE = 1_000;
    public static final int MAX_POLAR_DISTANCE = 30_000_000;
    public static final int DEFAULT_POLAR_DISTANCE = 100_000;
    public static final double DEFAULT_BORDER_WANDER = 6_000;
    public static final double DEFAULT_BORDER_MIXING = 2_000;
    public static final double MAX_BORDER_NOISE = 1_000_000;
    // Band waviness (0.14.0): 1 = the default, 0 = straight borders. It scales the border wander and the large meanders,
    // which swing the bands by up to DEFAULT_MEANDER of the polar distance over a wavelength of MEANDER_WAVE polar distances.
    public static final double DEFAULT_WAVINESS = 1.0;
    public static final double MAX_WAVINESS = 4.0;
    public static final double LIMIT_MAX_WAVINESS = 20.0;
    public static final double DEFAULT_MEANDER = 0.12;
    public static final double MEANDER_WAVE = 0.8;
    // The wander noise's largest features are 2^15 blocks across (firstOctave -15).
    private static final double WANDER_NOISE_WAVE = 32_768.0;

    public static final LatitudeLayout DEFAULT = new LatitudeLayout(
            LatitudeAxis.Z, 0, DEFAULT_POLAR_DISTANCE, Poles.REPEAT, ZoneSizes.DEFAULT, Tropics.MIXED, DEFAULT_DESERT_STRENGTH,
            DEFAULT_BORDER_WANDER, DEFAULT_BORDER_MIXING, 0.0, List.of(), DEFAULT_WAVINESS, DEFAULT_MEANDER);

    public static final MapCodec<LatitudeLayout> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            CodecUtil.field(LatitudeAxis.CODEC, "axis", LatitudeAxis.Z).forGetter(LatitudeLayout::axis),
            CodecUtil.field(Codec.INT, "equator", 0).forGetter(LatitudeLayout::equator),
            CodecUtil.field(Codec.intRange(MIN_POLAR_DISTANCE, MAX_POLAR_DISTANCE), "polar_distance", DEFAULT_POLAR_DISTANCE).forGetter(LatitudeLayout::polarDistance),
            // Files from versions 0.1.0 and 0.2.0 don't have this, and their worlds stay frozen past the poles.
            CodecUtil.field(Poles.CODEC, "poles", Poles.FROZEN).forGetter(LatitudeLayout::poles),
            CodecUtil.field(ZoneSizes.CODEC, "zones", ZoneSizes.DEFAULT).forGetter(LatitudeLayout::zones),
            // Files from version 0.1.0 don't have this, and their worlds were generated with desert tropics.
            CodecUtil.field(Tropics.CODEC, "tropics", Tropics.DESERT).forGetter(LatitudeLayout::tropics),
            CodecUtil.field(Codec.doubleRange(0.0, 2.0), "desert_strength", DEFAULT_DESERT_STRENGTH).forGetter(LatitudeLayout::desertStrength),
            CodecUtil.field(Codec.doubleRange(0.0, MAX_BORDER_NOISE), "border_wander", DEFAULT_BORDER_WANDER).forGetter(LatitudeLayout::borderWander),
            CodecUtil.field(Codec.doubleRange(0.0, MAX_BORDER_NOISE), "border_mixing", DEFAULT_BORDER_MIXING).forGetter(LatitudeLayout::borderMixing),
            CodecUtil.field(Codec.doubleRange(0.0, 2.0), "local_variation", 0.0).forGetter(LatitudeLayout::localVariation),
            CodecUtil.field(ProfilePoint.CODEC.listOf(), "custom_profile", List.of()).forGetter(LatitudeLayout::customProfile),
            // From 0.14.0. Older files keep their bands exactly: waviness 1 and no large meanders.
            CodecUtil.field(Codec.doubleRange(0.0, LIMIT_MAX_WAVINESS), "waviness", DEFAULT_WAVINESS).forGetter(LatitudeLayout::waviness),
            CodecUtil.field(Codec.doubleRange(0.0, 1.0), "meander", 0.0).forGetter(LatitudeLayout::meander),
            // From 0.20.0; older files keep the share they were made with, the default.
            CodecUtil.field(Codec.doubleRange(0.0, MAX_DESERT_SHARE), "desert_share", LEGACY_DESERT_SHARE).forGetter(LatitudeLayout::desertShare)
    ).apply(instance, LatitudeLayout::new));

    private final LatitudeAxis axis;
    private final int equator;
    private final int polarDistance;
    private final Poles poles;
    private final ZoneSizes zones;
    private final Tropics tropics;
    private final double desertStrength;
    private final double borderWander;
    private final double borderMixing;
    private final double localVariation;
    private final List<ProfilePoint> customProfile;
    private final double waviness;
    private final double meander;
    private final double desertShare;
    // Humidity below which tropical land starts getting hotter: 0 at the default desert share, higher for more desert.
    private final double dryThreshold;

    // The profile as parallel arrays: temperatures[i] applies at distances[i] blocks from the equator.
    private final double[] distances;
    private final double[] temperatures;
    // The pole: the end of the profile, where the zones turn around with Poles.REPEAT.
    private final double poleDistance;
    // How much hotter the driest tropical land gets: the desert strength with mixed tropics, otherwise 0.
    private final double drynessHeat;
    private final double minTemperature;
    private final double maxTemperature;

    public LatitudeLayout(LatitudeAxis axis, int equator, int polarDistance, Poles poles, ZoneSizes zones,
                          Tropics tropics, double desertStrength,
                          double borderWander, double borderMixing, double localVariation,
                          List<ProfilePoint> customProfile) {
        this(axis, equator, polarDistance, poles, zones, tropics, desertStrength, borderWander, borderMixing, localVariation, customProfile,
                DEFAULT_WAVINESS, 0.0);
    }

    /**
     * @param waviness how wavy the band borders are compared with the default (1); 0 keeps them straight
     * @param meander  how far the large meanders swing the bands at waviness 1, as a part of the polar distance
     */
    public LatitudeLayout(LatitudeAxis axis, int equator, int polarDistance, Poles poles, ZoneSizes zones,
                          Tropics tropics, double desertStrength,
                          double borderWander, double borderMixing, double localVariation,
                          List<ProfilePoint> customProfile, double waviness, double meander) {
        this(axis, equator, polarDistance, poles, zones, tropics, desertStrength, borderWander, borderMixing, localVariation, customProfile,
                waviness, meander, LEGACY_DESERT_SHARE);
    }

    /** @param desertShare roughly how much of the hot zone is desert with mixed tropics; see {@link #DEFAULT_DESERT_SHARE} */
    public LatitudeLayout(LatitudeAxis axis, int equator, int polarDistance, Poles poles, ZoneSizes zones,
                          Tropics tropics, double desertStrength,
                          double borderWander, double borderMixing, double localVariation,
                          List<ProfilePoint> customProfile, double waviness, double meander, double desertShare) {
        this.desertShare = desertShare;
        this.dryThreshold = dryThreshold(desertShare, desertStrength);
        this.waviness = waviness;
        this.meander = meander;
        this.axis = axis;
        this.equator = equator;
        this.polarDistance = polarDistance;
        this.poles = poles;
        this.zones = zones;
        this.tropics = tropics;
        this.desertStrength = desertStrength;
        this.drynessHeat = tropics == Tropics.MIXED ? desertStrength : 0.0;
        this.borderWander = borderWander;
        this.borderMixing = borderMixing;
        this.localVariation = localVariation;
        this.customProfile = List.copyOf(customProfile);

        if (this.customProfile.isEmpty()) {
            double total = zones.total();
            double hot = polarDistance * zones.hot() / total;
            double warm = polarDistance * zones.warm() / total;
            double temperate = polarDistance * zones.temperate() / total;
            double frozen = polarDistance * zones.frozen() / total;
            this.distances = new double[]{0, hot, hot + warm, hot + warm + temperate, polarDistance, polarDistance + frozen};
            this.temperatures = tropics == Tropics.MIXED
                    ? new double[]{MIXED_EQUATOR, MIXED_SUBTROPICS, WARM_TEMPERATE, TEMPERATE_COLD, COLD_FROZEN, FROZEN_MIN}
                    : new double[]{HOT_MAX, HOT_WARM, WARM_TEMPERATE, TEMPERATE_COLD, COLD_FROZEN, FROZEN_MIN};
        } else {
            List<ProfilePoint> sorted = new ArrayList<>(this.customProfile);
            sorted.sort(Comparator.comparingDouble(ProfilePoint::percent));
            this.distances = new double[sorted.size()];
            this.temperatures = new double[sorted.size()];
            for (int i = 0; i < sorted.size(); i++) {
                this.distances[i] = polarDistance * sorted.get(i).percent() / 100.0;
                this.temperatures[i] = sorted.get(i).temperature();
            }
        }
        this.poleDistance = this.distances[this.distances.length - 1];

        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double temperature : this.temperatures) {
            min = Math.min(min, temperature);
            max = Math.max(max, temperature);
        }
        this.minTemperature = min - localVariation;
        this.maxTemperature = max + localVariation + this.drynessHeat * (1.0 + Math.max(0.0, this.dryThreshold));
    }

    /**
     * The temperature at a position.
     *
     * @param wander   the large border noise at this position, from -1 to 1
     * @param mixing   the world's own temperature noise at this position, clamped to -1 to 1
     * @param humidity the world's humidity at this position, clamped to -1 to 1. Only used with mixed tropics.
     */
    public double temperature(int x, int z, double wander, double mixing, double humidity) {
        return this.temperature(x, z, wander, 0.0, mixing, humidity);
    }

    /**
     * The temperature at a position, with the large meanders.
     *
     * @param meanderNoise the wander noise sampled at {@link #meanderFrequency()}, from -1 to 1
     */
    public double temperature(int x, int z, double wander, double meanderNoise, double mixing, double humidity) {
        return this.temperature(x, z, wander, meanderNoise, mixing, humidity, 0.0);
    }

    /**
     * The temperature at a position, with the large meanders and a climate deviation patch.
     *
     * @param zoneShift how many temperature zones a deviation patch moves this spot, positive toward the pole (see {@link DeviationZones})
     */
    public double temperature(int x, int z, double wander, double meanderNoise, double mixing, double humidity, double zoneShift) {
        double distance = this.distanceFromEquator(x, z);
        // The meander moves the bands as a whole (like the equator shifting), so it's added before folding north and south.
        double shift = this.waviness * (this.borderWander * wander + this.meander * this.polarDistance * meanderNoise);
        distance = Math.abs(distance + shift + this.borderMixing * mixing);
        if (this.repeats()) {
            distance = this.foldAtPoles(distance);
        }
        if (zoneShift != 0.0) {
            distance = DeviationZones.shiftDistance(distance, zoneShift, this.distances);
        }
        double latitude = this.temperatureAt(distance);
        double temperature = latitude + this.localVariation * mixing;
        if (humidity < this.dryThreshold) {
            // Dry tropical land gets hotter, which tips it into the desert band while wetter land at the
            // same latitude stays savanna, forest or jungle. A higher threshold (more desert share) counts more land as dry.
            temperature += this.drynessHeat * tropicalShare(latitude) * (this.dryThreshold - humidity);
        }
        return temperature;
    }

    /**
     * Folds a distance from the equator back at every pole, so the zones run equator to pole, pole back
     * to the next equator, and so on: the climate along a walk over the top of a globe.
     */
    public double foldAtPoles(double distance) {
        if (this.poleDistance <= 0) {
            return distance;
        }
        double cycle = 2 * this.poleDistance;
        double withinCycle = distance % cycle;
        return withinCycle <= this.poleDistance ? withinCycle : cycle - withinCycle;
    }

    /** Whether the zones repeat past the poles. A profile with a single point has no pole to turn around at. */
    public boolean repeats() {
        return this.poles == Poles.REPEAT && this.poleDistance > 0;
    }

    /** Whether {@link #temperature} looks at humidity, so callers can skip computing it otherwise. */
    public boolean usesHumidity() {
        return this.drynessHeat > 0;
    }

    /** 1 where the profile is tropical, fading to 0 at the warm/temperate border, so cooler zones only follow latitude. */
    public static double tropicalShare(double latitudeTemperature) {
        return Math.clamp((latitudeTemperature - WARM_TEMPERATE) / (TROPICAL - WARM_TEMPERATE), 0.0, 1.0);
    }

    /** The profile temperature at a distance from the equator, before any border noise. */
    public double temperatureAt(double distance) {
        double[] d = this.distances;
        double[] t = this.temperatures;
        if (distance <= d[0]) {
            return t[0];
        }
        for (int i = 1; i < d.length; i++) {
            if (distance <= d[i]) {
                double span = d[i] - d[i - 1];
                if (span <= 0) {
                    return t[i];
                }
                return t[i - 1] + (t[i] - t[i - 1]) * (distance - d[i - 1]) / span;
            }
        }
        return t[t.length - 1];
    }

    /** Whether the large meanders are on, so callers can skip sampling them otherwise. */
    public boolean meanders() {
        return this.waviness > 0.0 && this.meander > 0.0;
    }

    /** What to multiply block coordinates by to sample the wander noise at the meanders' scale. */
    public double meanderFrequency() {
        return WANDER_NOISE_WAVE / (MEANDER_WAVE * Math.max(this.polarDistance, MIN_POLAR_DISTANCE));
    }

    public double waviness() {
        return this.waviness;
    }

    public double meander() {
        return this.meander;
    }

    /** The same layout with this band waviness; a layout without large meanders gets the default ones (unless 0). */
    public LatitudeLayout withWaviness(double waviness) {
        return new LatitudeLayout(this.axis, this.equator, this.polarDistance, this.poles, this.zones, this.tropics, this.desertStrength,
                this.borderWander, this.borderMixing, this.localVariation, this.customProfile, waviness, this.meander, this.desertShare);
    }

    public double distanceFromEquator(int x, int z) {
        return Math.abs((double) this.axis.along(x, z) - this.equator);
    }

    /** Short, human-readable lines describing the layout, for logs and the /latitude command. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        String direction = this.axis == LatitudeAxis.Z ? "north and south" : "east and west";
        lines.add(String.format(Locale.ROOT, "Equator at %s=%,d; polar zone starts %,d blocks %s of it.",
                this.axis.getSerializedName(), this.equator, this.polarDistance, direction));
        if (this.customProfile.isEmpty()) {
            String[] names = {"hot", "warm", "temperate", "cold"};
            StringBuilder zonesLine = new StringBuilder("Zones by distance from the equator: ");
            for (int i = 0; i < names.length; i++) {
                if (this.distances[i + 1] > this.distances[i]) {
                    zonesLine.append(String.format(Locale.ROOT, "%s %,d-%,d, ", names[i], Math.round(this.distances[i]), Math.round(this.distances[i + 1])));
                }
            }
            zonesLine.append(String.format(Locale.ROOT, "frozen beyond %,d.", this.polarDistance));
            lines.add(zonesLine.toString());
        } else {
            StringBuilder profileLine = new StringBuilder("Custom temperature profile (percent of polar distance:temperature): ");
            for (int i = 0; i < this.customProfile.size(); i++) {
                profileLine.append(i == 0 ? "" : ", ").append(this.customProfile.get(i));
            }
            lines.add(profileLine.toString());
        }
        if (this.repeats()) {
            lines.add(String.format(Locale.ROOT, "Past the pole at %,d blocks the zones come back in reverse, like going over the top of a globe, "
                    + "reaching another equator at %,d blocks.", Math.round(this.poleDistance), Math.round(2 * this.poleDistance)));
        } else if (this.customProfile.isEmpty()) {
            lines.add(String.format(Locale.ROOT, "Everything past the pole at %,d blocks stays frozen.", Math.round(this.poleDistance)));
        } else {
            lines.add(String.format(Locale.ROOT, "Past %,d blocks the temperature stays at %.2f.",
                    Math.round(this.poleDistance), this.temperatures[this.temperatures.length - 1]));
        }
        if (this.tropics == Tropics.MIXED) {
            lines.add(String.format(Locale.ROOT, "Tropics: humidity decides between desert and savanna, forest or jungle (desert strength %.2f, desert share about %.0f%%, dry threshold %+.2f)%s.",
                    this.desertStrength, this.desertShare * 100, this.dryThreshold, this.customProfile.isEmpty() ? "; deserts are most common toward the outer edge of the hot zone" : ""));
        } else {
            lines.add("Tropics: hot land is all desert.");
        }
        lines.add(String.format(Locale.ROOT, "Borders wander up to %,d blocks and mix over %,d blocks; extra local variation %.2f.",
                Math.round(this.waviness * this.borderWander), Math.round(this.borderMixing), this.localVariation));
        lines.add(String.format(Locale.ROOT, "Band waviness %d%%: large meanders swing the bands up to about %,d blocks.",
                Math.round(this.waviness * 100), Math.round(this.waviness * this.meander * this.polarDistance)));
        return lines;
    }

    public LatitudeAxis axis() {
        return this.axis;
    }

    public int equator() {
        return this.equator;
    }

    public int polarDistance() {
        return this.polarDistance;
    }

    /** Each zone's width in blocks: hot, warm, temperate and cold, then frozen, from its edge to the pole. Rounded. */
    public int[] zoneWidths() {
        double total = this.zones.total();
        double[] sizes = {this.zones.hot(), this.zones.warm(), this.zones.temperate(), this.zones.cold(), this.zones.frozen()};
        int[] widths = new int[sizes.length];
        for (int i = 0; i < sizes.length; i++) {
            widths[i] = (int) Math.round(this.polarDistance * sizes[i] / total);
        }
        return widths;
    }

    /**
     * The same layout with zones of these widths in blocks. The four zones up to the frozen one add up to the
     * polar distance, which must stay between {@link #MIN_POLAR_DISTANCE} and {@link #MAX_POLAR_DISTANCE}.
     */
    public LatitudeLayout withZoneWidths(int hot, int warm, int temperate, int cold, int frozen) {
        ZoneSizes zones = new ZoneSizes(hot, warm, temperate, cold, frozen);
        return new LatitudeLayout(this.axis, this.equator, hot + warm + temperate + cold, this.poles, zones, this.tropics, this.desertStrength,
                this.borderWander, this.borderMixing, this.localVariation, this.customProfile, this.waviness, this.meander, this.desertShare);
    }

    public Poles poles() {
        return this.poles;
    }

    /** Distance from the equator to the pole, the coldest point: the end of the temperature profile. */
    public double poleDistance() {
        return this.poleDistance;
    }

    /**
     * How many blocks across a map centred on the equator should be to show both poles, plus some of the
     * returning zones when they repeat. Never less than 2.6 times the polar distance.
     */
    public double mapSpan() {
        double margin = this.repeats() ? 0.35 : 0.05;
        return Math.max(2.6 * this.polarDistance, 2 * (this.poleDistance + margin * this.polarDistance));
    }

    public ZoneSizes zones() {
        return this.zones;
    }

    public Tropics tropics() {
        return this.tropics;
    }

    public double desertStrength() {
        return this.desertStrength;
    }

    public double desertShare() {
        return this.desertShare;
    }

    /** The same layout with roughly this share of the hot zone desert (mixed tropics only). */
    public LatitudeLayout withDesertShare(double desertShare) {
        return new LatitudeLayout(this.axis, this.equator, this.polarDistance, this.poles, this.zones, this.tropics, this.desertStrength,
                this.borderWander, this.borderMixing, this.localVariation, this.customProfile, this.waviness, this.meander, desertShare);
    }

    /**
     * The humidity offset that gives about this desert share. Desert is where the zone profile (0.4 at the equator to 0.5 at the
     * hot zone's edge) plus strength x (threshold - humidity) passes the hot band at 0.55, so the share is the average, across the
     * hot zone, of how much land is drier than threshold - (0.55 - profile) / strength. Measured against the legacy share, so that
     * is exactly 0.
     */
    static double dryThreshold(double share, double strength) {
        if (strength <= 0 || Math.abs(share - LEGACY_DESERT_SHARE) < 1e-9) {
            return 0.0;
        }
        if (share <= 0) {
            return -2.0; // drier than any land
        }
        return solveThreshold(share, strength) - solveThreshold(LEGACY_DESERT_SHARE, strength);
    }

    private static double solveThreshold(double share, double strength) {
        double low = -2.0;
        double high = 2.0;
        for (int i = 0; i < 50; i++) {
            double mid = (low + high) / 2;
            if (modelShare(mid, strength) < share) {
                low = mid;
            } else {
                high = mid;
            }
        }
        return (low + high) / 2;
    }

    private static double modelShare(double threshold, double strength) {
        double sum = 0;
        int steps = 21;
        for (int i = 0; i < steps; i++) {
            double profile = MIXED_EQUATOR + (MIXED_SUBTROPICS - MIXED_EQUATOR) * i / (steps - 1);
            sum += shareBelow(threshold - (HOT_WARM - profile) / strength);
        }
        return sum / steps;
    }

    private static double shareBelow(double humidity) {
        if (humidity <= HUMIDITY_AT[0]) {
            return 0.0;
        }
        for (int i = 1; i < HUMIDITY_AT.length; i++) {
            if (humidity <= HUMIDITY_AT[i]) {
                double t = (humidity - HUMIDITY_AT[i - 1]) / (HUMIDITY_AT[i] - HUMIDITY_AT[i - 1]);
                return SHARE_BELOW[i - 1] + t * (SHARE_BELOW[i] - SHARE_BELOW[i - 1]);
            }
        }
        return 1.0;
    }

    public double borderWander() {
        return this.borderWander;
    }

    public double borderMixing() {
        return this.borderMixing;
    }

    public double localVariation() {
        return this.localVariation;
    }

    public List<ProfilePoint> customProfile() {
        return this.customProfile;
    }

    public double minTemperature() {
        return this.minTemperature;
    }

    public double maxTemperature() {
        return this.maxTemperature;
    }

    /**
     * Relative sizes of the zones: the four between the equator and the frozen zone, which fill the polar
     * distance, and the frozen zone from its edge to the pole. Only their proportions matter, so they can be
     * percentages or, as the Customize screen saves them, widths in blocks.
     */
    public record ZoneSizes(double hot, double warm, double temperate, double cold, double frozen) {
        public static final ZoneSizes DEFAULT = of(20, 25, 30, 25);
        // Big enough for widths in blocks.
        private static final Codec<Double> SIZE = Codec.doubleRange(0.0, 1.0e9);

        public static final Codec<ZoneSizes> CODEC = RecordCodecBuilder.<ZoneSizes>create(instance -> instance.group(
                CodecUtil.field(SIZE, "hot", DEFAULT.hot()).forGetter(ZoneSizes::hot),
                CodecUtil.field(SIZE, "warm", DEFAULT.warm()).forGetter(ZoneSizes::warm),
                CodecUtil.field(SIZE, "temperate", DEFAULT.temperate()).forGetter(ZoneSizes::temperate),
                CodecUtil.field(SIZE, "cold", DEFAULT.cold()).forGetter(ZoneSizes::cold),
                // Files from before 0.7.0 don't have this; their frozen zone is a quarter of the polar distance.
                SIZE.optionalFieldOf("frozen").forGetter(sizes -> Optional.of(sizes.frozen()))
        ).apply(instance, (hot, warm, temperate, cold, frozen) -> frozen
                .map(size -> new ZoneSizes(hot, warm, temperate, cold, size))
                .orElseGet(() -> of(hot, warm, temperate, cold)))).validate(ZoneSizes::check);

        /** Sizes with the frozen zone as before 0.7.0: a quarter of the other four together. */
        public static ZoneSizes of(double hot, double warm, double temperate, double cold) {
            return new ZoneSizes(hot, warm, temperate, cold, (hot + warm + temperate + cold) * POLAR_RAMP);
        }

        public double total() {
            return this.hot + this.warm + this.temperate + this.cold;
        }

        private static DataResult<ZoneSizes> check(ZoneSizes sizes) {
            return sizes.total() > 0 ? DataResult.success(sizes) : DataResult.error(() -> "At least one zone size must be above zero");
        }
    }

    /** One point of a custom profile: a temperature at a distance given as a percentage of the polar distance. */
    public record ProfilePoint(double percent, double temperature) {
        public static final Codec<ProfilePoint> CODEC = Codec.STRING.comapFlatMap(ProfilePoint::parse, ProfilePoint::toString);

        public static DataResult<ProfilePoint> parse(String text) {
            String[] parts = text.split("[:=]");
            if (parts.length != 2) {
                return DataResult.error(() -> "Expected \"percent:temperature\", like \"25:0.55\", but got \"" + text + "\"");
            }
            try {
                double percent = Double.parseDouble(parts[0].trim());
                double temperature = Double.parseDouble(parts[1].trim());
                if (percent < 0) {
                    return DataResult.error(() -> "Profile distance can't be negative: \"" + text + "\"");
                }
                if (temperature < -2 || temperature > 2) {
                    return DataResult.error(() -> "Profile temperature should be between -2 and 2: \"" + text + "\"");
                }
                return DataResult.success(new ProfilePoint(percent, temperature));
            } catch (NumberFormatException e) {
                return DataResult.error(() -> "Couldn't read the numbers in \"" + text + "\"");
            }
        }

        @Override
        public String toString() {
            return plain(this.percent) + ":" + plain(this.temperature);
        }

        private static String plain(double value) {
            return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        }
    }
}
