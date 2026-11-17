package io.github.trini3.landmass.climate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.Landmass;
import io.github.trini3.landmass.config.TerrainConfig;
import io.github.trini3.landmass.continents.CoastErosion;
import io.github.trini3.landmass.continents.CoastFeatures;
import io.github.trini3.landmass.continents.ContinentLayout;
import io.github.trini3.landmass.continents.Shores;
import io.github.trini3.landmass.util.CodecUtil;
import io.github.trini3.landmass.world.WorldFeatures;
import io.github.trini3.landmass.world.LandmassGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The settings one world uses. Saved in the world folder the first time the world starts with the mod, so later config
 * changes can't put seams into terrain that already exists.
 *
 * <p>{@code humidityScale} resizes humidity zones: 2 makes them twice as big as vanilla's, 0.5 half as big.
 * {@code biomeScale} resizes biomes: humidity patches and temperature changes (see {@link HumidityBlend}), and with
 * {@code biomeTerrain} also vanilla's continentalness and erosion, like Large Biomes.
 * {@code continents} places the land when Custom Landmass Generation is on; {@code coastline} false leaves out its coastline
 * features (land bridges, sand bars, straits, coastal erosion, shores). {@code coast} is the Coast Size Override: empty leaves
 * coast size to vanilla or the installed terrain mod. {@code features} lists the terrain features and structures left out.
 *
 * <p>Landmass favours compatibility: it never changes world height or mountain ranges, and only reshapes the landscape
 * through the settings above when they are switched on.
 */
