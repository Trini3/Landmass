package io.github.trini3.landmass.client;

import io.github.trini3.landmass.Landmass;
import io.github.trini3.landmass.climate.ClimateHooks;
import io.github.trini3.landmass.climate.WorldClimateSettings;
import io.github.trini3.landmass.world.LandmassGenerator;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.PresetEditor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import org.jetbrains.annotations.Nullable;

// The client entrypoint: it never loads on dedicated servers, so client code is safe here.
public class LandmassClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Landmass.id("main"));
    /** Opens the world map; N by default, changeable under Options > Controls > Key Binds. */
    public static final KeyMapping WORLD_MAP_KEY = new KeyMapping("key.landmass.world_map", InputConstants.Type.KEYBOARD,
            InputConstants.KEY_N, CATEGORY);
    /** The Customize button for the Landmass world type on the Create World screen (see WorldCreationUiStateMixin). */
    public static final PresetEditor PRESET_EDITOR = CustomizeTerrainScreen::new;

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(WORLD_MAP_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(LandmassClient::onClientTick);
        ScreenEvents.AFTER_INIT.register(LandmassClient::onScreenInit);
        ClimateHooks.setCreateWorldSelection(LandmassClient::createWorldSelection);
    }

    private static void onClientTick(Minecraft minecraft) {
        while (WORLD_MAP_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null) {
                minecraft.gui.setScreen(new WorldMapScreen(null));
            }
        }
    }

    /** A World map button in the bottom left corner of the pause menu. */
    private static void onScreenInit(Minecraft minecraft, net.minecraft.client.gui.screens.Screen screen, int width, int height) {
        if (screen instanceof PauseScreen pause) {
            Screens.getWidgets(pause).add(Button.builder(Component.translatable("landmass.world_map.button"),
                    button -> Minecraft.getInstance().gui.setScreen(new WorldMapScreen(pause)))
                    .bounds(4, height - 24, 90, 20).build());
        }
    }

    /** The climate and overworld terrain of the world being set up on the Create World screen, when Landmass is its world type. */
    @Nullable
    private static ClimateHooks.Selection createWorldSelection() {
        if (Minecraft.getInstance().gui.screen() instanceof CreateWorldScreen screen) {
            return LandmassGenerator.of(screen.getUiState().getSettings().selectedDimensions())
                    .map(generator -> new ClimateHooks.Selection(generator.climateOrDefaults(), generator.generatorSettings().value()))
                    .orElse(null);
        }
        return null;
    }
}
