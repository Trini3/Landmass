package io.github.trini3.landmasslite.config;

import io.github.trini3.landmasslite.climate.LatitudeAxis;
import io.github.trini3.landmasslite.climate.LatitudeLayout;
import io.github.trini3.landmasslite.climate.Poles;
import io.github.trini3.landmasslite.climate.Tropics;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * The mod's config file, config/landmass_lite-common.toml (read at startup). These are the defaults for new Landmass
 * worlds, and what the Customize screen starts from: when a world is first created they're copied
 * into that world's own settings file, so changing them later never reshapes a world you've already explored.
 */
public final class TerrainConfig {
    private static final ConfigSpec.Builder BUILDER = new ConfigSpec.Builder();

    static {
        BUILDER.comment(
                "Latitude climate bands: hot at the equator, colder toward the poles. They apply to worlds made with",
                "the Landmass Lite world type. These are the defaults for NEW worlds of that type, and what its",
                "Customize screen starts from. When a world is created they're copied to",
                "<world>/serverconfig/landmass_lite-climate.json, and that file controls the world from then on.",
                "To change an existing world, edit that file (only chunks generated afterwards change).")
                .push("climateBands");
    }

    public static final ConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS = BUILDER
            .comment("Dimensions that get climate bands. Any dimension that uses noise-based terrain works.")
            .defineListAllowEmpty("dimensions", List.of("minecraft:overworld"), () -> "minecraft:overworld", TerrainConfig::isResourceLocation);

    public static final ConfigSpec.EnumValue<LatitudeAxis> AXIS = BUILDER
            .comment("Z: bands run east-west, so the climate changes as you travel north or south (realistic).",
                    "X: bands run north-south, so the climate changes as you travel east or west.")
            .defineEnum("axis", LatitudeAxis.Z);

    public static final ConfigSpec.IntValue EQUATOR = BUILDER
            .comment("Block coordinate of the equator along the axis above. 0 puts it through the world's centre.")
            .defineInRange("equator", 0, -29_000_000, 29_000_000);

    public static final ConfigSpec.IntValue POLAR_DISTANCE = BUILDER
            .comment("Distance in blocks from the equator to where the frozen polar zone begins, in both directions.",
                    "The pole, the coldest point, is a quarter of this distance further out (125,000 blocks with the default).")
            .defineInRange("polarDistance", LatitudeLayout.DEFAULT_POLAR_DISTANCE, LatitudeLayout.MIN_POLAR_DISTANCE, LatitudeLayout.MAX_POLAR_DISTANCE);

    public static final ConfigSpec.EnumValue<Poles> POLES = BUILDER
            .comment("What happens past the poles.",
                    "REPEAT: the zones come back in reverse, like walking over the top of a globe and down the other side:",
                    "frozen, cold, temperate, warm, then another equator (250,000 blocks out with the defaults), and so on.",
                    "FROZEN: everything past the pole stays frozen.")
            .defineEnum("poles", Poles.REPEAT);

    public static final ConfigSpec.EnumValue<Tropics> TROPICS = BUILDER
            .comment("What the land near the equator looks like.",
                    "MIXED: humidity decides. Dry land becomes desert, wetter land savanna, forest or jungle, so deserts and",
                    "jungles share the same latitudes. Deserts are most common toward the outer edge of the hot zone, like Earth's subtropics.",
                    "DESERT: the whole hot zone is desert, with savannas and jungles in the warm zone further out.")
            .defineEnum("tropics", Tropics.MIXED);

    static {
        BUILDER.comment(
                "Relative sizes of the zones between the equator and the polar zone, in order from the equator.",
                "They're scaled to fill polarDistance, so they don't need to add up to 100. Set one to 0 to skip that zone.",
                "Hot is where deserts are (mixed with jungles by humidity when tropics = MIXED), warm is jungles and savannas,",
                "temperate is forests and plains, cold is taiga. Biome mods slot their own biomes into the same zones.")
                .push("zoneSizes");
    }

