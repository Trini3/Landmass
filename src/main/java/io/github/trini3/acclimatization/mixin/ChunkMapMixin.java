package io.github.trini3.acclimatization.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.trini3.acclimatization.climate.ClimateHooks;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Each dimension creates its RandomState in the ChunkMap constructor. Wrapping that call (rather than redirecting it)
 * chains cleanly with other mods that hook the same spot.
 */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @WrapOperation(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/RandomState;create(Lnet/minecraft/core/HolderGetter;JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)Lnet/minecraft/world/level/levelgen/RandomState;"
            )
    )
    private RandomState acclimatization$applyClimate(HolderGetter<NormalNoise> noises, long seed, NoiseGeneratorSettings settings,
                                                     Operation<RandomState> original, @Local(argsOnly = true) ServerLevel level) {
        boolean outer = ClimateHooks.beginWorldSetup();
        try {
            return original.call(noises, seed, ClimateHooks.apply(level, settings, noises, seed));
        } finally {
            ClimateHooks.endWorldSetup(outer);
        }
    }
}
