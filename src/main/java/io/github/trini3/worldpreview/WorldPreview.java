package io.github.trini3.worldpreview;

import com.mojang.logging.LogUtils;
import io.github.trini3.worldpreview.command.PreviewCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public class WorldPreview implements ModInitializer {
    public static final String MODID = "triniworldpreview";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> PreviewCommand.register(dispatcher));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }

    /**
     * True when Landmass or Trini's Terrain is installed. Both have their own world map (N key, pause menu button) and their
     * own preview on their world type's Customize screen, so this mod steps aside for those: no default key and no second
     * pause menu button.
     */
    public static boolean terrainModWithMapLoaded() {
        FabricLoader loader = FabricLoader.getInstance();
        return loader.isModLoaded("landmass") || loader.isModLoaded("trinisterrainmod");
    }
}
