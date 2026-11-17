package io.github.trini3.landmass.continents;

import io.github.trini3.landmass.climate.BiomeScale;
import io.github.trini3.landmass.climate.ScaleXZ;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import io.github.trini3.landmass.util.Densities;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;

/**
 * Puts a world's continents into its noise router. Wherever the router samples Minecraft's continentalness noise,
 * for terrain shape or for biome choice, it gets {@link ContinentsFunction} instead, which wraps that noise. So land,
 * coasts and oceans move together with the biomes that follow them, and terrain packs that build on the
 * continentalness noise (rather than replacing it) keep their own terrain on the new continents.
 *
 * <p>With a different biome size for islands, erosion (which, with humidity and temperature, decides biomes) is
 * also sampled at the island scale out at sea; {@link #islandBlend} does the same for the climate functions.
 *
 * <p>Later terrain features (mountains, coastlines) are meant to be separate steps like this one, so a
 * compatibility mode can leave them out while keeping the continents.
 */
public final class Continents implements DfRewriteRule {
    private static final List<ResourceKey<NormalNoise>> CONTINENTALNESS = List.of(
            ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath("minecraft", "continentalness")));
    private static final List<ResourceKey<NormalNoise>> EROSION = List.of(
            ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath("minecraft", "erosion")));

    private final ContinentLayout layout;
    private final long seed;
    private final double bandScale;
    private final ContinentShapes shapes;
    // Cached per column inside chunks, since the climate functions that blend by it are sampled at every height.
    private final DensityFunction islandness;
    private final boolean landscape;
    private final List<ResourceKey<NormalNoise>> continentalness;

    /**
     * A world's continents.
     *
     * @param bandScale the world's biome size, which widens coasts and inland bands along with the biomes
     */
    public Continents(ContinentLayout layout, long seed, double bandScale) {
        this(layout, seed, bandScale, true);
    }

    /** @param landscape false in compatibility mode: erosion (terrain shape) keeps one size everywhere instead of island scale out at sea */
    public Continents(ContinentLayout layout, long seed, double bandScale, boolean landscape) {
        this(layout, seed, bandScale, landscape, false);
    }

    /** @param lithosphere also replace Lithosphere's own continentalness noise, so its terrain follows these continents */
    public Continents(ContinentLayout layout, long seed, double bandScale, boolean landscape, boolean lithosphere) {
        this.continentalness = lithosphere ? BiomeScale.continentalnessNoises(true) : CONTINENTALNESS;
        this.landscape = landscape;
        this.layout = layout;
        this.seed = seed;
        this.bandScale = bandScale;
        this.shapes = new ContinentShapes(layout, seed, bandScale);
        this.islandness = DensityFunctions.cache(new Islandness(layout, seed, bandScale, this.shapes));
    }

    /** Whether islands get a different biome size from the continents. */
    public boolean splitsIslandBiomes() {
        return this.layout.islandBiomeScale() != 1.0;
    }

    /** {@code continental} on continents and {@code island} out at sea, blended across the continental shelves. */
    public DensityFunction islandBlend(DensityFunction continental, DensityFunction island) {
        return new IslandBlend(continental, island, this.islandness);
    }

    /** The router with these continents in place of its continentalness noise. */
    public NoiseRouter apply(NoiseRouter router) {
        return Densities.rewriteAll(router, this);
    }

    private static final List<ResourceKey<NormalNoise>> RIDGES = List.of(
            ResourceKey.create(Registries.NOISE, Identifier.fromNamespaceAndPath("minecraft", "ridge")));

    /**
     * The router (with these continents already in place) with its erosion and weirdness steered near the shores, wherever
     * they're sampled, so each stretch of coast gets one kind of shore. Unchanged when shores are off.
     */
    public NoiseRouter shapeShores(NoiseRouter router) {
        if (!this.shapes.shapesShores()) {
            return router;
        }
        // The router's own continentalness (cached per column in chunks) tells how near the shore a position is.
        DensityFunction continentalness = router.continents();
        return Densities.rewriteAll(router, function -> {
                if (BiomeScale.samplesNoise(function, EROSION)) {
                    return new ShoreShaping(function, continentalness, false, Continents.this.layout, Continents.this.seed, Continents.this.bandScale,
                            Continents.this.shapes);
                }
                if (BiomeScale.samplesNoise(function, RIDGES)) {
                    return new ShoreShaping(function, continentalness, true, Continents.this.layout, Continents.this.seed, Continents.this.bandScale,
                            Continents.this.shapes);
                }
                return function;
        });
    }

    @Override
    public DensityFunction rewrite(DensityFunction function) {
        if (BiomeScale.samplesNoise(function, this.continentalness)) {
            return new ContinentsFunction(function, this.layout, this.seed, this.bandScale, this.shapes);
        }
        if (this.landscape && this.splitsIslandBiomes() && BiomeScale.samplesNoise(function, EROSION)) {
            return this.islandBlend(function, new ScaleXZ(function, this.layout.islandBiomeScale()));
        }
        return function;
    }
}
