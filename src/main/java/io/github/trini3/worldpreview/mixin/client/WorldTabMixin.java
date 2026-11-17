package io.github.trini3.worldpreview.mixin.client;

import io.github.trini3.worldpreview.client.CreatePreviewScreen;
import io.github.trini3.worldpreview.client.WorldPreviewClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a World Preview button under everything else on the Create World screen's World tab. Its grid has the world type and
 * Customize buttons, the seed box and the switches in rows 0 to 2, so the button goes in row 3 across both columns.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin extends GridLayoutTab {
    private WorldTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void triniworldpreview$addPreviewButton(CreateWorldScreen screen, CallbackInfo ci) {
        Button button = Button.builder(Component.translatable("triniworldpreview.preview.button"),
                b -> Minecraft.getInstance().gui.setScreen(new CreatePreviewScreen(screen))).width(308).build();
        this.layout.addChild(button, 3, 0, 1, 2);
        Tooltip ownPreview = Tooltip.create(Component.translatable("triniworldpreview.preview.button.own_preview"));
        screen.getUiState().addListener(state -> {
            button.active = WorldPreviewClient.canPreview(state);
            button.setTooltip(button.active ? null : ownPreview);
        });
    }
}
