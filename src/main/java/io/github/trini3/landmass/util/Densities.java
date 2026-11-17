package io.github.trini3.landmass.util;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Predicate;

/** Small helpers for the mod's density functions on Minecraft 26.x, where density functions compile into samplers. */
public final class Densities {
    /** The mod's functions depend on the horizontal position (plus whatever their inputs depend on). */
    public static final int XZ = DensityFunction.AXIS_X | DensityFunction.AXIS_Z;

    private Densities() {
    }

    /** A sampler that works one position at a time; whole volumes are filled point by point. */
    public interface PointSampler extends DensitySampler {
        @Override
        default void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
        }
    }

    /** An interval from two values in either order. */
    public static Interval range(double a, double b) {
        return Interval.of((float) Math.min(a, b), (float) Math.max(a, b));
    }

    /** Whether the function, or any function inside it (references included), matches. */
    public static boolean contains(DensityFunction function, Predicate<DensityFunction> test) {
        boolean[] found = {false};
        // Terrain packs such as Lithosphere reuse the same functions many times over, so each one is only looked at once.
        Map<DensityFunction, Boolean> seen = new IdentityHashMap<>();
        DfRewriteRule[] rule = new DfRewriteRule[1];
        rule[0] = inner -> {
            if (!found[0] && seen.put(inner, Boolean.TRUE) == null) {
                if (test.test(inner)) {
                    found[0] = true;
                } else {
                    inner.rewriteChildren(rule[0]);
                }
            }
            return inner;
        };
        rule[0].rewrite(function);
        return found[0];
    }

    /** The router with {@link #rewriteAll} applied to each of its functions. */
    public static NoiseRouter rewriteAll(NoiseRouter router, DfRewriteRule rule) {
        // One walk for the whole router, so a function the router uses in several places is rewritten once and stays shared.
        DfRewriteRule walk = walker(rule);
        return new NoiseRouter(
                walk.rewrite(router.temperature()),
                walk.rewrite(router.vegetation()),
                walk.rewrite(router.continents()),
                walk.rewrite(router.erosion()),
                walk.rewrite(router.depth()),
                walk.rewrite(router.ridges()),
                walk.rewrite(router.chunkSurfaceLevel()),
                walk.rewrite(router.finalDensity()));
    }

    /**
     * Rewrites every function inside {@code function} (references included, which are inlined only where something
     * inside them changed), bottom up: children first, then the function itself.
     */
    public static DensityFunction rewriteAll(DensityFunction function, DfRewriteRule rule) {
        return walker(rule).rewrite(function);
    }

    /**
     * A rule that applies {@code rule} bottom up through a whole tree. Each function is rewritten once and its result reused
     * wherever it appears again: terrain packs such as Lithosphere use the same functions dozens of times, and rewriting every
     * use separately both takes exponentially long and breaks the sharing their caches rely on (each copy would compute again).
     */
    private static DfRewriteRule walker(DfRewriteRule rule) {
        Map<DensityFunction, DensityFunction> done = new IdentityHashMap<>();
        DfRewriteRule[] walk = new DfRewriteRule[1];
        walk[0] = inner -> {
            DensityFunction known = done.get(inner);
            if (known != null) {
                return known;
            }
            DensityFunction result = rule.rewrite(inner.rewriteChildren(walk[0]));
            done.put(inner, result);
            return result;
        };
        return walk[0];
    }
}
