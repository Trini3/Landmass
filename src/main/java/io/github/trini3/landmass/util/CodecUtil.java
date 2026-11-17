package io.github.trini3.landmass.util;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

public final class CodecUtil {
    private CodecUtil() {
    }

    /**
     * A field that falls back to a default when it's missing, but is always written out.
     * DFU's own optionalFieldOf skips values equal to the default when encoding, which would leave
     * the per-world settings file without most of the options players want to see and edit.
     */
    public static <A> MapCodec<A> field(Codec<A> codec, String name, A defaultValue) {
        return codec.optionalFieldOf(name).xmap(value -> value.orElse(defaultValue), Optional::of);
    }
}
