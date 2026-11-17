package io.github.trini3.acclimatization.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.acclimatization.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Samples its input as if the world were stretched sideways: with a scale of 2, everything the input
 * makes, such as patches of humidity, comes out twice as wide and twice as long. Height is unchanged.
 * Used to resize humidity zones, and works whatever the humidity function is (vanilla's or a pack's).
 */
public record ScaleXZ(DensityFunction input, double scale) implements DensityFunction {
    public static final MapCodec<ScaleXZ> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(ScaleXZ::input),
            // Wider than the settings allow, since island biome sizes multiply with them.
            Codec.doubleRange(1.0E-4, 1.0E7).fieldOf("scale").forGetter(ScaleXZ::scale)
    ).apply(instance, ScaleXZ::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler input = this.input.compileSampler(context);
        double scale = this.scale;
        return (Densities.PointSampler) (samplerContext, x, y, z) ->
                input.sampleValue(samplerContext, (int) Math.floor(x / scale), y, (int) Math.floor(z / scale));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction input = rule.rewrite(this.input);
        return input == this.input ? this : new ScaleXZ(input, this.scale);
    }

    @Override
    public Interval range() {
        return this.input.range();
    }

    @Override
    public int domainAxes() {
        return Densities.XZ | this.input.domainAxes();
    }

    @Override
    public MapCodec<ScaleXZ> codec() {
        return DATA_CODEC;
    }
}
