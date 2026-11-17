package io.github.trini3.worldpreview.mixin.client;

import io.github.trini3.worldpreview.ChosenSpawn;
import io.github.trini3.worldpreview.client.PendingSpawn;
import io.github.trini3.worldpreview.client.WorldPreviewClient;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands the spawn picked on the preview map (or none) to the world about to be created. */
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin {
    @Inject(method = "onCreate", at = @At("HEAD"))
    private void triniworldpreview$armChosenSpawn(CallbackInfo ci) {
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
        ChosenSpawn.arm(WorldPreviewClient.canPreview(screen.getUiState()) ? PendingSpawn.get(screen).orElse(null) : null);
    }
}
