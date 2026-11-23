package io.github.trini3.landmasslite.climate;

import io.github.trini3.landmasslite.Landmass;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class ModNoises {
    /** Slow, large-scale noise (features roughly 8,000 to 30,000 blocks across) that bends the zone borders. */
    public static final ResourceKey<NormalNoise> BORDER_WANDER =
            ResourceKey.create(Registries.NOISE, Landmass.id("border_wander"));

    /** Patches about a thousand blocks across where beaches are gravel instead of sand (with the Shores settings). */
    public static final ResourceKey<NormalNoise> SHINGLE =
            ResourceKey.create(Registries.NOISE, Landmass.id("shingle"));

    private ModNoises() {
    }
}
