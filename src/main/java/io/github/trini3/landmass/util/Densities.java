package io.github.trini3.landmass.util;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

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
        DfRewriteRule[] rule = new DfRewriteRule[1];
        rule[0] = inner -> {
            if (!found[0]) {
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
        return new NoiseRouter(
                rewriteAll(router.temperature(), rule),
                rewriteAll(router.vegetation(), rule),
                rewriteAll(router.continents(), rule),
                rewriteAll(router.erosion(), rule),
                rewriteAll(router.depth(), rule),
                rewriteAll(router.ridges(), rule),
                rewriteAll(router.chunkSurfaceLevel(), rule),
                rewriteAll(router.finalDensity(), rule));
    }

    /**
     * Rewrites every function inside {@code function} (references included, which are inlined only where something
     * inside them changed), bottom up: children first, then the function itself.
     */
    public static DensityFunction rewriteAll(DensityFunction function, DfRewriteRule rule) {
        DfRewriteRule[] walk = new DfRewriteRule[1];
        walk[0] = inner -> rule.rewrite(inner.rewriteChildren(walk[0]));
        return walk[0].rewrite(function);
    }
}
