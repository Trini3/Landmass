package io.github.trini3.worldpreview.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.trini3.worldpreview.WorldPreview;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.network.chat.Component;

import java.util.Set;

// The client entrypoint: it never loads on dedicated servers, so client code is safe here.
public class WorldPreviewClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(WorldPreview.id("main"));
    /** World types whose mods have their own preview on their Customize screen; the World Preview button steps aside for them. */
    private static final Set<String> OWN_PREVIEW_NAMESPACES = Set.of("landmass", "trinisterrainmod");
    /**
     * Opens the world map. N by default, unless Landmass or Trini's Terrain (which open their own map with N) is installed:
     * then it starts unbound, and can be set under Options > Controls > Key Binds.
     */
    public static final KeyMapping WORLD_MAP_KEY = new KeyMapping("key.triniworldpreview.world_map", InputConstants.Type.KEYBOARD,
            WorldPreview.terrainModWithMapLoaded() ? InputConstants.UNKNOWN.getValue() : InputConstants.KEY_N, CATEGORY);

    @Override
    public void onInitializeClient() {
        KeyMappingHelper.registerKeyMapping(WORLD_MAP_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(WorldPreviewClient::onClientTick);
        if (!WorldPreview.terrainModWithMapLoaded()) {
            ScreenEvents.AFTER_INIT.register(WorldPreviewClient::onScreenInit);
        }
    }

    private static void onClientTick(Minecraft minecraft) {
        while (WORLD_MAP_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null && minecraft.player != null) {
                minecraft.gui.setScreen(new WorldMapScreen(null));
            }
        }
    }

    /** A World map button in the bottom left corner of the pause menu (where Landmass and Trini's Terrain put theirs). */
    private static void onScreenInit(Minecraft minecraft, Screen screen, int width, int height) {
        if (screen instanceof PauseScreen pause) {
            Screens.getWidgets(pause).add(Button.builder(Component.translatable("triniworldpreview.world_map.button"),
                    button -> Minecraft.getInstance().gui.setScreen(new WorldMapScreen(pause)))
                    .bounds(4, height - 24, 90, 20).build());
        }
    }

    /** Whether the World Preview button can preview this world: not for debug worlds, nor world types with their own preview. */
    public static boolean canPreview(WorldCreationUiState state) {
        if (state.isDebug()) {
            return false;
        }
        WorldCreationUiState.WorldTypeEntry type = state.getWorldType();
        return type == null || type.preset() == null || type.preset().unwrapKey()
                .map(key -> !OWN_PREVIEW_NAMESPACES.contains(key.identifier().getNamespace()))
                .orElse(true);
    }
}
