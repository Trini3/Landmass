package io.github.trini3.landmasslite.climate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import io.github.trini3.landmasslite.util.Densities;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.generator.NoiseFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.Collection;
import java.util.List;

/**
 * Resizes biomes. Since 1.18 biomes follow the land: oceans, coasts, inland areas and mountain ranges each
 * get their own biomes, picked from the same continentalness and erosion values that shape the terrain.
 * Stretching only the values used to pick biomes would put ocean biomes on land and mountain biomes on
 * plains, so this stretches the continentalness and erosion noises sideways everywhere the noise router
 * uses them, for terrain and biomes alike. That's what Minecraft's Large Biomes world type does, 4 times
 * over. Weirdness is left alone, as in Large Biomes, so rivers keep their usual spacing and width.
 *
 * <p>It works on the noises rather than on named density functions, so packs that rebuild the terrain from
 * Minecraft's continentalness and erosion noises are resized consistently too.
 */
public final class BiomeScale implements DfRewriteRule {
    private static final List<ResourceKey<NormalNoise>> NOISES = List.of(
            ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath("minecraft", "continentalness")),
            ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath("minecraft", "erosion")));
    private final double continentalnessScale;
    private final double erosionScale;

    private BiomeScale(double continentalnessScale, double erosionScale) {
        this.continentalnessScale = continentalnessScale;
        this.erosionScale = erosionScale;
    }

    /** The router with its continentalness and erosion noises stretched sideways by {@code scale}. */
    public static NoiseRouter apply(NoiseRouter router, double scale) {
        return apply(router, scale, scale);
    }

    /**
     * The router with its continentalness noise (coasts and the bands inland from them) stretched by {@code continentalnessScale}
     * and its erosion noise (mountain ranges, hills and flat areas) by {@code erosionScale}.
     */
    public static NoiseRouter apply(NoiseRouter router, double continentalnessScale, double erosionScale) {
        return continentalnessScale == 1.0 && erosionScale == 1.0 ? router : Densities.rewriteAll(router, new BiomeScale(continentalnessScale, erosionScale));
    }

    @Override
    public DensityFunction rewrite(DensityFunction function) {
        // Called for every function in the router, innermost first, so each noise is wrapped once, right where it's sampled.
        if (this.continentalnessScale != 1.0 && samplesNoise(function, NOISES.subList(0, 1))) {
            return new ScaleXZ(function, this.continentalnessScale);
        }
        if (this.erosionScale != 1.0 && samplesNoise(function, NOISES.subList(1, 2))) {
            return new ScaleXZ(function, this.erosionScale);
        }
        return function;
    }

    /**
     * Whether a function is a noise function sampling one of these noises.
     */
    public static boolean samplesNoise(DensityFunction function, Collection<ResourceKey<NormalNoise>> noises) {
        return function instanceof NoiseFunction noise && noises.stream().anyMatch(key -> noise.noise().is(key));
    }
}
