package io.github.trini3.landmasslite.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmasslite.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Continentalness from the world's continents and islands (see {@link ContinentShapes}). It takes the place of the
 * noise the world would have sampled for continentalness, and keeps that noise as {@code input}: inland, it picks
 * between lowlands and highlands, and far out at sea it still makes the occasional mushroom island.
 */
public final class ContinentsFunction implements DensityFunction {
    public static final MapCodec<ContinentsFunction> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(function -> function.input),
            ContinentLayout.CODEC.fieldOf("layout").forGetter(function -> function.layout),
            Codec.LONG.fieldOf("seed").forGetter(function -> function.seed),
            Codec.doubleRange(0.1, 1_000.0).optionalFieldOf("band_scale", 1.0).forGetter(function -> function.bandScale)
    ).apply(instance, ContinentsFunction::new));

    private final DensityFunction input;
    private final ContinentLayout layout;
    private final long seed;
    private final double bandScale;
    private final ContinentShapes shapes;

    public ContinentsFunction(DensityFunction input, ContinentLayout layout, long seed, double bandScale) {
        this(input, layout, seed, bandScale, new ContinentShapes(layout, seed, bandScale));
    }

    ContinentsFunction(DensityFunction input, ContinentLayout layout, long seed, double bandScale, ContinentShapes shapes) {
        this.input = input;
        this.layout = layout;
        this.seed = seed;
        this.bandScale = bandScale;
        this.shapes = shapes;
    }

    public DensityFunction input() {
        return this.input;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler input = this.input.compileSampler(context);
        ContinentShapes shapes = this.shapes;
        return (Densities.PointSampler) (samplerContext, x, y, z) -> (float) shapes.continentalness(x, z, input.sampleValue(samplerContext, x, y, z));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction input = rule.rewrite(this.input);
        return input == this.input ? this : new ContinentsFunction(input, this.layout, this.seed, this.bandScale, this.shapes);
    }

    @Override
    public Interval range() {
        return Densities.range(Math.min(-0.7, this.input.range().min()), 1.0);
    }

    @Override
    public int domainAxes() {
        return Densities.XZ | this.input.domainAxes();
    }

    @Override
    public MapCodec<ContinentsFunction> codec() {
        return DATA_CODEC;
    }

    /** Whether a density function has continents anywhere inside it. */
    public static boolean isUsedBy(DensityFunction function) {
        return Densities.contains(function, inner -> inner instanceof ContinentsFunction);
    }
}
