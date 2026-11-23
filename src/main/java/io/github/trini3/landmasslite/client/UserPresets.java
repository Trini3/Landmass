package io.github.trini3.landmasslite.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.github.trini3.landmasslite.Landmass;
import io.github.trini3.landmasslite.climate.WorldClimateSettings;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Presets saved by the player from the Customize screen: one JSON file each in {@code config/landmass_lite-presets/}, holding
 * every setting of the screen (the world's climate settings, as in its climate file, and the world height). They can be copied
 * to other installs or shared.
 */
final class UserPresets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    static final int MAX_NAME = 32;

    record Preset(String name, Path file, WorldClimateSettings climate) {
    }

    private UserPresets() {
    }

    private static Path folder() {
        return FabricLoader.getInstance().getConfigDir().resolve(Landmass.MODID + "-presets");
    }

    /** Every saved preset that can be read, by name. Files that can't be read are skipped with a warning in the log. */
    static List<Preset> list() {
        Path folder = folder();
        List<Preset> presets = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return presets;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(file -> file.getFileName().toString().endsWith(".json")).forEach(file -> read(file).ifPresent(presets::add));
        } catch (IOException e) {
            Landmass.LOGGER.warn("Couldn't list the saved presets in {}", folder, e);
        }
        presets.sort(Comparator.comparing(preset -> preset.name().toLowerCase(Locale.ROOT)));
        return presets;
    }

    private static Optional<Preset> read(Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String fallback = file.getFileName().toString().replaceFirst("\\.json$", "");
            String name = json.has("name") ? json.get("name").getAsString() : fallback;
            JsonElement climate = json.get("climate");
            Optional<WorldClimateSettings> settings = WorldClimateSettings.CODEC.parse(JsonOps.INSTANCE, climate)
                    .resultOrPartial(error -> Landmass.LOGGER.warn("Problem in saved preset {}: {}", file, error));
            return settings.map(each -> new Preset(name, file, each));
        } catch (Exception e) {
            Landmass.LOGGER.warn("Couldn't read the saved preset {}", file, e);
            return Optional.empty();
        }
    }

    /** Saves a preset under this name, replacing one of the same name. Returns false if it couldn't be written. */
    static boolean save(String name, WorldClimateSettings climate) {
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.add("climate", WorldClimateSettings.CODEC.encodeStart(JsonOps.INSTANCE, climate).getOrThrow());
        Path file = folder().resolve(fileName(name) + ".json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(json), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            Landmass.LOGGER.warn("Couldn't save the preset {}", file, e);
            return false;
        }
    }

    static void delete(Preset preset) {
        try {
            Files.deleteIfExists(preset.file());
        } catch (IOException e) {
            Landmass.LOGGER.warn("Couldn't delete the preset {}", preset.file(), e);
        }
    }

    /** A file name for a preset name: letters, digits, dashes and underscores only (lower case, so names differing in case share it). */
    private static String fileName(String name) {
        String safe = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "_");
        return safe.isEmpty() || safe.equals("_") ? "preset" : safe;
    }
}
