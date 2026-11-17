package io.github.trini3.acclimatization.climate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.trini3.acclimatization.util.CodecUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Climate deviation zones (0.15.0): scattered patches where the climate jumps away from what latitude and humidity would give,
 * like a snowy forest next to a desert, a cool rain-shadow desert or a wet pocket in dry land.
 *
 * @param temperature how many temperature zones (hot, warm, temperate, cold, frozen) a patch can jump, colder or warmer; 0 = none
 * @param humidity    how many humidity bands (Minecraft's five, arid to humid) a patch can jump, drier or wetter; 0 = none
 * @param size        the typical width of a patch in blocks (each one is between about 0.7 and 1.3 times this)
 * @param frequency   the share of the world inside patches, from 0 to 1
 */
public record ClimateDeviation(int temperature, int humidity, double size, double frequency) {
    public static final int MAX_TEMPERATURE = 5;
    public static final int MAX_HUMIDITY = 4;
    public static final double MIN_SIZE = 50;
    public static final double MAX_SIZE = 5_000_000;
    // Around 1 in 20 blocks of land in a patch at a few thousand blocks across: at the default zone sizes (about 1:70 of the Earth)
    // that's microclimates a couple of hundred kilometres wide, like the Atacama, the Namib or the Gobi's edges.
    public static final ClimateDeviation DEFAULT = new ClimateDeviation(1, 1, 3_000, 0.05);
    /** Worlds made before 0.15.0, which have no deviation. */
    public static final ClimateDeviation OFF = new ClimateDeviation(0, 0, 3_000, 0.0);

    public static final Codec<ClimateDeviation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecUtil.field(Codec.intRange(0, MAX_TEMPERATURE), "temperature_zones", DEFAULT.temperature()).forGetter(ClimateDeviation::temperature),
            CodecUtil.field(Codec.intRange(0, MAX_HUMIDITY), "humidity_zones", DEFAULT.humidity()).forGetter(ClimateDeviation::humidity),
            CodecUtil.field(Codec.doubleRange(MIN_SIZE, MAX_SIZE), "size", DEFAULT.size()).forGetter(ClimateDeviation::size),
            CodecUtil.field(Codec.doubleRange(0.0, 1.0), "frequency", DEFAULT.frequency()).forGetter(ClimateDeviation::frequency)
    ).apply(instance, ClimateDeviation::new));

    /** Whether any patches generate. */
    public boolean enabled() {
        return this.frequency > 0.0 && (this.temperature > 0 || this.humidity > 0);
    }

    public ClimateDeviation withTemperature(int zones) {
        return new ClimateDeviation(zones, this.humidity, this.size, this.frequency);
    }

    public ClimateDeviation withHumidity(int zones) {
        return new ClimateDeviation(this.temperature, zones, this.size, this.frequency);
    }

    public ClimateDeviation withSize(double size) {
        return new ClimateDeviation(this.temperature, this.humidity, size, this.frequency);
    }

    public ClimateDeviation withFrequency(double frequency) {
        return new ClimateDeviation(this.temperature, this.humidity, this.size, frequency);
    }

    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        if (!this.enabled()) {
            lines.add("Climate deviation: off.");
        } else {
            lines.add(String.format(Locale.ROOT, "Climate deviation: patches about %,d blocks across cover %.1f%% of the world, jumping up to %d "
                    + "temperature zone(s) and %d humidity band(s).", Math.round(this.size), 100 * this.frequency, this.temperature, this.humidity));
        }
        return lines;
    }
}
