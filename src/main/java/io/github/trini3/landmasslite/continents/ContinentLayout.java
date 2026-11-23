package io.github.trini3.landmasslite.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmasslite.util.CodecUtil;

import java.util.List;
import java.util.Locale;

/**
 * Where a world's land goes: continents, large single islands, archipelagos (clusters of small islands) and small
 * islands scattered across the oceans. Sizes are in blocks, each picked per landmass between a smallest and a largest
 * value. {@code landShare} is the part of the world that is land, from 0 to 1, counting islands. The amounts say how
 * common large islands and archipelagos are compared with the default (1 = default, 0 = none).
 * {@code islandBiomeScale} is the size of biomes and climate patches on islands compared with the continents.
 *
 * <p>Turned off, the world keeps the land and oceans Minecraft (or a terrain pack) would make.
 */
public record ContinentLayout(boolean enabled, SizeRange continentSize, double landShare, double islandBiomeScale, SizeRange largeIslandSize,
                              double largeIslandAmount, SizeRange archipelagoSize, double archipelagoAmount, SizeRange islandSize,
                              CoastFeatures coastFeatures, CoastErosion erosion, boolean scaledCoasts, Shores shores) {
    // Recommended ranges: what the Customize screen's sliders cover. Earth-sized worlds fit: Eurasia is about 10,000 km across.
    public static final int MIN_CONTINENT = 500;
    public static final int MAX_CONTINENT = 10_000_000;
    public static final int MIN_LARGE_ISLAND = 200;
    public static final int MAX_LARGE_ISLAND = 1_000_000;
    public static final int MIN_ARCHIPELAGO = 200;
    public static final int MAX_ARCHIPELAGO = 2_000_000;
    public static final int MIN_ISLAND = 20;
    public static final int MAX_ISLAND = 50_000;
    public static final double MIN_LAND_SHARE = 0.05;
    public static final double MAX_LAND_SHARE = 0.9;
    public static final double MAX_AMOUNT = 4.0;
    public static final double MIN_ISLAND_BIOME_SCALE = 0.1;
    public static final double MAX_ISLAND_BIOME_SCALE = 2.0;
    // Hard limits: what can be typed in (with a warning) and saved. Sizes stop at the world border, 30 million blocks out.
    public static final int LIMIT_MIN_SIZE = 10;
    public static final int LIMIT_MAX_SIZE = 30_000_000;
    public static final double LIMIT_MIN_LAND_SHARE = 0.01;
    public static final double LIMIT_MAX_LAND_SHARE = 0.99;
    public static final double LIMIT_MAX_AMOUNT = 10.0;
    public static final double LIMIT_MIN_ISLAND_BIOME_SCALE = 0.01;
    public static final double LIMIT_MAX_ISLAND_BIOME_SCALE = 10.0;

    /** A layout with coastlines that grow with the landmasses, as new worlds get. */
    public ContinentLayout(boolean enabled, SizeRange continentSize, double landShare, double islandBiomeScale, SizeRange largeIslandSize,
                           double largeIslandAmount, SizeRange archipelagoSize, double archipelagoAmount, SizeRange islandSize,
                           CoastFeatures coastFeatures, CoastErosion erosion) {
        this(enabled, continentSize, landShare, islandBiomeScale, largeIslandSize, largeIslandAmount, archipelagoSize, archipelagoAmount, islandSize,
                coastFeatures, erosion, true, Shores.DEFAULT);
    }

    /** What new worlds get. */
    public static final ContinentLayout DEFAULT = new ContinentLayout(true, new SizeRange(6_000, 24_000), 0.35, 0.5,
            new SizeRange(1_000, 4_000), 1.0, new SizeRange(1_500, 6_000), 1.0, new SizeRange(100, 800), CoastFeatures.DEFAULT,
            CoastErosion.DEFAULT);
    /** What worlds made before continents existed get: Minecraft's own land and oceans. */
    public static final ContinentLayout OFF = DEFAULT.withEnabled(false);

    public static final Codec<ContinentLayout> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(Codec.BOOL, "enabled", true).forGetter(ContinentLayout::enabled),
            CodecUtil.field(SizeRange.CODEC, "continent_size", DEFAULT.continentSize).forGetter(ContinentLayout::continentSize),
            CodecUtil.field(Codec.doubleRange(LIMIT_MIN_LAND_SHARE, LIMIT_MAX_LAND_SHARE), "land_share", DEFAULT.landShare).forGetter(ContinentLayout::landShare),
            // Missing in 0.9.0 files, whose worlds have island biomes like the continents' and no large islands.
            CodecUtil.field(Codec.doubleRange(LIMIT_MIN_ISLAND_BIOME_SCALE, LIMIT_MAX_ISLAND_BIOME_SCALE), "island_biome_scale", 1.0).forGetter(ContinentLayout::islandBiomeScale),
            CodecUtil.field(SizeRange.CODEC, "large_island_size", DEFAULT.largeIslandSize).forGetter(ContinentLayout::largeIslandSize),
            CodecUtil.field(Codec.doubleRange(0.0, LIMIT_MAX_AMOUNT), "large_island_amount", 0.0).forGetter(ContinentLayout::largeIslandAmount),
            CodecUtil.field(SizeRange.CODEC, "archipelago_size", DEFAULT.archipelagoSize).forGetter(ContinentLayout::archipelagoSize),
            CodecUtil.field(Codec.doubleRange(0.0, LIMIT_MAX_AMOUNT), "archipelago_amount", 1.0).forGetter(ContinentLayout::archipelagoAmount),
            CodecUtil.field(SizeRange.CODEC, "island_size", DEFAULT.islandSize).forGetter(ContinentLayout::islandSize),
            // Missing in files from before 0.12.0, whose worlds have no added coastal features.
            CodecUtil.field(CoastFeatures.CODEC, "coast_features", CoastFeatures.NONE).forGetter(ContinentLayout::coastFeatures),
            // Missing in files from before 0.14.0, whose worlds have no coastal erosion.
            CodecUtil.field(CoastErosion.CODEC, "erosion", CoastErosion.NONE).forGetter(ContinentLayout::erosion),
            // Missing in files from before 0.17.0, whose coastlines stop growing past a certain size (see ContinentShapes).
            CodecUtil.field(Codec.BOOL, "scaled_coasts", false).forGetter(ContinentLayout::scaledCoasts),
            // Missing in files from before 0.18.0, whose coasts are as Minecraft picks them.
            CodecUtil.field(Shores.CODEC, "shores", Shores.NONE).forGetter(ContinentLayout::shores)
    ).apply(instance, ContinentLayout::new));

    public ContinentLayout withEnabled(boolean enabled) {
        return new ContinentLayout(enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withContinentSize(SizeRange continentSize) {
        return new ContinentLayout(this.enabled, continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withLandShare(double landShare) {
        return new ContinentLayout(this.enabled, this.continentSize, landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withIslandBiomeScale(double islandBiomeScale) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withLargeIslandSize(SizeRange largeIslandSize) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withLargeIslandAmount(double largeIslandAmount) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withArchipelagoSize(SizeRange archipelagoSize) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withArchipelagoAmount(double archipelagoAmount) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withIslandSize(SizeRange islandSize) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withCoastFeatures(CoastFeatures coastFeatures) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, coastFeatures, this.erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withErosion(CoastErosion erosion) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, erosion, this.scaledCoasts, this.shores);
    }

    public ContinentLayout withShores(Shores shores) {
        return new ContinentLayout(this.enabled, this.continentSize, this.landShare, this.islandBiomeScale, this.largeIslandSize, this.largeIslandAmount,
                this.archipelagoSize, this.archipelagoAmount, this.islandSize, this.coastFeatures, this.erosion, this.scaledCoasts, shores);
    }

    /** For the log. */
    public List<String> describe() {
        if (!this.enabled) {
            return List.of("Continents: off (Minecraft's own land and oceans)");
        }
        return List.of(
                String.format(Locale.ROOT, "Continents %s blocks wide; land covers about %d%% of the world", this.continentSize, Math.round(this.landShare * 100)),
                String.format(Locale.ROOT, "Large islands %s blocks (%d%% of the usual number), archipelagos %s blocks (%d%%), islands %s blocks",
                        this.largeIslandSize, Math.round(this.largeIslandAmount * 100), this.archipelagoSize, Math.round(this.archipelagoAmount * 100), this.islandSize),
                String.format(Locale.ROOT, "Biomes on islands are %d%% of their size on continents", Math.round(this.islandBiomeScale * 100)),
                this.coastFeatures.describe().getFirst(),
                this.erosion.describe(),
                this.shores.describe());
    }

    /** A smallest and a largest size in blocks. Given the wrong way round, they're swapped. */
    public record SizeRange(int min, int max) {
        public SizeRange {
            if (min > max) {
                int swap = min;
                min = max;
                max = swap;
            }
        }

        static final Codec<SizeRange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(LIMIT_MIN_SIZE, LIMIT_MAX_SIZE).fieldOf("min").forGetter(SizeRange::min),
                Codec.intRange(LIMIT_MIN_SIZE, LIMIT_MAX_SIZE).fieldOf("max").forGetter(SizeRange::max)
        ).apply(instance, SizeRange::new));

        public SizeRange withMin(int min) {
            return new SizeRange(min, Math.max(min, this.max));
        }

        public SizeRange withMax(int max) {
            return new SizeRange(Math.min(this.min, max), max);
        }

        @Override
        public String toString() {
            return this.min == this.max ? String.format(Locale.ROOT, "%,d", this.min) : String.format(Locale.ROOT, "%,d to %,d", this.min, this.max);
        }
    }
}
