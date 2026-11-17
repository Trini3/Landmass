package io.github.trini3.acclimatization.climate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.acclimatization.Acclimatization;
import io.github.trini3.acclimatization.util.CodecUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * One world's climate settings. A world uses Custom Climate when its folder has {@code serverconfig/acclimatization.json}:
 * the Create World screen writes it for a new world with the switch on, and server owners can add it by hand (an empty
 * {@code {}} gives the defaults). Kept in the world, so later changes to the mod can't put seams into existing terrain.
 *
 * <p>{@code humidityScale} resizes humidity zones (2 = twice vanilla's size). {@code biomeScale}, when present, resizes the
 * humidity and temperature patches that pick individual biomes; empty keeps the world type's own biome size. Terrain is never changed.
 */
public record ClimateSettings(LatitudeLayout latitude, double humidityScale, Optional<Double> biomeScale, ClimateDeviation deviation) {
    public static final String FILE_NAME = Acclimatization.MODID + ".json";
    /** Roughly how far apart changes in humidity are in vanilla, measured along straight lines: about 375 blocks. */
    public static final int VANILLA_HUMIDITY_ZONE = 400;
    /** Humidity zones from 100 to 10,000,000 blocks. The settings screen's slider recommends up to 1,000,000. */
    public static final double MIN_HUMIDITY_SCALE = 0.25;
    public static final double MAX_HUMIDITY_SCALE = 25_000.0;
    /** Biome sizes from 10% to 100,000% of the world type's. The slider recommends 25% to 2,500%. */
    public static final double MIN_SIZE_SCALE = 0.1;
    public static final double MAX_SIZE_SCALE = 1_000.0;

    public static final ClimateSettings DEFAULT = new ClimateSettings(LatitudeLayout.DEFAULT, 1.0, Optional.empty(), ClimateDeviation.DEFAULT);

    public static final Codec<ClimateSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(LatitudeLayout.MAP_CODEC.codec(), "latitude", LatitudeLayout.DEFAULT).forGetter(ClimateSettings::latitude),
            CodecUtil.field(Codec.doubleRange(MIN_HUMIDITY_SCALE, MAX_HUMIDITY_SCALE), "humidity_scale", 1.0).forGetter(ClimateSettings::humidityScale),
            Codec.doubleRange(MIN_SIZE_SCALE, MAX_SIZE_SCALE).optionalFieldOf("biome_scale").forGetter(ClimateSettings::biomeScale),
            CodecUtil.field(ClimateDeviation.CODEC, "deviation", ClimateDeviation.DEFAULT).forGetter(ClimateSettings::deviation)
    ).apply(instance, ClimateSettings::new));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String README = "Custom Climate settings for this world's overworld, from the Create World screen. "
            + "Edit and restart the world to change them; only chunks generated afterwards are affected. Delete this file to turn "
            + "Custom Climate off for this world. polar_distance is in blocks from the equator. poles is repeat (past each pole the "
            + "zones come back in reverse) or frozen. zones are relative sizes: hot, warm, temperate and cold fill polar_distance, and "
            + "frozen reaches from there to the pole. humidity_scale resizes humidity zones (2 = twice vanilla's size). "
            + "biome_scale, when present, resizes the humidity and temperature patches that pick individual biomes (2 = twice the "
            + "world type's size); without it biomes keep the world type's size. deviation: patches where the climate jumps up to "
            + "temperature_zones zones colder or warmer and humidity_zones humidity bands drier or wetter; size is their typical width "
            + "in blocks and frequency the share of the world they cover (0.05 = 5%, 0 = none).";

    public ClimateSettings withLatitude(LatitudeLayout latitude) {
        return new ClimateSettings(latitude, this.humidityScale, this.biomeScale, this.deviation);
    }

    public ClimateSettings withHumidityScale(double humidityScale) {
        return new ClimateSettings(this.latitude, humidityScale, this.biomeScale, this.deviation);
    }

    /** These settings with the Biome Size override on at this scale, or off (empty: the world type's own biome size). */
    public ClimateSettings withBiomeScale(Optional<Double> biomeScale) {
        return new ClimateSettings(this.latitude, this.humidityScale, biomeScale, this.deviation);
    }

    public ClimateSettings withDeviation(ClimateDeviation deviation) {
        return new ClimateSettings(this.latitude, this.humidityScale, this.biomeScale, deviation);
    }

    public static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve(FILE_NAME).normalize();
    }

    /** This world's settings from its file, or empty when the world has none (Custom Climate off). */
    public static Optional<ClimateSettings> read(Path file) {
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Optional<ClimateSettings> parsed = CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .resultOrPartial(error -> Acclimatization.LOGGER.error("Problem in {}: {}", file, error));
            if (parsed.isPresent()) {
                return parsed;
            }
        } catch (IOException | JsonParseException e) {
            Acclimatization.LOGGER.error("Couldn't read {}", file, e);
        }
        Acclimatization.LOGGER.error("Using the default climate settings for now. {} was left as it is; fix it and restart the world.", file);
        return Optional.of(DEFAULT);
    }

    public static void save(Path file, ClimateSettings settings) throws IOException {
        JsonObject out = new JsonObject();
        out.addProperty("_readme", README);
        CODEC.encodeStart(JsonOps.INSTANCE, settings).getOrThrow().getAsJsonObject().entrySet()
                .forEach(entry -> out.add(entry.getKey(), entry.getValue()));
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(out), StandardCharsets.UTF_8);
    }

    /** Settings chosen on the Create World screen, waiting for the world (by folder name) about to be created. */
    public record Pending(String folder, ClimateSettings settings) {
    }

    @Nullable
    private static volatile Pending pending;

    public static void arm(@Nullable Pending next) {
        pending = next;
    }

    /**
     * This world's settings: from its file, or, for the new world the Create World screen just armed settings for, those
     * settings (saved to the file first). Empty when the world doesn't use Custom Climate.
     */
    public static Optional<ClimateSettings> load(MinecraftServer server) {
        Path file = file(server);
        Optional<ClimateSettings> saved = read(file);
        if (saved.isPresent()) {
            Acclimatization.LOGGER.info("Loaded this world's climate settings from {}", file);
            return saved;
        }
        Pending armed = pending;
        if (armed == null) {
            return Optional.empty();
        }
        Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        if (folder == null || !folder.toString().equals(armed.folder())) {
            Acclimatization.LOGGER.warn("Custom Climate settings are waiting for a new world in folder {}, but this world's folder is {}",
                    armed.folder(), folder);
            return Optional.empty();
        }
        pending = null;
        try {
            save(file, armed.settings());
            Acclimatization.LOGGER.info("Saved this world's climate settings to {}", file);
        } catch (IOException e) {
            Acclimatization.LOGGER.error("Couldn't save {}. Custom Climate applies this session only.", file, e);
        }
        return Optional.of(armed.settings());
    }
}
