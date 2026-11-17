package io.github.trini3.landmass.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a Landmass generator build its terrain from the settings with the climate bands and continents in. */
@Mixin(NoiseBasedChunkGenerator.class)
public interface NoiseBasedChunkGeneratorAccessor {
    @Mutable
    @Accessor("settings")
    void landmass$setSettings(Holder<NoiseGeneratorSettings> settings);
}
