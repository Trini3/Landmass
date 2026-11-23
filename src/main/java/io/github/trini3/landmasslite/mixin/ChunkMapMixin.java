package io.github.trini3.landmasslite.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.trini3.landmasslite.climate.ClimateHooks;
import io.github.trini3.landmasslite.world.LandmassGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Each dimension creates its RandomState in the ChunkMap constructor. Wrapping that call (rather than
 * redirecting it) chains cleanly with other mods that hook the same spot.
 *
 * <p>On Minecraft 26.x the generator compiles its terrain from its own settings rather than from the RandomState, so a
 * Landmass generator is also handed the changed settings (it keeps the original ones for saving the world).
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
    private RandomState landmass$applyClimateBands(HolderGetter<NormalNoise> noises, long seed, NoiseGeneratorSettings settings,
                                                         Operation<RandomState> original, @Local(argsOnly = true) ServerLevel level,
                                                         @Local(argsOnly = true) ChunkGenerator generator) {
        boolean outer = ClimateHooks.beginWorldSetup();
        try {
            NoiseGeneratorSettings banded = ClimateHooks.apply(level, settings, noises, seed);
            if (banded != settings && generator instanceof LandmassGenerator terrain) {
                terrain.useSettings(Holder.direct(banded));
            }
            return original.call(noises, seed, banded);
        } finally {
            ClimateHooks.endWorldSetup(outer);
        }
    }
}
