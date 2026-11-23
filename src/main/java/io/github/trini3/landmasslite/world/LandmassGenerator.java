package io.github.trini3.landmasslite.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmasslite.Landmass;
import io.github.trini3.landmasslite.climate.ClimateHooks;
import io.github.trini3.landmasslite.climate.WorldClimateSettings;
import io.github.trini3.landmasslite.mixin.ChunkGeneratorAccessor;
import io.github.trini3.landmasslite.mixin.NoiseBasedChunkGeneratorAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPreset;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The overworld generator of the "Landmass Lite" world type. It generates exactly like Minecraft's own
 * noise generator, from the same noise settings, so terrain datapacks and biome mods still apply. Being its
 * own type is what marks a world as Landmass, and it carries the settings picked on the Customize
 * screen into the new world, including a spawn point chosen on its preview map (see {@link ChosenSpawn}).
 */
public class LandmassGenerator extends NoiseBasedChunkGenerator {
    public static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET, Landmass.id("landmass_lite"));

    public static final MapCodec<LandmassGenerator> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
            // The settings as chosen, not the ones with the climate bands in (see useSettings), so the world saves the same as before.
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(generator -> generator.savedSettings),
            // Only there once the Customize screen was used; otherwise a new world starts from the mod config.
            WorldClimateSettings.CODEC.optionalFieldOf("climate").forGetter(LandmassGenerator::climate),
            // Only there once a spawn point was picked on the preview map; otherwise Minecraft picks one. Only x and z count.
            BlockPos.CODEC.optionalFieldOf("spawn").forGetter(LandmassGenerator::spawn)
    ).apply(instance, instance.stable(LandmassGenerator::new)));

    private final Holder<NoiseGeneratorSettings> savedSettings;
    private final Optional<WorldClimateSettings> climate;
    private final Optional<BlockPos> spawn;

    public LandmassGenerator(BiomeSource biomeSource, Holder<NoiseGeneratorSettings> settings, Optional<WorldClimateSettings> climate,
                                  Optional<BlockPos> spawn) {
        super(biomeSource, settings);
        this.savedSettings = settings;
        this.climate = climate;
        this.spawn = spawn;
        this.filterBiomeFeatures();
    }

    /**
     * The terrain features and structures this world leaves out: from its settings file once the world runs (so edits to the
     * file apply), else from the Customize screen or the config.
     */
    public WorldFeatures features() {
        WorldClimateSettings running = ClimateHooks.active();
        return running != null ? running.features() : this.climateOrDefaults().features();
    }

    /** Makes every biome's carvers and features, as this generator sees them, leave out the terrain features turned off. */
    private void filterBiomeFeatures() {
        ChunkGeneratorAccessor accessor = (ChunkGeneratorAccessor) (Object) this;
        Function<Holder<Biome>, BiomeGenerationSettings> original = accessor.landmass$getGenerationSettingsGetter();
        Map<Holder<Biome>, BiomeGenerationSettings> filtered = new ConcurrentHashMap<>();
        accessor.landmass$setGenerationSettingsGetter(biome -> {
            WorldFeatures features = this.features();
            return features.filtersBiomes() ? filtered.computeIfAbsent(biome, each -> features.filter(original.apply(each))) : original.apply(biome);
        });
    }

    /**
     * Builds the terrain from these settings from now on: the world's own settings with its climate bands, continents and
     * the rest in. Called once, while the world's dimension is set up. The world still saves its original settings.
     */
    public void useSettings(Holder<NoiseGeneratorSettings> settings) {
        ((NoiseBasedChunkGeneratorAccessor) (Object) this).landmass$setSettings(settings);
    }

    /** Leaves the structure groups turned off out of the structure sets this world places (and /locate finds). */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        WorldFeatures features = this.features();
        return super.createState(features.filtersStructures() ? features.filter(structureSets) : structureSets, randomState, seed);
    }

    public Optional<WorldClimateSettings> climate() {
        return this.climate;
    }

    /** The settings a new world of this type starts with: the Customize screen's, or else the mod config's. */
    public WorldClimateSettings climateOrDefaults() {
        return this.climate.orElseGet(WorldClimateSettings::fromConfig);
    }

    /** Where a new world should spawn players, if one was picked on the Customize screen's preview map. */
    public Optional<BlockPos> spawn() {
        return this.spawn;
    }

    public LandmassGenerator withClimate(WorldClimateSettings climate) {
        return new LandmassGenerator(this.getBiomeSource(), this.savedSettings, Optional.of(climate), this.spawn);
    }

    public LandmassGenerator withSpawn(Optional<BlockPos> spawn) {
        return new LandmassGenerator(this.getBiomeSource(), this.savedSettings, this.climate, spawn);
    }

    /** The overworld generator, if these dimensions are a Landmass world. */
    public static Optional<LandmassGenerator> of(WorldDimensions dimensions) {
        return dimensions.get(LevelStem.OVERWORLD).flatMap(stem -> of(stem.generator()));
    }

    /** The overworld generator, if the server's world is a Landmass world. */
    public static Optional<LandmassGenerator> of(MinecraftServer server) {
        return server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOptional(LevelStem.OVERWORLD)
                .flatMap(stem -> of(stem.generator()));
    }

    private static Optional<LandmassGenerator> of(ChunkGenerator generator) {
        return generator instanceof LandmassGenerator terrain ? Optional.of(terrain) : Optional.empty();
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }
}
