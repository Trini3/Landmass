package io.github.trini3.landmass.mixin;

import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The router a RandomState samples climate from, to check whether the climate bands are in it. */
@Mixin(RandomState.class)
public interface RandomStateAccessor {
    @Accessor("router")
    NoiseRouter landmass$router();
}
