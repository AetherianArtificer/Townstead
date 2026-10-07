package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.client.gui.VillagerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(VillagerEditorScreen.class)
public interface VillagerEditorClothingAccessor {
    @Accessor(value = "hoveredClothingId", remap = false)
    int townstead$getHoveredClothingId();

    @Accessor(value = "filteredClothing", remap = false)
    List<String> townstead$getFilteredClothing();
}
