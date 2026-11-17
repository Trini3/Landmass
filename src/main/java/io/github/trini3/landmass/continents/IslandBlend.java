package io.github.trini3.landmass.continents;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * One function on continents and another on islands: {@code continental} where {@code islandness} is 0,
 * {@code island} where it's 1, and a mix of the two in between (over the continental shelves, under water).
 */
public record IslandBlend(DensityFunction continental, DensityFunction island, DensityFunction islandness) implements DensityFunction {
    public static final MapCodec<IslandBlend> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("continental").forGetter(IslandBlend::continental),
            DensityFunction.CODEC.fieldOf("island").forGetter(IslandBlend::island),
            DensityFunction.CODEC.fieldOf("islandness").forGetter(IslandBlend::islandness)
    ).apply(instance, IslandBlend::new));

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler continentalSampler = this.continental.compileSampler(context);
        DensitySampler islandSampler = this.island.compileSampler(context);
        DensitySampler islandnessSampler = this.islandness.compileSampler(context);
        return (Densities.PointSampler) (samplerContext, x, y, z) -> {
            float share = islandnessSampler.sampleValue(samplerContext, x, y, z);
            if (share <= 0.0F) {
                return continentalSampler.sampleValue(samplerContext, x, y, z);
            }
            if (share >= 1.0F) {
                return islandSampler.sampleValue(samplerContext, x, y, z);
            }
            float continental = continentalSampler.sampleValue(samplerContext, x, y, z);
            return continental + (islandSampler.sampleValue(samplerContext, x, y, z) - continental) * share;
        };
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction continental = rule.rewrite(this.continental);
        DensityFunction island = rule.rewrite(this.island);
        DensityFunction islandness = rule.rewrite(this.islandness);
        return continental == this.continental && island == this.island && islandness == this.islandness ? this
                : new IslandBlend(continental, island, islandness);
    }

    @Override
    public Interval range() {
        return Interval.encapsulating(this.continental.range(), this.island.range());
    }

    @Override
    public int domainAxes() {
        return this.continental.domainAxes() | this.island.domainAxes() | this.islandness.domainAxes();
    }

    @Override
    public MapCodec<IslandBlend> codec() {
        return DATA_CODEC;
    }
}
