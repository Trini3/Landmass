package io.github.trini3.landmasslite.map;

import io.github.trini3.landmasslite.util.Climates;
import io.github.trini3.landmasslite.climate.ClimateZone;
import io.github.trini3.landmasslite.climate.LatitudeAxis;
import io.github.trini3.landmasslite.climate.LatitudeLayout;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Draws top-down maps straight from the climate sampler and biome source, without generating chunks,
 * so a map tens of thousands of blocks wide takes seconds. Safe to run off the server and render threads.
 */
public final class ClimateMapRenderer {
    /** Biomes are sampled high in the air so cave biomes don't show up. */
    public static final int SAMPLE_Y = 160;
    // Vanilla's continentalness thresholds for deep ocean and ocean.
    private static final double DEEP_OCEAN = -0.455;
    private static final double OCEAN = -0.19;

    public enum Mode {
        /** Temperature zones from the climate sampler, shaded by humidity, with oceans in blue. */
        CLIMATE,
        /** The biome that would generate at each point, coloured by type. */
        BIOMES
    }

    /** A square map: {@code size} pixels across, each covering {@code blocksPerPixel} blocks. */
    public record Request(Mode mode, int centerX, int centerZ, int size, int blocksPerPixel) {
    }

    public record Result(Path file, Request request, List<Map.Entry<String, Integer>> biomeCounts) {
    }

    private ClimateMapRenderer() {
    }

    public static Result render(ServerLevel level, @Nullable LatitudeLayout layout, Request request, Path outputDir) throws IOException {
        Climate.Sampler sampler = Climates.sampler(level.getChunkSource().randomState());
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();
        Map<String, Integer> counts = new HashMap<>();
        BufferedImage image = draw(sampler, biomeSource, layout, request, counts, () -> true);

        Files.createDirectories(outputDir);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        Path file = outputDir.resolve(String.format(Locale.ROOT, "%s_%s_%dbpp.png", request.mode().name().toLowerCase(Locale.ROOT), stamp, request.blocksPerPixel()));
        ImageIO.write(image, "png", file.toFile());

        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return new Result(file, request, sorted);
    }

    /**
     * Draws a map, with its guide lines and coordinates, without saving it. Biome maps add up how often
     * each biome appears in {@code counts}, when given. Returns null if {@code stillWanted} turns false
     * part way, as it does when the Customize screen's preview moves on before the map is finished.
     */
    @Nullable
    public static BufferedImage draw(Climate.Sampler sampler, BiomeSource biomeSource, @Nullable LatitudeLayout layout, Request request,
                                     @Nullable Map<String, Integer> counts, BooleanSupplier stillWanted) {
        return draw(sampler, biomeSource, layout, request, counts, null, stillWanted);
    }

    /**
     * The same, also storing each pixel's biome in {@code biomesOut} (row by row, {@code size * size} long) on biome maps,
     * so the screens can name the biome under the mouse.
     */
    @Nullable
    public static BufferedImage draw(Climate.Sampler sampler, BiomeSource biomeSource, @Nullable LatitudeLayout layout, Request request,
                                     @Nullable Map<String, Integer> counts, @Nullable Holder<Biome>[] biomesOut, BooleanSupplier stillWanted) {
        int size = request.size();
        int step = request.blocksPerPixel();
        int quartY = QuartPos.fromBlock(SAMPLE_Y);

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Map<Holder<Biome>, Integer> colors = new HashMap<>();
        for (int py = 0; py < size; py++) {
            if (!stillWanted.getAsBoolean()) {
                return null;
            }
            int z = request.centerZ() + (py - size / 2) * step;
            for (int px = 0; px < size; px++) {
                int x = request.centerX() + (px - size / 2) * step;
                int quartX = QuartPos.fromBlock(x);
                int quartZ = QuartPos.fromBlock(z);
                int rgb;
                if (request.mode() == Mode.CLIMATE) {
                    Climate.TargetPoint point = sampler.sample(quartX, quartY, quartZ);
                    rgb = climateColor(Climate.unquantizeCoord(point.temperature()), Climate.unquantizeCoord(point.humidity()),
                            Climate.unquantizeCoord(point.continentalness()));
                } else {
                    Holder<Biome> biome = Climates.biome(biomeSource, sampler, quartX, quartY, quartZ);
                    rgb = colors.computeIfAbsent(biome, ClimateMapRenderer::biomeColor);
                    if (biomesOut != null) {
                        biomesOut[py * size + px] = biome;
                    }
                    if (counts != null) {
                        counts.merge(biomeName(biome), 1, Integer::sum);
                    }
                }
                image.setRGB(px, py, rgb);
            }
        }
        if (layout != null) {
            drawGuides(image, layout, request);
        }
        MapMarkers.draw(image, request);
        return image;
    }

    public static String biomeName(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
    }

