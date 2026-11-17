package io.github.trini3.landmass.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.trini3.landmass.client.LandmassClient;
import io.github.trini3.landmass.world.LandmassGenerator;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Map;
import java.util.Optional;

/** Gives the Landmass world type a Customize button: Minecraft looks world types up in a fixed map of editors. */
@Mixin(WorldCreationUiState.class)
public abstract class WorldCreationUiStateMixin {
    @WrapOperation(
            method = {"getPresetEditor", "updatePresetLists"},
            at = @At(value = "INVOKE", target = "Ljava/util/Map;get(Ljava/lang/Object;)Ljava/lang/Object;")
    )
    private Object landmass$addPresetEditor(Map<?, ?> editors, Object key, Operation<Object> original) {
        if (Optional.of(LandmassGenerator.PRESET).equals(key)) {
            return LandmassClient.PRESET_EDITOR;
        }
        return original.call(editors, key);
    }
}
