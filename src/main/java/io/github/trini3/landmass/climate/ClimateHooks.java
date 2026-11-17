package io.github.trini3.landmass.climate;

import io.github.trini3.landmass.Landmass;
import io.github.trini3.landmass.continents.Continents;
import io.github.trini3.landmass.continents.ContinentsFunction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import io.github.trini3.landmass.continents.Shores;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Connects the climate bands to world generation. They apply to worlds made with the Landmass
 * world type, and to worlds that older versions of the mod (which applied to every world) set up.
 *
 * <p>Instead of replacing the overworld's noise settings (which would undo terrain datapacks), the
 * mod waits until each dimension builds its RandomState, the object that holds the final, seeded
 * noise router. By then every datapack, Lithostitched modifier and TerraBlender region is in place.
 * The changes are to that router's temperature, which gets wrapped rather than replaced, and to where it
 * samples continentalness, which gets the world's continents (see {@link Continents}).
 *
 * <p>Mods that preview a world before it's created build a RandomState of their own, and get the
 * bands through {@link #applyOutsideWorlds}.
 */
public final class ClimateHooks {
    @Nullable
    private static volatile WorldClimateSettings active;
    // Whether this game is running a world (singleplayer or server), even one without the bands.
    private static volatile boolean serverRunning;
    // The running world's overworld terrain settings. Only RandomStates made from these get the bands outside world setup,
    // so other dimensions (the Nether, the End, modded ones) are never touched, even when their terrain looks like the overworld's.
    @Nullable
    private static volatile NoiseGeneratorSettings overworldNoise;
    // The settings of the world being set up on the Create World screen, if it's a Landmass world. Client only.
    @Nullable
    private static volatile Supplier<Selection> createWorldSelection;
    // True while a world's ChunkMap creates its RandomState. apply() handles that one, knowing the dimension.
    private static final ThreadLocal<Boolean> IN_WORLD_SETUP = ThreadLocal.withInitial(() -> false);
    private static volatile boolean loggedPreview;
    private static volatile boolean loggedPreviewError;

    private ClimateHooks() {
    }

    public static void onServerAboutToStart(MinecraftServer server) {
        WorldClimateSettings settings = WorldClimateSettings.load(server);
        active = settings;
        serverRunning = true;
        overworldNoise = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOptional(LevelStem.OVERWORLD)
                .map(LevelStem::generator)
                .filter(generator -> generator instanceof NoiseBasedChunkGenerator)
                .map(generator -> ((NoiseBasedChunkGenerator) generator).generatorSettings().value())
                .orElse(null);
        if (settings == null) {
            Landmass.LOGGER.info("This world wasn't made with the Landmass world type, so it generates normally");
            return;
        }
        if (!settings.enabled()) {
            Landmass.LOGGER.info("Latitude climate bands are turned off for this world");
            return;
        }
        Landmass.LOGGER.info("Latitude climate bands for {}:", settings.dimensionNames());
        settings.latitude().describe().forEach(line -> Landmass.LOGGER.info("  {}", line));
        if (settings.humidityScale() != 1.0) {
            Landmass.LOGGER.info("  Humidity zones are {} times their vanilla size.", settings.humidityScale());
        }
        if (settings.biomeScale() != 1.0) {
            Landmass.LOGGER.info("  Individual biomes are {} times their vanilla size.", settings.biomeScale());
        }
        settings.coast().ifPresent(scale -> Landmass.LOGGER.info("  Coast Size Override: coasts are {} times their vanilla size.", scale));
        if (settings.continents().enabled() && !settings.coastline()) {
            Landmass.LOGGER.info("  Coastline features are off: no land bridges, sand bars, straits, coastal erosion or shore types.");
        }
        settings.deviation().describe().forEach(line -> Landmass.LOGGER.info("  {}", line));
        settings.features().describe().forEach(line -> Landmass.LOGGER.info("  {}", line));
        settings.effectiveContinents().describe().forEach(line -> Landmass.LOGGER.info("  {}", line));
    }

    public static void onServerStopped(MinecraftServer server) {
        active = null;
        serverRunning = false;
        overworldNoise = null;
    }

    /** The climate of the world being set up on the Create World screen, and its overworld's terrain settings. */
    public record Selection(WorldClimateSettings climate, NoiseGeneratorSettings overworldNoise) {
    }

    /** Lets world previews on the Create World screen know whether, and how, the world being set up gets bands. */
    public static void setCreateWorldSelection(Supplier<Selection> selection) {
        createWorldSelection = selection;
    }

    @Nullable
    public static WorldClimateSettings active() {
        return active;
    }

    /** Called from ChunkMapMixin just before a dimension creates its RandomState. */
    public static NoiseGeneratorSettings apply(ServerLevel level, NoiseGeneratorSettings settings, HolderGetter<NormalNoise> noises, long seed) {
        HolderGetter<Biome> biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        WorldClimateSettings world = active;
        if (world == null || !world.appliesTo(level.dimension())) {
            return settings;
        }
        if (LatitudeTemperature.isUsedBy(settings.noiseRouter().temperature())) {
            Landmass.LOGGER.info("{} already uses landmass:latitude_temperature from a datapack, so its temperature was left alone",
                    level.dimension().identifier());
            return settings;
        }
        Optional<Holder.Reference<NormalNoise>> wander = noises.get(ModNoises.BORDER_WANDER);
        if (wander.isEmpty()) {
            Landmass.LOGGER.error("Noise {} is missing, so {} keeps its normal temperature", ModNoises.BORDER_WANDER.identifier(), level.dimension().identifier());
            return settings;
        }
        Landmass.LOGGER.info("Applying latitude climate bands to {}", level.dimension().identifier());
        NoiseGeneratorSettings banded = withClimate(settings, wander.get(), world, seed, biomes);
        if (world.continents().enabled() && looksLikeOverworld(settings) && !ContinentsFunction.isUsedBy(banded.noiseRouter().continents())) {
            Landmass.LOGGER.warn("{}'s terrain doesn't use Minecraft's continentalness noise (a terrain mod may have its own), "
                    + "so the continent settings can't change its land and oceans", level.dimension().identifier());
        }
        return banded;
    }

    /** Marks the start of a world creating its own RandomState. Returns the previous mark, for {@link #endWorldSetup}. */
    public static boolean beginWorldSetup() {
        boolean outer = IN_WORLD_SETUP.get();
        IN_WORLD_SETUP.set(true);
        return outer;
    }

    public static void endWorldSetup(boolean outer) {
        IN_WORLD_SETUP.set(outer);
    }

    /**
     * Called from RandomStateMixin for every RandomState made outside a world's own setup. Those come from
     * mods that preview a world's biomes before it's created, or draw maps of it. They don't say which
     * dimension they mean, so overworld-type terrain (anything with continents) gets the overworld's bands:
     * the running world's if this game runs one, otherwise those of the world being set up on the Create
     * World screen, when Landmass is the selected world type.
     */
    public static NoiseGeneratorSettings applyOutsideWorlds(NoiseGeneratorSettings settings, HolderGetter<NormalNoise> noises, long seed) {
        if (IN_WORLD_SETUP.get()) {
            return settings;
        }
        // This runs for every RandomState in the game, so a problem here must never stop one being made.
        try {
            if (!looksLikeOverworld(settings) || LatitudeTemperature.isUsedBy(settings.noiseRouter().temperature())) {
                return settings;
            }
            WorldClimateSettings world;
            NoiseGeneratorSettings overworld;
            if (serverRunning) {
                world = active;
                overworld = overworldNoise;
            } else {
                Supplier<Selection> supplier = createWorldSelection;
                Selection selection = supplier != null ? supplier.get() : null;
                world = selection != null ? selection.climate() : null;
                overworld = selection != null ? selection.overworldNoise() : null;
            }
            // Only the overworld's own terrain: other dimensions are left alone even if their terrain looks similar.
            if (world == null || !world.appliesTo(Level.OVERWORLD) || overworld == null || (settings != overworld && !settings.equals(overworld))) {
                return settings;
            }
            Optional<Holder.Reference<NormalNoise>> wander = noises.get(ModNoises.BORDER_WANDER);
            if (wander.isEmpty()) {
                return settings;
            }
            if (!loggedPreview) {
                loggedPreview = true;
                Landmass.LOGGER.info("Adding latitude climate bands to a world preview or map made outside the world itself");
            }
            return withClimate(settings, wander.get(), world, seed, null);
        } catch (RuntimeException e) {
            if (!loggedPreviewError) {
                loggedPreviewError = true;
                Landmass.LOGGER.warn("Couldn't add climate bands to a world preview, so it shows normal temperatures", e);
            }
            return settings;
        }
    }

    /** Humidity for these settings, with sizes multiplied by {@code scale} (islands can have their own). */
    private static DensityFunction humidity(DensityFunction vegetation, WorldClimateSettings world, double scale) {
        return HumidityBlend.of(vegetation, world.humidityScale() * scale, world.biomeScale() * scale);
    }

    /** Overworld-type terrain has continents; the Nether and the End keep them at a constant 0. */
    private static boolean looksLikeOverworld(NoiseGeneratorSettings settings) {
        DensityFunction continents = settings.noiseRouter().continents();
        return continents.range().min() < continents.range().max();
    }

    /** The material rules with some beaches gravel (shingle) instead of sand, in patches as big as the Shores settings ask. */
    private static Holder<MaterialRule> withShingle(Holder<MaterialRule> rules, Shores shores, @Nullable HolderGetter<Biome> biomes) {
        if (!shores.enabled() || !shores.gravel() || biomes == null) {
            return rules;
        }
        MaterialRule gravel = MaterialRules.ifTrue(MaterialRules.abovePreliminarySurface(),
                MaterialRules.ifTrue(MaterialRules.isBiome(biomes, Biomes.BEACH, Biomes.SNOWY_BEACH),
                        MaterialRules.ifTrue(MaterialRules.noiseCondition2d(ModNoises.SHINGLE, shores.gravelThreshold(), 10.0),
                                MaterialRules.ifTrue(MaterialRules.stoneDepthCheck(0, true, CaveSurface.FLOOR),
                                        MaterialRules.state(Blocks.GRAVEL.defaultBlockState())))));
        return Holder.direct(MaterialRules.sequence(gravel, rules.value()));
    }

    /**
     * A copy of the settings whose temperature follows latitude, with the world's continents, and humidity zones and
     * biomes resized. The original object is never modified: it belongs to the registry and may be shared with other
     * dimensions or saved with the world.
     *
     * @param seed the world seed, which the continents are placed from
     * @param biomes the biome registry, for the gravel beaches; null where only biomes matter (previews)
     */
    public static NoiseGeneratorSettings withClimate(NoiseGeneratorSettings settings, Holder<NormalNoise> wanderNoise, WorldClimateSettings world,
                                                     long seed, @Nullable HolderGetter<Biome> biomes) {
        NoiseRouter original = settings.noiseRouter();
        // Landmass leaves the landscape to vanilla or the installed terrain mod wherever it can: no island-scale erosion and no
        // world height. Continents only move land and sea; shores are shaped only with coastline features on. Biome Size
        // (like Large Biomes) and the Coast Size Override stretch vanilla's noises, so they resize terrain features too.
        boolean coastline = world.coastline();
        Continents continents = world.continents().enabled() ? new Continents(world.effectiveContinents(), seed, world.coastScale() * world.terrainBiomeScale(), false, world.lithosphereCompat()) : null;
        // Individual biomes: local humidity patches and local temperature changes. Humidity zones are the bigger regions around them.
        double biomeSize = world.biomeScale();
        DensityFunction humidity = humidity(original.vegetation(), world, 1.0);
        DensityFunction mixing = biomeSize == 1.0 ? original.temperature() : new ScaleXZ(original.temperature(), biomeSize);
        if (continents != null && continents.splitsIslandBiomes()) {
            // Islands get their own size of humidity patches and local temperature changes, like their own size of biomes.
            double islandScale = world.continents().islandBiomeScale();
            humidity = continents.islandBlend(humidity, humidity(original.vegetation(), world, islandScale));
            mixing = continents.islandBlend(mixing, new ScaleXZ(original.temperature(), biomeSize * islandScale));
        }
        // Climate deviation patches: humidity jumps here, and the temperature (below) jumps by zones in the same places.
        Optional<DeviationZones> deviation = world.deviation().enabled() ? Optional.of(new DeviationZones(world.deviation(), seed)) : Optional.empty();
        if (deviation.isPresent() && world.deviation().humidity() > 0) {
            humidity = new HumidityDeviation(humidity, deviation.get());
        }
        // The noise gets the world seed when RandomState compiles the function.
        DensityFunction temperature = new LatitudeTemperature(mixing, Optional.of(humidity), wanderNoise, world.latitude(), deviation);
        NoiseRouter router = new NoiseRouter(
                temperature,
                humidity,
                original.continents(),
                original.erosion(),
                original.depth(),
                original.ridges(),
                original.chunkSurfaceLevel(),
                original.finalDensity());
        // Continents first, so resizing biomes afterwards also stretches the noise they keep for inland variety.
        if (continents != null) {
            router = continents.apply(router);
            if (coastline) {
                router = continents.shapeShores(router);
            }
        }
        // Biome Size works like Large Biomes: continentalness and erosion are stretched for terrain and biomes alike, so the
        // biomes keep matching the land. The Coast Size Override multiplies the continentalness stretch.
        router = BiomeScale.apply(router, world.coastScale() * world.terrainBiomeScale(), world.terrainBiomeScale(), world.lithosphereCompat());
        Optional<Aquifer.Config> aquifers = settings.aquifers();
        if (!world.features().aquifers()) {
            // Constant -1 makes every aquifer empty: no water at its own level in dips and caves, no flooded caves, no deep lava.
            // Oceans and rivers keep their water, since spots under sea level near the surface still use the sea's.
            aquifers = aquifers.map(config -> new Aquifer.Config(config.barrierNoise(), DensityFunctions.constant(-1.0F), config.fluidLevelSpreadNoise(),
                    config.lavaNoise(), config.exclusion(), config.surfaceLevel()));
        }
        if (continents != null || world.coast().isPresent() || world.terrainBiomeScale() != 1.0) {
            // The aquifers' own functions sample the terrain's noises too, so they follow the continents and biome sizes.
            aquifers = aquifers.map(config -> rewriteAquifer(config, continents, world));
        }
        return new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                router,
                continents != null && coastline ? withShingle(settings.materialRule(), world.effectiveContinents().shores(), biomes) : settings.materialRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                aquifers,
                settings.useLegacyRandomSource(),
                settings.debugFunctions());
    }

    /** An aquifer config whose functions use the same continents and biome sizes as the terrain. */
    private static Aquifer.Config rewriteAquifer(Aquifer.Config config, @Nullable Continents continents, WorldClimateSettings world) {
        NoiseRouter parts = new NoiseRouter(config.barrierNoise(), config.fluidLevelFloodednessNoise(), config.fluidLevelSpreadNoise(), config.lavaNoise(),
                config.exclusion(), config.surfaceLevel(), DensityFunctions.zero(), DensityFunctions.zero());
        if (continents != null) {
            parts = continents.apply(parts);
        }
        parts = BiomeScale.apply(parts, world.coastScale() * world.terrainBiomeScale(), world.terrainBiomeScale(), world.lithosphereCompat());
        return new Aquifer.Config(parts.temperature(), parts.vegetation(), parts.continents(), parts.erosion(), parts.depth(), parts.ridges());
    }
}
