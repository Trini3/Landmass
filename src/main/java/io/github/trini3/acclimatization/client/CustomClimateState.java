package io.github.trini3.acclimatization.client;

import io.github.trini3.acclimatization.climate.ClimateSettings;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.lang.ref.WeakReference;

/**
 * The Custom Climate switch and settings of one Create World screen. Backing out and starting another new world starts
 * again from off and the defaults.
 */
public final class CustomClimateState {
    private static WeakReference<CreateWorldScreen> owner = new WeakReference<>(null);
    private static CustomClimateState current = new CustomClimateState();

    private boolean enabled;
    private ClimateSettings settings = ClimateSettings.DEFAULT;

    private CustomClimateState() {
    }

    public static CustomClimateState get(CreateWorldScreen screen) {
        if (owner.get() != screen) {
            owner = new WeakReference<>(screen);
            current = new CustomClimateState();
        }
        return current;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ClimateSettings settings() {
        return this.settings;
    }

    public void setSettings(ClimateSettings settings) {
        this.settings = settings;
    }
}
