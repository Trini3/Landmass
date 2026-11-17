package io.github.trini3.worldpreview.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.core.BlockPos;

import java.lang.ref.WeakReference;
import java.util.Optional;

/**
 * The spawn point picked on the preview map, kept for the Create World screen it was picked on, so that backing out and
 * starting another new world forgets it.
 */
public final class PendingSpawn {
    private static WeakReference<CreateWorldScreen> owner = new WeakReference<>(null);
    private static Optional<BlockPos> spawn = Optional.empty();

    private PendingSpawn() {
    }

    public static Optional<BlockPos> get(CreateWorldScreen screen) {
        return owner.get() == screen ? spawn : Optional.empty();
    }

    public static void set(CreateWorldScreen screen, Optional<BlockPos> picked) {
        owner = new WeakReference<>(screen);
        spawn = picked;
    }
}
