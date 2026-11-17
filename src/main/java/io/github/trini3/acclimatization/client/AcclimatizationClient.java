package io.github.trini3.acclimatization.client;

import io.github.trini3.acclimatization.climate.ClimateHooks;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.jetbrains.annotations.Nullable;

// The client entrypoint: it never loads on dedicated servers, so client code is safe here.
public class AcclimatizationClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClimateHooks.setCreateWorldSelection(AcclimatizationClient::createWorldSelection);
    }

    /** Whether Custom Climate can be used with the world type picked on this Create World screen. */
    public static boolean usable(WorldCreationUiState state) {
        return !ClimateHooks.hasOwnClimate(state.getSettings().selectedDimensions().overworld());
    }

    /**
     * The climate and overworld terrain of the world being set up, when its Custom Climate switch is on. Asked while no world
     * runs, so the latest Create World screen is used even while a screen opened from it (a world preview) is showing.
     */
    @Nullable
    private static ClimateHooks.Selection createWorldSelection() {
        CreateWorldScreen screen = CustomClimateState.screen();
        if (screen != null) {
            CustomClimateState state = CustomClimateState.get(screen);
            ChunkGenerator generator = screen.getUiState().getSettings().selectedDimensions().overworld();
            if (state.enabled() && usable(screen.getUiState()) && generator instanceof NoiseBasedChunkGenerator noise) {
                return new ClimateHooks.Selection(state.settings(), noise.generatorSettings().value());
            }
        }
        return null;
    }
}
