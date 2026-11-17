package io.github.trini3.acclimatization.climate;

import io.github.trini3.acclimatization.Acclimatization;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public final class ModNoises {
    /** Slow, large-scale noise (features roughly 8,000 to 30,000 blocks across) that bends the zone borders. */
    public static final ResourceKey<NormalNoise> BORDER_WANDER =
            ResourceKey.create(Registries.NOISE, Acclimatization.id("border_wander"));

    private ModNoises() {
    }
}