    public static final ConfigSpec.DoubleValue HOT = BUILDER.defineInRange("hot", LatitudeLayout.ZoneSizes.DEFAULT.hot(), 0.0, 1000.0);
    public static final ConfigSpec.DoubleValue WARM = BUILDER.defineInRange("warm", LatitudeLayout.ZoneSizes.DEFAULT.warm(), 0.0, 1000.0);
    public static final ConfigSpec.DoubleValue TEMPERATE = BUILDER.defineInRange("temperate", LatitudeLayout.ZoneSizes.DEFAULT.temperate(), 0.0, 1000.0);
    public static final ConfigSpec.DoubleValue COLD = BUILDER.defineInRange("cold", LatitudeLayout.ZoneSizes.DEFAULT.cold(), 0.0, 1000.0);

    static {
        BUILDER.pop();
        BUILDER.comment("How natural the borders between zones look. Set both to 0 for perfectly straight bands.")
                .push("borders");
    }

    public static final ConfigSpec.IntValue BORDER_WANDER = BUILDER
            .comment("How far, in blocks, zone borders can bend north or south in large, slow curves.")
            .defineInRange("wander", (int) LatitudeLayout.DEFAULT_BORDER_WANDER, 0, (int) LatitudeLayout.MAX_BORDER_NOISE);

    public static final ConfigSpec.IntValue BORDER_MIXING = BUILDER
            .comment("How far, in blocks, neighbouring zones blend into each other in smaller patches.",
                    "This uses the world's own temperature noise, so it keeps the feel of any terrain or biome pack.")
            .defineInRange("mixing", (int) LatitudeLayout.DEFAULT_BORDER_MIXING, 0, (int) LatitudeLayout.MAX_BORDER_NOISE);

    static {
        BUILDER.pop();
        BUILDER.comment("Advanced options. The defaults are fine for most worlds.")
                .push("advanced");
    }

    public static final ConfigSpec.DoubleValue LOCAL_VARIATION = BUILDER
            .comment("Extra temperature variation everywhere, on Minecraft's -1 to 1 temperature scale.",
                    "0 keeps clean bands. Around 0.2 to 0.4 lets neighbouring zones appear as patches inside each band.")
            .defineInRange("localVariation", 0.0, 0.0, 2.0);

    public static final ConfigSpec.DoubleValue DESERT_STRENGTH = BUILDER
            .comment("With tropics = MIXED: how easily dry land near the equator turns into desert. Higher means more desert;",
                    "0 means no deserts there at all.")
            .defineInRange("desertStrength", LatitudeLayout.DEFAULT_DESERT_STRENGTH, 0.0, 2.0);

    public static final ConfigSpec.ConfigValue<List<? extends String>> CUSTOM_PROFILE = BUILDER
            .comment("Replaces zoneSizes with your own temperature curve when not empty.",
                    "Each entry is \"percent of polarDistance:temperature\". For example",
                    "[\"0:0.4\", \"20:0.4\", \"30:0.8\", \"40:0.2\", \"75:-0.15\", \"100:-0.45\", \"125:-1\"]",
                    "gives an Earth-like layout: jungles and savannas at the equator, deserts in a belt further out,",
                    "then temperate, cold and frozen. Minecraft's temperature bands are:",
                    "frozen below -0.45, cold -0.45 to -0.15, temperate -0.15 to 0.2, warm 0.2 to 0.55, hot above 0.55.")
            .defineListAllowEmpty("customProfile", List.of(), () -> "0:1.0", TerrainConfig::isProfilePoint);

    static {
        BUILDER.pop();
        BUILDER.pop();
    }

    public static final ConfigSpec SPEC = BUILDER.build();

    private TerrainConfig() {
    }

    private static boolean isResourceLocation(Object value) {
        return value instanceof String text && Identifier.tryParse(text) != null;
    }

    private static boolean isProfilePoint(Object value) {
        return value instanceof String text && LatitudeLayout.ProfilePoint.parse(text).result().isPresent();
    }
}
