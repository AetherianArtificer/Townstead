package com.aetherianartificer.townstead.mixin.accessor;

import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.client.gui.components.Button;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(BlueprintScreen.class)
public interface BlueprintScreenAccessor {
    @Accessor("village")
    Village townstead$getVillage();

    @Accessor("catalogButtons")
    List<Button> townstead$getCatalogButtons();
}