public record WorldClimateSettings(boolean enabled, List<ResourceKey<Level>> dimensions, LatitudeLayout latitude, double humidityScale,
                                   double biomeScale, ContinentLayout continents, boolean coastline, Optional<Double> coast,
                                   WorldFeatures features, ClimateDeviation deviation, boolean biomeTerrain) {
    public static final String FILE_NAME = Landmass.MODID + "-climate.json";
    /** Roughly how far apart changes in humidity are in vanilla, measured along straight lines: about 375 blocks. */
    public static final int VANILLA_HUMIDITY_ZONE = 400;
    /** Humidity zones from 100 to 10,000,000 blocks. The Customize screen's slider recommends up to 1,000,000. */
    public static final double MIN_HUMIDITY_SCALE = 0.25;
    public static final double MAX_HUMIDITY_SCALE = 25_000.0;
    /** Biome and coast sizes from 10% to 100,000% of vanilla's. The slider recommends 25% to 2,500%. */
    public static final double MIN_SIZE_SCALE = 0.1;
    public static final double MAX_SIZE_SCALE = 1_000.0;

    public static final Codec<WorldClimateSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(Codec.BOOL, "enabled", true).forGetter(WorldClimateSettings::enabled),
            CodecUtil.field(ResourceKey.codec(Registries.DIMENSION).listOf(), "dimensions", List.of(Level.OVERWORLD)).forGetter(WorldClimateSettings::dimensions),
            CodecUtil.field(LatitudeLayout.MAP_CODEC.codec(), "latitude", LatitudeLayout.DEFAULT).forGetter(WorldClimateSettings::latitude),
            CodecUtil.field(Codec.doubleRange(MIN_HUMIDITY_SCALE, MAX_HUMIDITY_SCALE), "humidity_scale", 1.0).forGetter(WorldClimateSettings::humidityScale),
            CodecUtil.field(Codec.doubleRange(MIN_SIZE_SCALE, MAX_SIZE_SCALE), "biome_scale", 1.0).forGetter(WorldClimateSettings::biomeScale),
            CodecUtil.field(ContinentLayout.CODEC, "continents", ContinentLayout.OFF).forGetter(WorldClimateSettings::continents),
            CodecUtil.field(Codec.BOOL, "coastline_features", true).forGetter(WorldClimateSettings::coastline),
            Codec.doubleRange(MIN_SIZE_SCALE, MAX_SIZE_SCALE).optionalFieldOf("coast_scale").forGetter(WorldClimateSettings::coast),
            CodecUtil.field(WorldFeatures.CODEC, "features", WorldFeatures.DEFAULT).forGetter(WorldClimateSettings::features),
            CodecUtil.field(ClimateDeviation.CODEC, "deviation", ClimateDeviation.OFF).forGetter(WorldClimateSettings::deviation),
            CodecUtil.field(Codec.BOOL, "biome_terrain_scaling", true).forGetter(WorldClimateSettings::biomeTerrain)
    ).apply(instance, WorldClimateSettings::new));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String README = "Settings for this world, copied from config/landmass-common.toml or the Customize screen when the world was created. "
            + "Edit and restart the world to change them; only chunks generated afterwards are affected. "
            + "polar_distance is in blocks from the equator. poles is repeat (past each pole the zones come back in reverse) or frozen. "
            + "zones are relative sizes: hot, warm, temperate and cold fill polar_distance, and frozen reaches from there to the pole. "
            + "border_wander and border_mixing are in blocks. humidity_scale resizes humidity zones (2 = twice vanilla's size). "
            + "biome_scale resizes biomes (2 = twice vanilla's size); with biome_terrain_scaling true it works like Large Biomes and "
            + "stretches vanilla's continentalness and erosion noises too, false only resizes humidity and temperature patches. "
            + "continents: enabled is Custom Landmass Generation (false keeps the land and oceans of vanilla or the installed terrain mod); "
            + "sizes are in blocks, land_share is the part of the world that is land (0.35 = 35%), the amounts say how common large islands and "
            + "archipelagos are (1 = the usual number, 0 = none), island_biome_scale is the size of biomes on islands compared with continents. "
            + "coastline_features false leaves out land bridges, sand bars, straits, coastal erosion and shore types. "
            + "coast_scale, when present, is the Coast Size Override (2 = coasts twice as wide); without it coast size is left to vanilla or "
            + "the installed terrain mod. "
            + "features: false leaves ravines, cave_tunnels (carver caves), lava_lakes, aquifers (aquifer lakes, flooded caves and deep "
            + "lava) or springs out of the overworld; disabled_structures lists vanilla structure groups left out. "
            + "deviation: patches where the climate jumps up to temperature_zones zones (hot, warm, temperate, cold, frozen) colder or warmer and "
            + "humidity_zones humidity bands drier or wetter; size is their typical width in blocks and frequency the share of the world they cover "
            + "(0.05 = 5%, 0 = none). "
            + "Delete this file to re-copy the settings the world was created with.";

    public WorldClimateSettings withDeviation(ClimateDeviation deviation) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, this.biomeScale, this.continents,
                this.coastline, this.coast, this.features, deviation, this.biomeTerrain);
    }

    /** These settings with roughly this share of the hot zone desert; see {@link LatitudeLayout#withDesertShare}. */
    public WorldClimateSettings withDesertShare(double desertShare) {
        return this.withLatitude(this.latitude.withDesertShare(desertShare));
    }

    /** These settings with this climate band waviness; see {@link LatitudeLayout#withWaviness}. */
    public WorldClimateSettings withWaviness(double waviness) {
        return this.withLatitude(this.latitude.withWaviness(waviness));
    }

    /** These settings with zones of these widths in blocks; see {@link LatitudeLayout#withZoneWidths}. */
    public WorldClimateSettings withZoneWidths(int hot, int warm, int temperate, int cold, int frozen) {
        return this.withLatitude(this.latitude.withZoneWidths(hot, warm, temperate, cold, frozen));
    }

    public WorldClimateSettings withLatitude(LatitudeLayout latitude) {
        return new WorldClimateSettings(this.enabled, this.dimensions, latitude, this.humidityScale, this.biomeScale, this.continents,
                this.coastline, this.coast, this.features, this.deviation, this.biomeTerrain);
    }

    public WorldClimateSettings withHumidityScale(double humidityScale) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, humidityScale, this.biomeScale, this.continents,
                this.coastline, this.coast, this.features, this.deviation, this.biomeTerrain);
    }

    public WorldClimateSettings withBiomeScale(double biomeScale) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, biomeScale, this.continents,
                this.coastline, this.coast, this.features, this.deviation, this.biomeTerrain);
    }

    public WorldClimateSettings withContinents(ContinentLayout continents) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, this.biomeScale, continents,
                this.coastline, this.coast, this.features, this.deviation, this.biomeTerrain);
    }

    public WorldClimateSettings withCoastline(boolean coastline) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, this.biomeScale, this.continents,
                coastline, this.coast, this.features, this.deviation, this.biomeTerrain);
    }

    /** These settings with the Coast Size Override on at this scale, or off (empty). */
    public WorldClimateSettings withCoast(Optional<Double> coast) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, this.biomeScale, this.continents,
                this.coastline, coast, this.features, this.deviation, this.biomeTerrain);
    }

    public WorldClimateSettings withFeatures(WorldFeatures features) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, this.biomeScale, this.continents,
                this.coastline, this.coast, features, this.deviation, this.biomeTerrain);
    }

    /** These settings with Biome Size stretching vanilla's terrain noises (like Large Biomes) or only the climate patches. */
    public WorldClimateSettings withBiomeTerrain(boolean biomeTerrain) {
        return new WorldClimateSettings(this.enabled, this.dimensions, this.latitude, this.humidityScale, this.biomeScale, this.continents,
                this.coastline, this.coast, this.features, this.deviation, biomeTerrain);
    }

    /** How much Biome Size stretches vanilla's continentalness and erosion: the biome scale, or 1 with terrain scaling off. */
    public double terrainBiomeScale() {
        return this.biomeTerrain ? this.biomeScale : 1.0;
    }

    /** How much wider than vanilla's coasts are: the Coast Size Override, or 1 (vanilla's or the terrain mod's own) when it is off. */
    public double coastScale() {
        return this.coast.orElse(1.0);
    }

    /** The continent layout the world generates: without coastline features when those are switched off. */
    public ContinentLayout effectiveContinents() {
        return this.coastline ? this.continents
                : this.continents.withCoastFeatures(CoastFeatures.NONE).withErosion(CoastErosion.NONE).withShores(Shores.NONE);
    }

    public boolean appliesTo(ResourceKey<Level> dimension) {
        return this.enabled && this.dimensions.contains(dimension);
    }

    /** The dimension ids, for messages: "minecraft:overworld, minecraft:the_nether". */
    public String dimensionNames() {
        return this.dimensions.stream().map(dimension -> dimension.identifier().toString()).collect(Collectors.joining(", "));
    }

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME).normalize();
    }

    /**
     * This world's settings, from its settings file. A new Landmass world gets the file first, filled
     * from the Customize screen or the config. Null for a world made with another world type, unless an
     * older version of the mod (which applied to every world) already gave it a settings file.
     */
    @Nullable
    public static WorldClimateSettings load(MinecraftServer server) {
        Optional<LandmassGenerator> generator = LandmassGenerator.of(server);
        Path file = file(server);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement json = JsonParser.parseReader(reader);
                Optional<WorldClimateSettings> parsed = CODEC.parse(JsonOps.INSTANCE, json)
                        .resultOrPartial(error -> Landmass.LOGGER.error("Problem in {}: {}", file, error));
                if (parsed.isPresent()) {
                    Landmass.LOGGER.info("Loaded this world's climate settings from {}", file);
                    return parsed.get();
                }
            } catch (IOException | JsonParseException e) {
                Landmass.LOGGER.error("Couldn't read {}", file, e);
            }
            Landmass.LOGGER.error("Using the default climate settings for now. {} was left as it is; fix it and restart the world.", file);
            return generator.map(LandmassGenerator::climateOrDefaults).orElseGet(WorldClimateSettings::fromConfig);
        }
        if (generator.isEmpty()) {
            return null;
        }

        WorldClimateSettings settings = generator.get().climateOrDefaults();
        try {
            save(file, settings);
            Landmass.LOGGER.info("Saved this world's climate settings to {}", file);
        } catch (IOException e) {
            Landmass.LOGGER.error("Couldn't save {}. If the mod config changes, this world's climate may change with it.", file, e);
        }
        return settings;
    }

    public static void save(Path file, WorldClimateSettings settings) throws IOException {
        JsonObject out = new JsonObject();
        out.addProperty("_readme", README);
        CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow().getAsJsonObject().entrySet()
                .forEach(entry -> out.add(entry.getKey(), entry.getValue()));
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(out), StandardCharsets.UTF_8);
    }

    public static WorldClimateSettings fromConfig() {
        List<ResourceKey<Level>> dimensions = new ArrayList<>();
        for (String id : TerrainConfig.DIMENSIONS.get()) {
            Identifier location = Identifier.tryParse(id);
            if (location == null) {
                Landmass.LOGGER.warn("Ignoring dimension \"{}\" in the config: not a valid id", id);
            } else {
                dimensions.add(ResourceKey.create(Registries.DIMENSION, location));
            }
        }

        LatitudeLayout.ZoneSizes zones = LatitudeLayout.ZoneSizes.of(
                TerrainConfig.HOT.get(), TerrainConfig.WARM.get(), TerrainConfig.TEMPERATE.get(), TerrainConfig.COLD.get());
        if (zones.total() <= 0) {
            Landmass.LOGGER.warn("All zone sizes in the config are 0, so the default sizes are used");
            zones = LatitudeLayout.ZoneSizes.DEFAULT;
        }

        List<LatitudeLayout.ProfilePoint> profile = new ArrayList<>();
        for (String entry : TerrainConfig.CUSTOM_PROFILE.get()) {
            LatitudeLayout.ProfilePoint.parse(entry)
                    .resultOrPartial(error -> Landmass.LOGGER.warn("Ignoring custom profile entry: {}", error))
                    .ifPresent(profile::add);
        }

        LatitudeLayout layout = new LatitudeLayout(
                TerrainConfig.AXIS.get(),
                TerrainConfig.EQUATOR.get(),
                TerrainConfig.POLAR_DISTANCE.get(),
                TerrainConfig.POLES.get(),
                zones,
                TerrainConfig.TROPICS.get(),
                TerrainConfig.DESERT_STRENGTH.get(),
                TerrainConfig.BORDER_WANDER.get(),
                TerrainConfig.BORDER_MIXING.get(),
                TerrainConfig.LOCAL_VARIATION.get(),
                profile,
                LatitudeLayout.DEFAULT_WAVINESS,
                LatitudeLayout.DEFAULT_MEANDER,
                LatitudeLayout.DEFAULT_DESERT_SHARE);
        return new WorldClimateSettings(true, List.copyOf(dimensions), layout, 1.0, 1.0, ContinentLayout.DEFAULT, true, Optional.empty(),
                WorldFeatures.DEFAULT, ClimateDeviation.DEFAULT, true);
    }
}
