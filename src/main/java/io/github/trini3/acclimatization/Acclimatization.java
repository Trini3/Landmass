package io.github.trini3.acclimatization;

import com.mojang.logging.LogUtils;
import io.github.trini3.acclimatization.climate.ClimateHooks;
import io.github.trini3.acclimatization.climate.HumidityBlend;
import io.github.trini3.acclimatization.climate.HumidityDeviation;
import io.github.trini3.acclimatization.climate.LatitudeTemperature;
import io.github.trini3.acclimatization.climate.ScaleXZ;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

/**
 * Acclimatization: latitude climate zones for any world type. Turned on per world with the Custom Climate switch on the
 * Create World screen's World tab; only the overworld's temperature and humidity change, never its terrain.
 */
public class Acclimatization implements ModInitializer {
    public static final String MODID = "acclimatization";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        // Registered so the climate functions can be saved or used by datapacks; the mod itself builds them in code.
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("latitude_temperature"), LatitudeTemperature.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("scale_xz"), ScaleXZ.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("humidity_blend"), HumidityBlend.DATA_CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("humidity_deviation"), HumidityDeviation.DATA_CODEC);

        ServerLifecycleEvents.SERVER_STARTING.register(ClimateHooks::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPED.register(ClimateHooks::onServerStopped);
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
