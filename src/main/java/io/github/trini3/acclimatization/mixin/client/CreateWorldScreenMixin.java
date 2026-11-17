package io.github.trini3.acclimatization.mixin.client;

import io.github.trini3.acclimatization.client.AcclimatizationClient;
import io.github.trini3.acclimatization.client.CustomClimateState;
import io.github.trini3.acclimatization.climate.ClimateSettings;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the Custom Climate settings (or none) to the world about to be created, by its folder name. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void acclimatization$armClimate(CallbackInfo ci) {
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
        CustomClimateState state = CustomClimateState.get(screen);
        boolean on = state.enabled() && AcclimatizationClient.usable(screen.getUiState());
        ClimateSettings.arm(on ? new ClimateSettings.Pending(screen.getUiState().getTargetFolder(), state.settings()) : null);
    }
}
