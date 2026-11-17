package io.github.trini3.landmass.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Steers erosion or weirdness near the shore toward what the kind of shore there asks for (see {@link Shores}), so Minecraft,
 * and biome mods, pick one kind of coast biome and coast terrain along a whole stretch. It takes the place of the erosion or
 * weirdness noise everywhere it's sampled, terrain included, and leaves it alone away from the coast. Weirdness near 0
 * (river valleys) is kept, so rivers still reach the sea.
 */
public final class ShoreShaping implements DensityFunction {
    public static final MapCodec<ShoreShaping> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(function -> function.input),
            DensityFunction.CODEC.fieldOf("continentalness").forGetter(function -> function.continentalness),
            Codec.BOOL.fieldOf("ridges").forGetter(function -> function.ridges),
            ContinentLayout.CODEC.fieldOf("layout").forGetter(function -> function.layout),
            Codec.LONG.fieldOf("seed").forGetter(function -> function.seed),
            Codec.doubleRange(0.1, 1_000.0).optionalFieldOf("band_scale", 1.0).forGetter(function -> function.bandScale)
    ).apply(instance, ShoreShaping::new));

    // Continentalness band where shores are steered: fully between SEA and LAND, fading out over FADE beyond them.
    private static final double SEA = -0.3;
    private static final double LAND = -0.05;
    private static final double FADE = 0.15;

    private final DensityFunction input;
    private final DensityFunction continentalness;
    private final boolean ridges;
    private final ContinentLayout layout;
    private final long seed;
    private final double bandScale;
    private final ContinentShapes shapes;

    public ShoreShaping(DensityFunction input, DensityFunction continentalness, boolean ridges, ContinentLayout layout, long seed, double bandScale) {
        this(input, continentalness, ridges, layout, seed, bandScale, new ContinentShapes(layout, seed, bandScale));
    }

    ShoreShaping(DensityFunction input, DensityFunction continentalness, boolean ridges, ContinentLayout layout, long seed, double bandScale,
                 ContinentShapes shapes) {
        this.input = input;
        this.continentalness = continentalness;
        this.ridges = ridges;
        this.layout = layout;
        this.seed = seed;
        this.bandScale = bandScale;
        this.shapes = shapes;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler input = this.input.compileSampler(context);
        DensitySampler continentalness = this.continentalness.compileSampler(context);
        return (Densities.PointSampler) (samplerContext, x, y, z) ->
                (float) this.shape(x, z, input.sampleValue(samplerContext, x, y, z), continentalness.sampleValue(samplerContext, x, y, z));
    }

    private double shape(int x, int z, double value, double c) {
        double near = c < SEA ? 1.0 - smoothStep((SEA - c) / FADE) : c > LAND ? 1.0 - smoothStep((c - LAND) / FADE) : 1.0;
        if (near <= 0.0) {
            return value;
        }
        if (this.ridges) {
            // River valleys keep their weirdness.
            near *= smoothStep((Math.abs(value) - 0.05) / 0.1);
            if (near <= 0.0) {
                return value;
            }
        }
        double target = this.shapes.shoreTarget(this.ridges, x, z, value);
        return value + near * (target - value);
    }

    private static double smoothStep(double t) {
        double c = t < 0.0 ? 0.0 : Math.min(t, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction input = rule.rewrite(this.input);
        DensityFunction continentalness = rule.rewrite(this.continentalness);
        return input == this.input && continentalness == this.continentalness ? this
                : new ShoreShaping(input, continentalness, this.ridges, this.layout, this.seed, this.bandScale, this.shapes);
    }

    @Override
    public Interval range() {
        return Densities.range(Math.min(-1.0, this.input.range().min()), Math.max(1.0, this.input.range().max()));
    }

    @Override
    public int domainAxes() {
        return Densities.XZ | this.input.domainAxes() | this.continentalness.domainAxes();
    }

    @Override
    public MapCodec<ShoreShaping> codec() {
        return DATA_CODEC;
    }
}