    /**
     * Draws the equator in red and the start of the polar zones as dashed pale blue lines. When the zones
     * repeat past the poles, every further equator and polar zone gets the same lines.
     */
    private static void drawGuides(BufferedImage image, LatitudeLayout layout, Request request) {
        int size = request.size();
        int center = layout.axis() == LatitudeAxis.Z ? request.centerZ() : request.centerX();
        long firstBlock = center - (long) (size / 2) * request.blocksPerPixel();
        long lastBlock = center + (long) (size - size / 2) * request.blocksPerPixel();
        double cycle = 2 * layout.poleDistance();
        long firstCycle = 0;
        long lastCycle = 0;
        if (layout.repeats()) {
            firstCycle = (long) Math.floor((firstBlock - layout.equator() - layout.polarDistance()) / cycle);
            lastCycle = (long) Math.ceil((lastBlock - layout.equator() + layout.polarDistance()) / cycle);
        }
        for (long i = firstCycle; i <= lastCycle; i++) {
            long equator = Math.round(layout.equator() + i * cycle);
            drawGuide(image, layout, request, equator, 0xE53935, false);
            drawGuide(image, layout, request, equator - layout.polarDistance(), 0x9ED8FF, true);
            drawGuide(image, layout, request, equator + layout.polarDistance(), 0x9ED8FF, true);
        }
    }

    private static void drawGuide(BufferedImage image, LatitudeLayout layout, Request request, long block, int rgb, boolean dashed) {
        int size = request.size();
        int center = layout.axis() == LatitudeAxis.Z ? request.centerZ() : request.centerX();
        long pixel = size / 2 + Math.floorDiv(block - center, (long) request.blocksPerPixel());
        // Thicker on big maps, matching the coordinate markers.
        int thickness = Math.max(1, size / 512);
        for (long line = pixel; line < pixel + thickness; line++) {
            if (line < 0 || line >= size) {
                continue;
            }
            for (int along = 0; along < size; along++) {
                if (dashed && (along / (4 * thickness)) % 2 == 1) {
                    continue;
                }
                if (layout.axis() == LatitudeAxis.Z) {
                    image.setRGB(along, (int) line, rgb);
                } else {
                    image.setRGB((int) line, along, rgb);
                }
            }
        }
    }

    public static int climateColor(double temperature, double humidity, double continentalness) {
        int rgb = ClimateZone.of(temperature).color();
        rgb = scale(rgb, 1.0 - 0.18 * Mth.clamp(humidity, -1.0, 1.0)); // wetter is darker
        if (continentalness < DEEP_OCEAN) {
            rgb = mix(rgb, 0x163C78, 0.75);
        } else if (continentalness < OCEAN) {
            rgb = mix(rgb, 0x2A62B5, 0.65);
        }
        return rgb;
    }

    private static int biomeColor(Holder<Biome> holder) {
        Biome biome = holder.value();
        float temperature = biome.getBaseTemperature();
        boolean snowy = temperature < 0.15F;
        int base;
        if (holder.is(BiomeTags.IS_DEEP_OCEAN)) {
            base = snowy ? 0x3A5F8F : 0x1B3F7A;
        } else if (holder.is(BiomeTags.IS_OCEAN)) {
            base = snowy ? 0x7FA8D6 : temperature > 0.8F ? 0x2E8FC9 : 0x2C66B8;
        } else if (holder.is(BiomeTags.IS_RIVER)) {
            base = snowy ? 0xA9CCEB : 0x4C9BE8;
        } else if (holder.is(BiomeTags.IS_BEACH)) {
            base = snowy ? 0xDDE6E8 : 0xE8D9A0;
        } else if (holder.is(BiomeTags.IS_BADLANDS)) {
            base = 0xC1622F;
        } else if (holder.is(BiomeTags.IS_JUNGLE)) {
            base = 0x2F8F1F;
        } else if (holder.is(BiomeTags.IS_SAVANNA)) {
            base = 0xA9A23A;
        } else if (holder.is(BiomeTags.IS_MOUNTAIN)) {
            // Before the snowy check, so snowy slopes, groves and peaks read as mountains rather than snowy plains.
            base = snowy ? 0xA7AEB5 : 0x8C8C8C;
        } else if (snowy) {
            base = holder.is(BiomeTags.IS_TAIGA) || holder.is(BiomeTags.IS_FOREST) ? 0xBFD6D0 : 0xEEF3F7;
        } else if (holder.is(BiomeTags.IS_TAIGA)) {
            base = 0x3D6E5A;
        } else if (temperature >= 1.5F && !biome.hasPrecipitation()) {
            base = 0xE3CF7A; // desert-like
        } else if (holder.is(BiomeTags.IS_FOREST)) {
            base = temperature >= 0.9F ? 0x2E7D32 : 0x3F8E3A;
        } else if (temperature >= 1.0F) {
            base = biome.hasPrecipitation() ? 0x6E9E2E : 0xC9B85C;
        } else if (temperature >= 0.5F) {
            base = 0x6DAF4F;
        } else {
            base = 0x5E8C6A;
        }
        // Nudge brightness per biome so neighbouring biomes of the same type stay distinguishable.
        int hash = biomeName(holder).hashCode();
        return scale(base, 0.9 + 0.2 * ((hash & 0xFF) / 255.0));
    }

    private static int scale(int rgb, double factor) {
        int r = Mth.clamp((int) Math.round(((rgb >> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp((int) Math.round(((rgb >> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp((int) Math.round((rgb & 0xFF) * factor), 0, 255);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int rgb, int other, double amount) {
        int r = (int) Math.round(((rgb >> 16) & 0xFF) * (1 - amount) + ((other >> 16) & 0xFF) * amount);
        int g = (int) Math.round(((rgb >> 8) & 0xFF) * (1 - amount) + ((other >> 8) & 0xFF) * amount);
        int b = (int) Math.round((rgb & 0xFF) * (1 - amount) + (other & 0xFF) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
