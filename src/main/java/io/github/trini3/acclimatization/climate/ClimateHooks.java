package io.github.trini3.acclimatization.climate;

import io.github.trini3.acclimatization.Acclimatization;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Connects the climate zones to world generation, for the overworld of worlds with Custom Climate on.
 *
 * <p>Instead of replacing the overworld's noise settings (which would undo terrain datapacks), the mod waits until the
 * overworld builds its RandomState, the object that holds the final, seeded noise router. By then every datapack and
 * biome mod region is in place. Only the router's temperature and humidity (vegetation) change, which on Minecraft 26.x
 * pick the biomes and nothing else: the generator compiles its terrain from its own settings, so terrain stays exactly as
 * the world type or terrain mod makes it.
 *
 * <p>Mods that preview a world before it's created build a RandomState of their own, and get the zones through
 * {@link #applyOutsideWorlds}.
 */
public final class ClimateHooks {
    @Nullable
    private static volatile ClimateSettings active;
    private static volatile boolean serverRunning;
    // The running world's overworld terrain settings: only RandomStates made from these get the zones outside world setup.
    @Nullable
    private static volatile NoiseGeneratorSettings overworldNoise;
    @Nullable
    private static volatile Supplier<Selection> createWorldSelection;
    private static final ThreadLocal<Boolean> IN_WORLD_SETUP = ThreadLocal.withInitial(() -> false);
    private static volatile boolean loggedPreviewError;

    private ClimateHooks() {
    }

    /** The climate of the world being set up on the Create World screen, and its overworld's terrain settings. */
    public record Selection(ClimateSettings climate, NoiseGeneratorSettings overworldNoise) {
    }

    public static void setCreateWorldSelection(Supplier<Selection> selection) {
        createWorldSelection = selection;
    }

    /**
     * Whether this overworld generator comes from a mod with its own climate settings (Landmass or Trini's Terrain). Those
     * world types keep their own climate, and Custom Climate stays out of the way.
     */
    public static boolean hasOwnClimate(@Nullable ChunkGenerator generator) {
        if (generator == null) {
            return false;
        }
        String name = generator.getClass().getName();
        return name.startsWith("io.github.trini3.landmass.") || name.startsWith("io.github.trini3.trinisterrainmod.");
    }

    public static void onServerStarting(MinecraftServer server) {
        serverRunning = true;
        active = null;
        Optional<ChunkGenerator> generator = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM).getOptional(LevelStem.OVERWORLD)
                .map(LevelStem::generator);
        overworldNoise = generator.filter(each -> each instanceof NoiseBasedChunkGenerator)
                .map(each -> ((NoiseBasedChunkGenerator) each).generatorSettings().value())
                .orElse(null);
        Optional<ClimateSettings> settings = ClimateSettings.load(server);
        if (settings.isEmpty()) {
            return;
        }
        if (hasOwnClimate(generator.orElse(null))) {
            Acclimatization.LOGGER.info("This world's type (Landmass or Trini's Terrain) has its own climate settings, so Custom Climate is not used");
            return;
        }
        active = settings.get();
        Acclimatization.LOGGER.info("Custom Climate for the overworld:");
        settings.get().latitude().describe().forEach(line -> Acclimatization.LOGGER.info("  {}", line));
        if (settings.get().humidityScale() != 1.0) {
            Acclimatization.LOGGER.info("  Humidity zones are {} times their vanilla size.", settings.get().humidityScale());
        }
        if (settings.get().biomeScale() != 1.0) {
            Acclimatization.LOGGER.info("  Biome patches are {} times their vanilla size.", settings.get().biomeScale());
        }
        settings.get().deviation().describe().forEach(line -> Acclimatization.LOGGER.info("  {}", line));
    }

    public static void onServerStopped(MinecraftServer server) {
        active = null;
        serverRunning = false;
        overworldNoise = null;
    }

    @Nullable
    public static ClimateSettings active() {
        return active;
    }

    /** Called from ChunkMapMixin just before a dimension creates its RandomState. Only the overworld is changed. */
    public static NoiseGeneratorSettings apply(ServerLevel level, NoiseGeneratorSettings settings, HolderGetter<NormalNoise> noises, long seed) {
        ClimateSettings world = active;
        if (world == null || level.dimension() != Level.OVERWORLD || !looksLikeOverworld(settings)) {
            return settings;
        }
        if (LatitudeTemperature.isUsedBy(settings.noiseRouter().temperature())) {
            return settings;
        }
        Optional<Holder.Reference<NormalNoise>> wander = noises.get(ModNoises.BORDER_WANDER);
        if (wander.isEmpty()) {
            Acclimatization.LOGGER.error("Noise {} is missing, so the overworld keeps its normal climate", ModNoises.BORDER_WANDER.identifier());
            return settings;
        }
        Acclimatization.LOGGER.info("Applying Custom Climate to the overworld");
        return withClimate(settings, wander.get(), world, seed);
    }

    public static boolean beginWorldSetup() {
        boolean outer = IN_WORLD_SETUP.get();
        IN_WORLD_SETUP.set(true);
        return outer;
    }

    public static void endWorldSetup(boolean outer) {
        IN_WORLD_SETUP.set(outer);
    }

    /**
     * Called from RandomStateMixin for every RandomState made outside a world's own setup (world previews and maps). Only
     * RandomStates made from the overworld's own terrain settings get the zones: the running world's, or those of the
     * world being set up on the Create World screen with Custom Climate on.
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
            ClimateSettings world;
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
            if (world == null || overworld == null || (settings != overworld && !settings.equals(overworld))) {
                return settings;
            }
            Optional<Holder.Reference<NormalNoise>> wander = noises.get(ModNoises.BORDER_WANDER);
            return wander.isEmpty() ? settings : withClimate(settings, wander.get(), world, seed);
        } catch (RuntimeException e) {
            if (!loggedPreviewError) {
                loggedPreviewError = true;
                Acclimatization.LOGGER.warn("Couldn't add climate zones to a world preview, so it shows normal temperatures", e);
            }
            return settings;
        }
    }

    /** Overworld-type terrain has continents; the Nether and the End keep them at a constant 0. */
    private static boolean looksLikeOverworld(NoiseGeneratorSettings settings) {
        DensityFunction continents = settings.noiseRouter().continents();
        return continents.range().min() < continents.range().max();
    }

    /**
     * A copy of the settings whose temperature follows latitude, with humidity zones and biome patches resized. The original
     * object is never modified: it belongs to the registry and may be shared with other dimensions or saved with the world.
     */
    public static NoiseGeneratorSettings withClimate(NoiseGeneratorSettings settings, Holder<NormalNoise> wanderNoise, ClimateSettings world, long seed) {
        NoiseRouter original = settings.noiseRouter();
        double biomeSize = world.biomeScale();
        DensityFunction humidity = HumidityBlend.of(original.vegetation(), world.humidityScale(), biomeSize);
        DensityFunction mixing = biomeSize == 1.0 ? original.temperature() : new ScaleXZ(original.temperature(), biomeSize);
        Optional<DeviationZones> deviation = world.deviation().enabled() ? Optional.of(new DeviationZones(world.deviation(), seed)) : Optional.empty();
        if (deviation.isPresent() && world.deviation().humidity() > 0) {
            humidity = new HumidityDeviation(humidity, deviation.get());
        }
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
        return new NoiseGeneratorSettings(
                settings.noiseSettings(),
                settings.defaultBlock(),
                settings.defaultFluid(),
                router,
                settings.materialRule(),
                settings.spawnTarget(),
                settings.seaLevel(),
                settings.disableMobGeneration(),
                settings.aquifers(),
                settings.useLegacyRandomSource(),
                settings.debugFunctions());
    }
}
