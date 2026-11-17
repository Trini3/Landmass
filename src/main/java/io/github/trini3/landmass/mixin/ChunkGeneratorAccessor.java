package io.github.trini3.landmass.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Function;

/**
 * Lets the Landmass generator swap in a filtered view of each biome's carvers and features, which is how terrain
 * features turned off on the Customize screen are left out of its world (and only its world: other generators are untouched).
 */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Accessor("generationSettingsGetter")
    Function<Holder<Biome>, BiomeGenerationSettings> landmass$getGenerationSettingsGetter();

    @Mutable
    @Accessor("generationSettingsGetter")
    void landmass$setGenerationSettingsGetter(Function<Holder<Biome>, BiomeGenerationSettings> getter);
}
