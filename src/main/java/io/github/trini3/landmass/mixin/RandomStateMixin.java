package io.github.trini3.landmass.mixin;

import io.github.trini3.landmass.climate.ClimateHooks;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mods that preview a world's biomes before it exists, or draw maps of it, build their own RandomState
 * instead of using the world's. This gives those the climate bands and continents too, so previews match the world.
 * The world's own RandomState is left to {@link ChunkMapMixin}, which knows the dimension.
 */
@Mixin(RandomState.class)
public abstract class RandomStateMixin {
    @Inject(
            method = "create(Lnet/minecraft/core/HolderGetter;JLnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;)Lnet/minecraft/world/level/levelgen/RandomState;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void landmass$applyClimateBandsToPreviews(HolderGetter<NormalNoise> noises, long seed, NoiseGeneratorSettings settings,
                                                                    CallbackInfoReturnable<RandomState> cir) {
        NoiseGeneratorSettings banded = ClimateHooks.applyOutsideWorlds(settings, noises, seed);
        if (banded != settings) {
            // Comes back through here with the banded settings, which are then left alone.
            cir.setReturnValue(RandomState.create(noises, seed, banded));
        }
    }
}
