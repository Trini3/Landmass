package io.github.trini3.landmass.climate;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.Densities;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.Optional;

/**
 * The temperature density function: latitude decides the band, noise roughens the borders.
 *
 * <p>{@code input} is whatever temperature function the world had before (vanilla's, or one from a
 * terrain or biome pack). It's kept and used as the small-scale "mixing" noise rather than thrown away.
 * {@code humidity} is the world's humidity (the router's vegetation), which mixed tropics need.
 *
 * <p>Datapacks can use this directly as {@code "type": "landmass:latitude_temperature"} in
 * a noise router; the mod then leaves that dimension's temperature alone.
 */
public record LatitudeTemperature(DensityFunction input, Optional<DensityFunction> humidity, Holder<NormalNoise> wanderNoise,
                                  LatitudeLayout layout, Optional<DeviationZones> deviation) implements DensityFunction {
    public static final MapCodec<LatitudeTemperature> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("input").forGetter(LatitudeTemperature::input),
            DensityFunction.CODEC.optionalFieldOf("humidity").forGetter(LatitudeTemperature::humidity),
            NormalNoise.CODEC.fieldOf("wander_noise").forGetter(LatitudeTemperature::wanderNoise),
            LatitudeLayout.MAP_CODEC.forGetter(LatitudeTemperature::layout),
            // From 0.15.0: climate deviation patches, placed from deviation_seed.
            ClimateDeviation.CODEC.optionalFieldOf("deviation").forGetter(function -> function.deviation().map(DeviationZones::settings)),
            com.mojang.serialization.Codec.LONG.optionalFieldOf("deviation_seed", 0L).forGetter(function -> function.deviation().map(DeviationZones::seed).orElse(0L))
    ).apply(instance, (input, humidity, wander, layout, deviation, seed) -> new LatitudeTemperature(input, humidity, wander, layout,
            deviation.filter(ClimateDeviation::enabled).map(settings -> new DeviationZones(settings, seed)))));

    public LatitudeTemperature(DensityFunction input, Optional<DensityFunction> humidity, Holder<NormalNoise> wanderNoise, LatitudeLayout layout) {
        this(input, humidity, wanderNoise, layout, Optional.empty());
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler input = this.input.compileSampler(context);
        // The noise gets the world seed here.
        Noise wanderNoise = context.createNoiseSampler(this.wanderNoise);
        DensitySampler humidity = this.humidity.isPresent() && this.layout.usesHumidity() ? this.humidity.get().compileSampler(context) : null;
        LatitudeLayout layout = this.layout;
        DeviationZones deviation = this.deviation.orElse(null);
        return (Densities.PointSampler) (samplerContext, x, y, z) -> {
            double mixing = Mth.clamp(input.sampleValue(samplerContext, x, y, z), -1.0, 1.0);
            double wander = Mth.clamp(wanderNoise.get(x, 0.0, z), -1.0, 1.0);
            double wet = humidity != null ? Mth.clamp(humidity.sampleValue(samplerContext, x, y, z), -1.0, 1.0) : 0.0;
            double meander = 0.0;
            if (layout.meanders()) {
                // The same noise far more stretched, and offset so its swings don't line up with the border wander.
                double k = layout.meanderFrequency();
                meander = Mth.clamp(1.6 * wanderNoise.get(x * k + 7_919.5, 0.0, z * k - 4_271.25), -1.0, 1.0);
            }
            double zoneShift = deviation != null ? deviation.temperatureShift(x, z) : 0.0;
            return (float) layout.temperature(x, z, wander, meander, mixing, wet, zoneShift);
        };
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction input = rule.rewrite(this.input);
        Optional<DensityFunction> humidity = this.humidity.map(rule::rewrite);
        boolean same = input == this.input && (this.humidity.isEmpty() || humidity.get() == this.humidity.get());
        return same ? this : new LatitudeTemperature(input, humidity, this.wanderNoise, this.layout, this.deviation);
    }

    @Override
    public Interval range() {
        return Densities.range(this.layout.minTemperature(), this.layout.maxTemperature());
    }

    @Override
    public int domainAxes() {
        return Densities.XZ;
    }

    @Override
    public MapCodec<LatitudeTemperature> codec() {
        return DATA_CODEC;
    }

    /** Whether a density function already contains a latitude temperature anywhere inside it. */
    public static boolean isUsedBy(DensityFunction function) {
        return Densities.contains(function, inner -> inner instanceof LatitudeTemperature);
    }
}
