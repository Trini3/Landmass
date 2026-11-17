package io.github.trini3.acclimatization.climate;

import net.minecraft.util.StringRepresentable;

/**
 * What the climate does past the poles.
 *
 * <p>A Minecraft world can't wrap around like a real globe: the land at its north edge can never join
 * up with the land at its south edge. What it can do is give the same climate you'd meet walking over
 * the top of a globe and down the far side.
 */
public enum Poles implements StringRepresentable {
    /**
     * Past each pole the zones come back in reverse (frozen, cold, temperate, warm, then another
     * equator) and keep repeating, like walking over the top of a globe and down the other side.
     */
    REPEAT("repeat"),
    /** Everything past the pole stays as cold as the pole. Versions 0.1.0 and 0.2.0 worked this way. */
    FROZEN("frozen");

    public static final StringRepresentable.EnumCodec<Poles> CODEC = StringRepresentable.fromEnum(Poles::values);

    private final String serializedName;

    Poles(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
