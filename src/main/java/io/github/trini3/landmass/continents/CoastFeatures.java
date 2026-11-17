package io.github.trini3.landmass.continents;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.landmass.util.CodecUtil;

import java.util.List;
import java.util.Locale;

/**
 * Coastal landforms added to the continents and islands: land bridges (isthmuses) between neighbouring landmasses, some
 * of them broken into island chains and some reaching only part way and tapering into the water; sand bars (spits with
 * hooked tips, and barriers enclosing lagoons) on shallow shoals; small sandbank islets off both; and straits cut through
 * landmasses that touch.
 *
 * @param bridgeAmount how common land bridges are compared with the default (1), 0 for none
 * @param bridgeWidth  how wide a bridge is at its narrowest, in blocks; each picks a width in between, and bridges between
 *                     small islands stay narrower than the islands themselves
 * @param bridgeLength the widest stretch of water, in blocks, that a bridge crosses
 * @param chainShare   the part of bridges, 0 to 1, that come as chains of islands instead of solid land
 * @param barAmount    how much of the coastline has sand bars compared with the default (1), 0 for none
 * @param barWidth     how wide sand bars are, in blocks
 * @param barLength    roughly how long one sand bar runs along the coast, in blocks
 * @param bridgeIslets how many sandbank islets lie off bridges, compared with the default (1)
 * @param lagoonShare  the part of sand bars, 0 to 1, that are barriers joined to the shore at both ends, closing off a lagoon
 * @param barIslets    how many sandbank islets lie off sand bars, compared with the default (1)
 * @param straitAmount how common straits between landmasses that touch are, compared with the default (1), 0 for none
 * @param straitWidth  how wide a strait is at its narrowest, in blocks
 */
