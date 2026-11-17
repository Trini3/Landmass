package io.github.trini3.acclimatization.mixin.client;

import io.github.trini3.acclimatization.client.AcclimatizationClient;
import io.github.trini3.acclimatization.client.ClimateScreen;
import io.github.trini3.acclimatization.client.CustomClimateState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the Custom Climate switch and the Climate Settings button under everything else on the Create World screen's World
 * tab. Its grid uses rows 0 to 2 (and Trini's World Preview row 3), so these go in row 4, one per column.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class WorldTabMixin extends GridLayoutTab {
    private WorldTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void acclimatization$addClimateButtons(CreateWorldScreen screen, CallbackInfo ci) {
        CustomClimateState state = CustomClimateState.get(screen);
        Tooltip toggleHint = Tooltip.create(Component.translatable("acclimatization.world.toggle.hint"));
        Tooltip ownClimate = Tooltip.create(Component.translatable("acclimatization.world.own_climate"));
        Button settings = Button.builder(Component.translatable("acclimatization.world.settings"),
                b -> Minecraft.getInstance().gui.setScreen(new ClimateScreen(screen, state))).width(150).build();
        Button toggle = Button.builder(message(state.enabled()), b -> {
            state.setEnabled(!state.enabled());
            b.setMessage(message(state.enabled()));
            settings.active = state.enabled() && AcclimatizationClient.usable(screen.getUiState());
        }).width(150).build();
        settings.setTooltip(Tooltip.create(Component.translatable("acclimatization.world.settings.hint")));
        this.layout.addChild(toggle, 4, 0);
        this.layout.addChild(settings, 4, 1);
        screen.getUiState().addListener(ui -> {
            boolean usable = AcclimatizationClient.usable(ui);
            toggle.active = usable;
            toggle.setTooltip(usable ? toggleHint : ownClimate);
            settings.active = usable && state.enabled();
        });
    }

    private static Component message(boolean on) {
        return Component.translatable("acclimatization.world.toggle", on ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
    }
}
