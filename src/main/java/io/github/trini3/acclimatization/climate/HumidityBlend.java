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
 * Humidity made of two sizes of the same humidity noise: big {@code zones} (regions that are wet or dry overall) and smaller
 * {@code local} patches inside them, which decide individual biomes such as forest or plains. Their weighted sum is scaled
 * back up by {@code gain}, so wet and dry extremes stay about as common as in vanilla.
 */
public record HumidityBlend(DensityFunction zones, DensityFunction local, double gain) implements DensityFunction {
    // Regions lead, so a dry zone stays mostly dry; local patches still add their own variety.
    private static final double ZONE_WEIGHT = 0.6;
    private static final double LOCAL_WEIGHT = 0.4;

    public static final MapCodec<HumidityBlend> DATA_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DensityFunction.CODEC.fieldOf("zones").forGetter(HumidityBlend::zones),
            DensityFunction.CODEC.fieldOf("local").forGetter(HumidityBlend::local),
            Codec.doubleRange(0.0, 10.0).fieldOf("gain").forGetter(HumidityBlend::gain)
    ).apply(instance, HumidityBlend::new));

    /**
     * Humidity with zones about {@code zoneScale} times vanilla's size and local patches {@code localScale} times. Zones are
     * never smaller than the patches inside them. With both the same, it's simply the humidity noise at that size.
     */
    public static DensityFunction of(DensityFunction humidity, double zoneScale, double localScale) {
        zoneScale = Math.max(zoneScale, localScale);
        DensityFunction local = localScale == 1.0 ? humidity : new ScaleXZ(humidity, localScale);
        if (zoneScale == localScale) {
            return local;
        }
        // Two far-apart sizes are unrelated, and their weighted sum varies less than either; close sizes vary almost together.
        double apart = Math.min(1.0, Math.log(zoneScale / localScale) / Math.log(8.0));
        double gain = 1.0 + apart * (1.0 / Math.sqrt(ZONE_WEIGHT * ZONE_WEIGHT + LOCAL_WEIGHT * LOCAL_WEIGHT) - 1.0);
        return new HumidityBlend(new ScaleXZ(humidity, zoneScale), local, gain);
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler zones = this.zones.compileSampler(context);
        DensitySampler local = this.local.compileSampler(context);
        double gain = this.gain;
        return (Densities.PointSampler) (samplerContext, x, y, z) ->
                (float) (gain * (ZONE_WEIGHT * zones.sampleValue(samplerContext, x, y, z) + LOCAL_WEIGHT * local.sampleValue(samplerContext, x, y, z)));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction zones = rule.rewrite(this.zones);
        DensityFunction local = rule.rewrite(this.local);
        return zones == this.zones && local == this.local ? this : new HumidityBlend(zones, local, this.gain);
    }

    @Override
    public Interval range() {
        return Densities.range(this.gain * (ZONE_WEIGHT * this.zones.range().min() + LOCAL_WEIGHT * this.local.range().min()),
                this.gain * (ZONE_WEIGHT * this.zones.range().max() + LOCAL_WEIGHT * this.local.range().max()));
    }

    @Override
    public int domainAxes() {
        return this.zones.domainAxes() | this.local.domainAxes();
    }

    @Override
    public MapCodec<HumidityBlend> codec() {
        return DATA_CODEC;
    }
}
