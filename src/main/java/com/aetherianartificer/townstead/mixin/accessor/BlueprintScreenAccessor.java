package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BlueprintScreen.class)
public interface BlueprintScreenAccessor {
    @Accessor(value = "village", remap = false)
    Village townstead$getVillage();

    @Accessor(value = "catalogButtons", remap = false)
    List<Button> townstead$getCatalogButtons();
}
