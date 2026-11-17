package io.github.trini3.landmass.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Humidity with the climate deviation patches (see {@link DeviationZones}): inside a patch, humidity moves up or down by whole
 * humidity bands, so a patch of desert can sit in jungle country or a wet pocket in dry land. Outside them it's the input as is.
 */
public final class HumidityDeviation implements DensityFunction {
    public static final MapCodec<HumidityDeviation> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(function -> function.input),
            ClimateDeviation.CODEC.fieldOf("deviation").forGetter(function -> function.zones.settings()),
            Codec.LONG.fieldOf("seed").forGetter(function -> function.zones.seed())
    ).apply(instance, (input, deviation, seed) -> new HumidityDeviation(input, new DeviationZones(deviation, seed))));

    private final DensityFunction input;
    private final DeviationZones zones;

    public HumidityDeviation(DensityFunction input, DeviationZones zones) {
        this.input = input;
        this.zones = zones;
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler input = this.input.compileSampler(context);
        DeviationZones zones = this.zones;
        return (Densities.PointSampler) (samplerContext, x, y, z) -> {
            float humidity = input.sampleValue(samplerContext, x, y, z);
            double bands = zones.humidityShift(x, z);
            return bands == 0.0 ? humidity : (float) DeviationZones.shiftHumidity(humidity, bands);
        };
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction input = rule.rewrite(this.input);
        return input == this.input ? this : new HumidityDeviation(input, this.zones);
    }

    @Override
    public Interval range() {
        return Densities.range(Math.min(-1.0, this.input.range().min()), Math.max(1.0, this.input.range().max()));
    }

    @Override
    public int domainAxes() {
        return Densities.XZ | this.input.domainAxes();
    }

    @Override
    public MapCodec<HumidityDeviation> codec() {
        return DATA_CODEC;
    }
}
