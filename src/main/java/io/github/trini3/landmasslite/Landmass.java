package io.github.trini3.landmasslite;

import com.mojang.logging.LogUtils;
import io.github.trini3.landmasslite.climate.ClimateHooks;
import io.github.trini3.landmasslite.climate.LatitudeTemperature;
import io.github.trini3.landmasslite.climate.HumidityBlend;
import io.github.trini3.landmasslite.climate.HumidityDeviation;
import io.github.trini3.landmasslite.climate.ScaleXZ;
import io.github.trini3.landmasslite.command.LatitudeCommand;
import io.github.trini3.landmasslite.config.TerrainConfig;
import io.github.trini3.landmasslite.continents.ContinentsFunction;
import io.github.trini3.landmasslite.continents.IslandBlend;
import io.github.trini3.landmasslite.continents.Islandness;
import io.github.trini3.landmasslite.continents.ShoreShaping;
import io.github.trini3.landmasslite.selftest.SelfTest;
import io.github.trini3.landmasslite.world.ChosenSpawn;
import io.github.trini3.landmasslite.world.LandmassGenerator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class Landmass implements ModInitializer {
    public static final String MODID = "landmass_lite";
    public static final Logger LOGGER = LogUtils.getLogger();

    // The overworld generator of the Landmass world type (data/landmass_lite/worldgen/world_preset).
    public static final Identifier LANDMASS = id("landmass_lite");

    @Override
    public void onInitialize() {
        // Registered so datapacks can also use the latitude function and the others directly in their own noise settings.
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("latitude_temperature"), LatitudeTemperature.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("scale_xz"), ScaleXZ.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("continents"), ContinentsFunction.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("island_blend"), IslandBlend.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("humidity_blend"), HumidityBlend.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("humidity_deviation"), HumidityDeviation.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("shore_shaping"), ShoreShaping.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("islandness"), Islandness.DATA_CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, LANDMASS, LandmassGenerator.CODEC);

        TerrainConfig.SPEC.load(FabricLoader.getInstance().getConfigDir().resolve(MODID + "-common.toml"));

        ServerLifecycleEvents.SERVER_STARTING.register(ClimateHooks::onServerAboutToStart);
        ServerLifecycleEvents.SERVER_STOPPED.register(ClimateHooks::onServerStopped);
        ServerLifecycleEvents.SERVER_STARTED.register(SelfTest::onServerStarted);
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> LatitudeCommand.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