public record CoastFeatures(double bridgeAmount, ContinentLayout.SizeRange bridgeWidth, int bridgeLength, double chainShare,
                            double barAmount, int barWidth, int barLength, double bridgeIslets, double lagoonShare, double barIslets,
                            double straitAmount, ContinentLayout.SizeRange straitWidth) {
    // Recommended ranges (the sliders); the hard limits below can be typed in with a warning.
    public static final int MIN_BRIDGE_WIDTH = 10;
    public static final int MAX_BRIDGE_WIDTH = 100_000;
    public static final int MIN_BRIDGE_LENGTH = 50;
    public static final int MAX_BRIDGE_LENGTH = 1_000_000;
    public static final int MIN_BAR_WIDTH = 4;
    public static final int MAX_BAR_WIDTH = 2_000;
    public static final int MIN_BAR_LENGTH = 50;
    public static final int MAX_BAR_LENGTH = 100_000;
    public static final int MIN_STRAIT_WIDTH = 10;
    public static final int MAX_STRAIT_WIDTH = 100_000;
    public static final int LIMIT_MIN_BAR_WIDTH = 2;
    public static final int LIMIT_MAX_BAR_WIDTH = 100_000;
    public static final int LIMIT_MIN_LENGTH = 10;
    public static final int LIMIT_MAX_LENGTH = 30_000_000;

    /** What new worlds get. */
    public static final CoastFeatures DEFAULT = new CoastFeatures(1.0, new ContinentLayout.SizeRange(60, 2_000), 8_000, 0.35, 1.0, 24, 1_200,
            1.0, 0.3, 1.0, 1.0, new ContinentLayout.SizeRange(150, 3_000));
    /** What worlds made before 0.12.0 get: no added coastal features. */
    public static final CoastFeatures NONE = DEFAULT.withBridgeAmount(0.0).withBarAmount(0.0).withStraitAmount(0.0);

    private static final Codec<Double> AMOUNT = Codec.doubleRange(0.0, ContinentLayout.LIMIT_MAX_AMOUNT);

    public static final Codec<CoastFeatures> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(AMOUNT, "bridge_amount", DEFAULT.bridgeAmount).forGetter(CoastFeatures::bridgeAmount),
            CodecUtil.field(ContinentLayout.SizeRange.CODEC, "bridge_width", DEFAULT.bridgeWidth).forGetter(CoastFeatures::bridgeWidth),
            CodecUtil.field(Codec.intRange(LIMIT_MIN_LENGTH, LIMIT_MAX_LENGTH), "bridge_length", DEFAULT.bridgeLength).forGetter(CoastFeatures::bridgeLength),
            CodecUtil.field(Codec.doubleRange(0.0, 1.0), "chain_share", DEFAULT.chainShare).forGetter(CoastFeatures::chainShare),
            CodecUtil.field(AMOUNT, "bar_amount", DEFAULT.barAmount).forGetter(CoastFeatures::barAmount),
            CodecUtil.field(Codec.intRange(LIMIT_MIN_BAR_WIDTH, LIMIT_MAX_BAR_WIDTH), "bar_width", DEFAULT.barWidth).forGetter(CoastFeatures::barWidth),
            CodecUtil.field(Codec.intRange(LIMIT_MIN_LENGTH, LIMIT_MAX_LENGTH), "bar_length", DEFAULT.barLength).forGetter(CoastFeatures::barLength),
            // From 0.13.0; 0.12.0 files get the defaults, except straits, which would cut through land those worlds already have.
            CodecUtil.field(AMOUNT, "bridge_islets", DEFAULT.bridgeIslets).forGetter(CoastFeatures::bridgeIslets),
            CodecUtil.field(Codec.doubleRange(0.0, 1.0), "lagoon_share", DEFAULT.lagoonShare).forGetter(CoastFeatures::lagoonShare),
            CodecUtil.field(AMOUNT, "bar_islets", DEFAULT.barIslets).forGetter(CoastFeatures::barIslets),
            CodecUtil.field(AMOUNT, "strait_amount", 0.0).forGetter(CoastFeatures::straitAmount),
            CodecUtil.field(ContinentLayout.SizeRange.CODEC, "strait_width", DEFAULT.straitWidth).forGetter(CoastFeatures::straitWidth)
    ).apply(instance, CoastFeatures::new));

    public boolean bridges() {
        return this.bridgeAmount > 0.0;
    }

    public boolean bars() {
        return this.barAmount > 0.0;
    }

    public boolean straits() {
        return this.straitAmount > 0.0;
    }

    public CoastFeatures withBridgeAmount(double v) {
        return new CoastFeatures(v, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBridgeWidth(ContinentLayout.SizeRange v) {
        return new CoastFeatures(bridgeAmount, v, bridgeLength, chainShare, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBridgeLength(int v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, v, chainShare, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withChainShare(double v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, v, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBarAmount(double v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, v, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBarWidth(int v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, v, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBarLength(int v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, v, bridgeIslets, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBridgeIslets(double v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, barLength, v, lagoonShare, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withLagoonShare(double v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, barLength, bridgeIslets, v, barIslets, straitAmount, straitWidth);
    }

    public CoastFeatures withBarIslets(double v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, v, straitAmount, straitWidth);
    }

    public CoastFeatures withStraitAmount(double v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, v, straitWidth);
    }

    public CoastFeatures withStraitWidth(ContinentLayout.SizeRange v) {
        return new CoastFeatures(bridgeAmount, bridgeWidth, bridgeLength, chainShare, barAmount, barWidth, barLength, bridgeIslets, lagoonShare, barIslets, straitAmount, v);
    }

    /** For the log. */
    public List<String> describe() {
        return List.of(String.format(Locale.ROOT, "Land bridges %d%%, %s blocks wide, across up to %,d blocks, %d%% as island chains, islets %d%%; "
                        + "sand bars %d%%, %,d blocks wide, about %,d long, %d%% closing lagoons, islets %d%%; straits %d%%, %s blocks wide",
                Math.round(bridgeAmount * 100), bridgeWidth, bridgeLength, Math.round(chainShare * 100), Math.round(bridgeIslets * 100),
                Math.round(barAmount * 100), barWidth, barLength, Math.round(lagoonShare * 100), Math.round(barIslets * 100),
                Math.round(straitAmount * 100), straitWidth));
    }
}
