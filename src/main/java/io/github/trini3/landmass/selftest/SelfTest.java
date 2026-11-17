package io.github.trini3.landmass.selftest;

import io.github.trini3.landmass.util.Climates;
import io.github.trini3.landmass.Landmass;
import io.github.trini3.landmass.climate.ClimateHooks;
import io.github.trini3.landmass.climate.ClimateZone;
import io.github.trini3.landmass.climate.LatitudeAxis;
import io.github.trini3.landmass.climate.LatitudeLayout;
import io.github.trini3.landmass.climate.LatitudeTemperature;
import io.github.trini3.landmass.climate.Tropics;
import io.github.trini3.landmass.climate.WorldClimateSettings;
import io.github.trini3.landmass.command.LatitudeCommand;
import io.github.trini3.landmass.map.ClimateMapRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A check that runs on a real dedicated server when started with -Dlandmass.selftest=true
 * (the runSelfTest Gradle task). It confirms the overworld's temperature really follows latitude,
 * logs a text map and the biomes found at each latitude, saves PNG maps, then stops the server.
 * The GitHub Actions build runs it with and without other worldgen mods installed.
 */
public final class SelfTest {
    public static final String PROPERTY = "landmass.selftest";
    public static final String PASSED = "LANDMASS SELF-TEST PASSED";
    public static final String FAILED = "LANDMASS SELF-TEST FAILED";

    private static final Logger LOGGER = Landmass.LOGGER;
    // Fractions of the polar distance. With the default layout the pole is at 1.25, and with repeating zones
    // 1.6 and 2.0 are on the way back and 2.5 is the next equator.
    private static final double[] LATITUDES = {0.0, 0.1, 0.25, 0.4, 0.55, 0.7, 0.85, 1.0, 1.25, 1.6, 2.0, 2.5};
    /** How close the average temperature at a distance and at its mirror image past the pole must be. */
    private static final double MIRROR_TOLERANCE = 0.1;
    private static final int SAMPLES_PER_ROW = 400;
    private static final int ROW_WIDTH = 400_000;

    private SelfTest() {
    }

