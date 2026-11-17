package io.github.trini3.landmass.util;

import io.github.trini3.landmass.mixin.RandomStateAccessor;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/** Climate sampling on Minecraft 26.x, where samplers need a context and biome sources a resolver. */
public final class Climates {
    private Climates() {
    }

    /** A climate sampler for one thread (its caches aren't shared). */
    public static Climate.Sampler sampler(RandomState randomState) {
        return randomState.createClimateSampler(SamplerContext.builder().enableCaches().build());
    }

    public static NoiseRouter router(RandomState randomState) {
        return ((RandomStateAccessor) (Object) randomState).landmass$router();
    }

    public static Holder<Biome> biome(BiomeSource biomeSource, Climate.Sampler sampler, int quartX, int quartY, int quartZ) {
        return biomeSource.createResolver(sampler).getNoiseBiome(quartX, quartY, quartZ);
    }
}
