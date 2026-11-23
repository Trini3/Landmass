package io.github.trini3.landmasslite.climate;

import net.minecraft.util.StringRepresentable;

/** Which block coordinate counts as latitude. */
public enum LatitudeAxis implements StringRepresentable {
    /** Bands run north to south, so going east or west changes the climate. */
    X("x"),
    /** Bands run east to west, so going north or south changes the climate. The realistic default. */
    Z("z");

    public static final StringRepresentable.EnumCodec<LatitudeAxis> CODEC = StringRepresentable.fromEnum(LatitudeAxis::values);

    private final String serializedName;

    LatitudeAxis(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }

    public int along(int x, int z) {
        return this == X ? x : z;
    }
}
