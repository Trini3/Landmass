package io.github.trini3.landmasslite.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmasslite.util.CodecUtil;

import java.util.Locale;

/**
 * What the coasts are like: long stretches of shore, each one kind. Minecraft only puts beaches at the shore where its
 * weirdness noise allows, which flips every few hundred blocks and leaves beaches patchy; near the shore these settings steer
 * the noises Minecraft (and biome mods) pick coast biomes and coast terrain by, so each stretch stays one kind.
 *
 * @param beaches      how much of the coast is sand or gravel beaches (shares are relative to each other)
 * @param gravelShare  the part of beaches, 0 to 1, that are gravel (shingle) instead of sand
 * @param rocky        how much of the coast is low rocky shore (stony shore)
 * @param cliffs       how much of the coast is rocky cliffs: the land rises steeply right from the water
 * @param grassy       how much of the coast has the land's own ground running straight into the sea, without a beach
 * @param stretch      roughly how long one stretch of a kind of shore is, in blocks
 */
public record Shores(double beaches, double gravelShare, double rocky, double cliffs, double grassy, int stretch) {
    public static final int MIN_STRETCH = 200;
    public static final int MAX_STRETCH = 200_000;
    public static final int LIMIT_MIN_STRETCH = 50;
    public static final int LIMIT_MAX_STRETCH = 30_000_000;
    public static final double MAX_SHARE = 4.0;
    public static final double LIMIT_MAX_SHARE = 10.0;

    /** What new worlds get. */
    public static final Shores DEFAULT = new Shores(1.0, 0.25, 0.5, 0.35, 0.6, 2_500);
    /** What worlds made before 0.18.0 get: coasts as Minecraft picks them. */
    public static final Shores NONE = new Shores(0.0, 0.0, 0.0, 0.0, 0.0, 2_500);

    private static final Codec<Double> SHARE = Codec.doubleRange(0.0, LIMIT_MAX_SHARE);

    public static final Codec<Shores> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(SHARE, "beaches", DEFAULT.beaches).forGetter(Shores::beaches),
            CodecUtil.field(Codec.doubleRange(0.0, 1.0), "gravel_share", DEFAULT.gravelShare).forGetter(Shores::gravelShare),
            CodecUtil.field(SHARE, "rocky", DEFAULT.rocky).forGetter(Shores::rocky),
            CodecUtil.field(SHARE, "cliffs", DEFAULT.cliffs).forGetter(Shores::cliffs),
            CodecUtil.field(SHARE, "grassy", DEFAULT.grassy).forGetter(Shores::grassy),
            CodecUtil.field(Codec.intRange(LIMIT_MIN_STRETCH, LIMIT_MAX_STRETCH), "stretch", DEFAULT.stretch).forGetter(Shores::stretch)
    ).apply(instance, Shores::new));

    /** Whether the mod shapes the coasts at all; with every share at 0, Minecraft picks coasts as it always has. */
    public boolean enabled() {
        return this.beaches + this.rocky + this.cliffs + this.grassy > 0.0;
    }

    /** Whether some beaches are gravel. */
    public boolean gravel() {
        return this.beaches > 0.0 && this.gravelShare > 0.0;
    }

    /**
     * The shingle noise value above which a beach is gravel, for the gravel share asked for. The noise is roughly normal with a
     * spread of about 0.3.
     */
    public double gravelThreshold() {
        if (this.gravelShare >= 1.0) {
            return -10.0;
        }
        double p = 1.0 - this.gravelShare;
        double q = p < 0.5 ? p : 1.0 - p;
        double t = Math.sqrt(-2.0 * Math.log(q));
        double x = t - (2.515517 + 0.802853 * t + 0.010328 * t * t) / (1.0 + 1.432788 * t + 0.189269 * t * t + 0.001308 * t * t * t);
        return 0.3 * (p < 0.5 ? -x : x);
    }

    public Shores withBeaches(double v) {
        return new Shores(v, gravelShare, rocky, cliffs, grassy, stretch);
    }

    public Shores withGravelShare(double v) {
        return new Shores(beaches, v, rocky, cliffs, grassy, stretch);
    }

    public Shores withRocky(double v) {
        return new Shores(beaches, gravelShare, v, cliffs, grassy, stretch);
    }

    public Shores withCliffs(double v) {
        return new Shores(beaches, gravelShare, rocky, v, grassy, stretch);
    }

    public Shores withGrassy(double v) {
        return new Shores(beaches, gravelShare, rocky, cliffs, v, stretch);
    }

    public Shores withStretch(int v) {
        return new Shores(beaches, gravelShare, rocky, cliffs, grassy, v);
    }

    /** For the log. */
    public String describe() {
        if (!this.enabled()) {
            return "Shores: as Minecraft picks them";
        }
        double total = this.beaches + this.rocky + this.cliffs + this.grassy;
        return String.format(Locale.ROOT, "Shores in stretches of about %,d blocks: beaches %d%% (%d%% of them gravel), rocky %d%%, cliffs %d%%, grassy %d%%",
                this.stretch, Math.round(100 * this.beaches / total), Math.round(100 * this.gravelShare), Math.round(100 * this.rocky / total),
                Math.round(100 * this.cliffs / total), Math.round(100 * this.grassy / total));
    }
}
