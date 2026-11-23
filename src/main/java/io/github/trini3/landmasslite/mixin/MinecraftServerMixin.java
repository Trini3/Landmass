package io.github.trini3.landmasslite.mixin;

import io.github.trini3.landmasslite.world.ChosenSpawn;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.LevelLoadListener;
import net.minecraft.world.level.storage.ServerLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Puts a new world's spawn near the point picked on the Customize screen's preview map, when one was picked. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "setInitialSpawn", at = @At("HEAD"), cancellable = true)
    private static void landmass$useChosenSpawn(ServerLevel level, ServerLevelData levelData, boolean spawnBonusChest, boolean isDebug,
                                                       LevelLoadListener levelLoadListener, CallbackInfo ci) {
        if (!isDebug && ChosenSpawn.apply(level, levelData)) {
            ci.cancel();
        }
    }
}
