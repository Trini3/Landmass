package io.github.trini3.landmasslite.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmasslite.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/** How much a position counts as island rather than continent: 0 on continents, 1 out at sea (see {@link ContinentShapes#islandness}). */
public final class Islandness implements DensityFunction {
    public static final MapCodec<Islandness> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ContinentLayout.CODEC.fieldOf("layout").forGetter(function -> function.layout),
            Codec.LONG.fieldOf("seed").forGetter(function -> function.seed),
            Codec.doubleRange(0.01, 1000.0).optionalFieldOf("band_scale", 1.0).forGetter(function -> function.bandScale)
    ).apply(instance, Islandness::new));

    private final ContinentLayout layout;
    private final long seed;
    private final double bandScale;
    private final ContinentShapes shapes;

    public Islandness(ContinentLayout layout, long seed, double bandScale) {
        this(layout, seed, bandScale, new ContinentShapes(layout, seed, bandScale));
    }

    Islandness(ContinentLayout layout, long seed, double bandScale, ContinentShapes shapes) {
        this.layout = layout;
        this.seed = seed;
        this.bandScale = bandScale;
        this.shapes = shapes;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        ContinentShapes shapes = this.shapes;
        return (Densities.PointSampler) (samplerContext, x, y, z) -> (float) shapes.islandness(x, z);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    @Override
    public Interval range() {
        return Interval.of(0.0F, 1.0F);
    }

    @Override
    public int domainAxes() {
        return Densities.XZ;
    }

    @Override
    public MapCodec<Islandness> codec() {
        return DATA_CODEC;
    }
}
