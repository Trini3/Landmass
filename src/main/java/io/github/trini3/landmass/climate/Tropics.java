package io.github.trini3.landmass.climate;

import net.minecraft.util.StringRepresentable;

/**
 * What the land near the equator looks like.
 *
 * <p>Minecraft (and the biome mods built on its climate system) only puts deserts and other dry biomes
 * in the hot temperature band; savannas, forests and jungles sit in the warm band and are picked by
 * humidity. So to get deserts and jungles at the same latitude, dry land has to be a little hotter.
 */
public enum Tropics implements StringRepresentable {
    /**
     * Humidity decides: dry land becomes desert, wetter land savanna, forest or jungle. Deserts are
     * most common toward the outer edge of the hot zone, like Earth's subtropics.
     */
    MIXED("mixed"),
    /** The whole hot zone is desert, with savannas and jungles in the warm zone further out. Version 0.1.0's layout. */
    DESERT("desert");

    public static final StringRepresentable.EnumCodec<Tropics> CODEC = StringRepresentable.fromEnum(Tropics::values);

    private final String serializedName;

    Tropics(String serializedName) {
        this.serializedName = serializedName;
    }

    @Override
    public String getSerializedName() {
        return this.serializedName;
    }
}