    public static void onServerStarted(MinecraftServer server) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        boolean passed;
        try {
            passed = run(server);
        } catch (Throwable t) {
            LOGGER.error("Self-test crashed", t);
            passed = false;
        }
        LOGGER.info(passed ? PASSED : FAILED);
        server.halt(false);
    }

    private static boolean run(MinecraftServer server) throws Exception {
        List<String> failures = new ArrayList<>();
        WorldClimateSettings settings = ClimateHooks.active();
        if (settings == null) {
            LOGGER.error("Self-test: no climate settings were loaded");
            return false;
        }
        LatitudeLayout layout = settings.latitude();
        ServerLevel level = server.overworld();
        RandomState randomState = level.getChunkSource().randomState();
        Climate.Sampler sampler = Climates.sampler(randomState);
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        LOGGER.info("Self-test: settings file {}", WorldClimateSettings.file(server));
        layout.describe().forEach(line -> LOGGER.info("Self-test: {}", line));
        LOGGER.info("Self-test: overworld generator {}, biome source {}", level.getChunkSource().getGenerator().getClass().getName(),
                biomeSource.getClass().getName());
        if (!(Climates.router(randomState).temperature() instanceof LatitudeTemperature)) {
            failures.add("The overworld's temperature function is " + Climates.router(randomState).temperature().getClass().getName()
                    + ", not the latitude function");
        }

        // Average temperature, zone mix and most common biomes at each distance from the equator.
        double[] averages = new double[LATITUDES.length];
        double[] hotShares = new double[LATITUDES.length];
        double[] warmShares = new double[LATITUDES.length];
        for (int row = 0; row < LATITUDES.length; row++) {
            double fraction = LATITUDES[row];
            Row sampled = sampleRow(layout, sampler, biomeSource, fraction * layout.polarDistance(), true);
            averages[row] = sampled.average();
            hotShares[row] = sampled.share(ClimateZone.HOT);
            warmShares[row] = sampled.share(ClimateZone.WARM);
            LOGGER.info(String.format(Locale.ROOT, "Self-test: %4.0f%% of polar distance (%,9d blocks): average temperature %+.2f | zones %s | biomes %s",
                    fraction * 100, Math.round(fraction * layout.polarDistance()), averages[row],
                    percentages(sampled.zones(), sampled.count()), top(sampled.biomes(), sampled.count(), 6)));
        }

        // Past the pole the zones should come back in reverse: the climate at a distance from the equator
        // and at the same distance back from the pole should match.
        if (layout.repeats()) {
            for (double fraction : new double[]{0.0, 0.55, 1.0}) {
                double distance = fraction * layout.polarDistance();
                double mirrored = 2 * layout.poleDistance() - distance;
                double near = sampleRow(layout, sampler, biomeSource, distance, false).average();
                double far = sampleRow(layout, sampler, biomeSource, mirrored, false).average();
                LOGGER.info(String.format(Locale.ROOT, "Self-test: past the pole, %,d blocks out averages %+.2f, like %,d blocks out (%+.2f)",
                        Math.round(mirrored), far, Math.round(distance), near));
                if (Math.abs(far - near) > MIRROR_TOLERANCE) {
                    failures.add(String.format(Locale.ROOT, "Zones should repeat past the pole: %,d blocks out averaged %.2f, but %,d blocks out averaged %.2f",
                            Math.round(mirrored), far, Math.round(distance), near));
                }
            }
        }

        // Only checked for the zone-based layout; a custom profile can go up and down on purpose.
        if (layout.customProfile().isEmpty()) {
            boolean mixed = layout.tropics() == Tropics.MIXED;
            // Mixed tropics are hottest at the outer edge of the hot zone, so they only cool steadily from there,
            // and repeating zones only cool as far as the pole.
            double coolingStarts = mixed ? layout.zones().hot() / layout.zones().total() : 0.0;
            for (int row = 1; row < averages.length; row++) {
                boolean beforePole = !layout.repeats() || LATITUDES[row] * layout.polarDistance() <= layout.poleDistance();
                if (beforePole && LATITUDES[row - 1] >= coolingStarts && averages[row] > averages[row - 1] + 0.05) {
                    failures.add(String.format(Locale.ROOT, "Temperature rose going poleward: %.2f at %.0f%% vs %.2f at %.0f%%",
                            averages[row], LATITUDES[row] * 100, averages[row - 1], LATITUDES[row - 1] * 100));
                }
            }
            if (mixed) {
                if (layout.desertStrength() > 0 && hotShares[0] < 0.01) {
                    failures.add(String.format(Locale.ROOT, "Mixed tropics should have some desert at the equator, but only %.1f%% was hot", hotShares[0] * 100));
                }
                if (warmShares[0] < 0.15) {
                    failures.add(String.format(Locale.ROOT, "Mixed tropics should have savanna, forest or jungle at the equator, but only %.1f%% was warm", warmShares[0] * 100));
                }
            } else if (averages[0] < LatitudeLayout.HOT_WARM) {
                failures.add(String.format(Locale.ROOT, "The equator should be hot on average, but averaged %.2f", averages[0]));
            }
            if (!layout.repeats() && averages[averages.length - 1] > LatitudeLayout.COLD_FROZEN) {
                failures.add(String.format(Locale.ROOT, "Far past the polar distance should be frozen, but averaged %.2f", averages[averages.length - 1]));
            }
        }

        logTextMap(layout, sampler);
        logTropicalHumidity(layout, sampler);
        logGeneratedChunks(level, layout, biomeSource, sampler);

        Path folder = server.getWorldPath(LevelResource.ROOT).resolve(LatitudeCommand.MAP_FOLDER).normalize();
        int step = (int) Math.ceil(layout.mapSpan() / 384);
        int centerX = layout.axis() == LatitudeAxis.Z ? 0 : layout.equator();
        int centerZ = layout.axis() == LatitudeAxis.Z ? layout.equator() : 0;
        for (ClimateMapRenderer.Mode mode : ClimateMapRenderer.Mode.values()) {
            ClimateMapRenderer.Result result = ClimateMapRenderer.render(level, layout,
                    new ClimateMapRenderer.Request(mode, centerX, centerZ, 384, step), folder);
            LOGGER.info("Self-test: saved {} map to {}", mode.name().toLowerCase(Locale.ROOT), result.file());
        }

        failures.forEach(failure -> LOGGER.error("Self-test failure: {}", failure));
        return failures.isEmpty();
    }

    /** Temperatures (and optionally biomes) along two lines at the given distance north and south of the equator. */
    private static Row sampleRow(LatitudeLayout layout, Climate.Sampler sampler, BiomeSource biomeSource, double distance, boolean withBiomes) {
        int quartY = QuartPos.fromBlock(ClimateMapRenderer.SAMPLE_Y);
        double sum = 0;
        int count = 0;
        Map<ClimateZone, Integer> zones = new EnumMap<>(ClimateZone.class);
        Map<String, Integer> biomes = new HashMap<>();
        for (int hemisphere : new int[]{1, -1}) {
            int along = (int) Math.round(layout.equator() + hemisphere * distance);
            for (int i = 0; i < SAMPLES_PER_ROW; i++) {
                int across = -ROW_WIDTH / 2 + i * (ROW_WIDTH / SAMPLES_PER_ROW);
                int x = layout.axis() == LatitudeAxis.Z ? across : along;
                int z = layout.axis() == LatitudeAxis.Z ? along : across;
                int quartX = QuartPos.fromBlock(x);
                int quartZ = QuartPos.fromBlock(z);
                double temperature = Climate.unquantizeCoord(sampler.sample(quartX, quartY, quartZ).temperature());
                sum += temperature;
                count++;
                zones.merge(ClimateZone.of(temperature), 1, Integer::sum);
                if (withBiomes) {
                    biomes.merge(ClimateMapRenderer.biomeName(Climates.biome(biomeSource, sampler, quartX, quartY, quartZ)), 1, Integer::sum);
                }
            }
        }
        return new Row(sum / count, zones, biomes, count);
    }

    private record Row(double average, Map<ClimateZone, Integer> zones, Map<String, Integer> biomes, int count) {
        double share(ClimateZone zone) {
            return this.zones.getOrDefault(zone, 0) / (double) this.count;
        }
    }

    /** Humidity percentiles and the hot (desert) share across the hot zone, which the desert share setting is calibrated against. */
    private static void logTropicalHumidity(LatitudeLayout layout, Climate.Sampler sampler) {
        int quartY = QuartPos.fromBlock(ClimateMapRenderer.SAMPLE_Y);
        double hotWidth = layout.polarDistance() * layout.zones().hot() / layout.zones().total();
        int rows = 40;
        int columns = 200;
        double[] humidity = new double[rows * columns];
        int hot = 0;
        for (int r = 0; r < rows; r++) {
            int along = (int) Math.round(layout.equator() + (r + 0.5) / rows * hotWidth);
            for (int c = 0; c < columns; c++) {
                int across = -ROW_WIDTH / 2 + c * (ROW_WIDTH / columns);
                int x = layout.axis() == LatitudeAxis.Z ? across : along;
                int z = layout.axis() == LatitudeAxis.Z ? along : across;
                Climate.TargetPoint point = sampler.sample(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z));
                humidity[r * columns + c] = Climate.unquantizeCoord(point.humidity());
                if (ClimateZone.of(Climate.unquantizeCoord(point.temperature())) == ClimateZone.HOT) {
                    hot++;
                }
            }
        }
        java.util.Arrays.sort(humidity);
        StringBuilder line = new StringBuilder("Self-test: hot zone humidity percentiles");
        for (int percent : new int[]{10, 25, 40, 50, 60, 75, 90}) {
            line.append(String.format(Locale.ROOT, " p%d=%+.2f", percent, humidity[humidity.length * percent / 100]));
        }
        LOGGER.info(line.append(String.format(Locale.ROOT, " | hot share %.1f%%", 100.0 * hot / humidity.length)).toString());
    }

    /** One character per cell: H W T C F for the zones on land, ~ for ocean. North is up. */
    private static void logTextMap(LatitudeLayout layout, Climate.Sampler sampler) {
        int rows = 48;
        int columns = 120;
        double alongSpan = layout.mapSpan();
        double rowBlocks = alongSpan / rows;
        double columnBlocks = rowBlocks / 2; // text cells are about twice as tall as wide
        int quartY = QuartPos.fromBlock(ClimateMapRenderer.SAMPLE_Y);
        StringBuilder map = new StringBuilder("Self-test: text map (H hot, W warm, T temperate, C cold, F frozen, ~ ocean; ")
                .append(String.format(Locale.ROOT, "each row %,d blocks, each column %,d blocks, > marks the equator)%n", Math.round(rowBlocks), Math.round(columnBlocks)));
        for (int row = 0; row < rows; row++) {
            int along = (int) Math.round(layout.equator() - alongSpan / 2 + (row + 0.5) * rowBlocks);
            boolean equatorRow = Math.abs(along - layout.equator()) <= rowBlocks / 2;
            map.append(equatorRow ? '>' : ' ');
            for (int column = 0; column < columns; column++) {
                int across = (int) Math.round((column - columns / 2 + 0.5) * columnBlocks);
                int x = layout.axis() == LatitudeAxis.Z ? across : along;
                int z = layout.axis() == LatitudeAxis.Z ? along : across;
                Climate.TargetPoint point = sampler.sample(QuartPos.fromBlock(x), quartY, QuartPos.fromBlock(z));
                if (Climate.unquantizeCoord(point.continentalness()) < -0.19) {
                    map.append('~');
                } else {
                    map.append(ClimateZone.of(Climate.unquantizeCoord(point.temperature())).symbol());
                }
            }
            map.append(System.lineSeparator());
        }
        LOGGER.info(map.toString());
    }

    /** Generates real chunks at a few latitudes and logs the biome that actually generated there. */
    private static void logGeneratedChunks(ServerLevel level, LatitudeLayout layout, BiomeSource biomeSource, Climate.Sampler sampler) {
        List<Double> distances = new ArrayList<>(List.of(0.0, 0.5 * layout.polarDistance(), 1.3 * layout.polarDistance()));
        if (layout.repeats()) {
            distances.add(2 * layout.poleDistance()); // the next equator
        }
        for (double distance : distances) {
            int along = (int) Math.round(layout.equator() + distance);
            int x = layout.axis() == LatitudeAxis.Z ? 8 : along;
            int z = layout.axis() == LatitudeAxis.Z ? along : 8;
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
            Holder<Biome> generated = level.getBiome(new BlockPos(x, surface, z));
            Holder<Biome> predicted = Climates.biome(biomeSource, sampler, QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));
            LOGGER.info("Self-test: generated chunk at x={} z={} (surface y={}): biome {} (climate sampler predicts {})",
                    x, z, surface, ClimateMapRenderer.biomeName(generated), ClimateMapRenderer.biomeName(predicted));
        }
    }

    private static String percentages(Map<ClimateZone, Integer> zones, int total) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<ClimateZone, Integer> entry : zones.entrySet()) {
            text.append(text.isEmpty() ? "" : " ").append(entry.getKey().symbol())
                    .append(String.format(Locale.ROOT, "%.0f%%", 100.0 * entry.getValue() / total));
        }
        return text.toString();
    }

    private static String top(Map<String, Integer> counts, int total, int limit) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            text.append(i == 0 ? "" : ", ").append(sorted.get(i).getKey())
                    .append(String.format(Locale.ROOT, " %.0f%%", 100.0 * sorted.get(i).getValue() / total));
        }
        return text.toString();
    }
}
