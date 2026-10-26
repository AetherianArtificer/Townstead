package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.TownsteadKeybinds;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.KeyMapping;
//? if neoforge {
import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
//?} else {
/*import net.minecraft.client.gui.screens.controls.KeyBindsList;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Leaves the keys of switched-off systems out of the Controls screen. Their bindings stay saved. */
@Mixin(KeyBindsList.class)
public abstract class KeyBindsListSystemsMixin {
    @ModifyExpressionValue(method = "<init>", remap = false,
            at = @At(value = "INVOKE", target = "Lorg/apache/commons/lang3/ArrayUtils;clone([Ljava/lang/Object;)[Ljava/lang/Object;"))
    private Object[] townstead$hideOffSystemKeys(Object[] mappings) {
        return TownsteadKeybinds.shownInControls((KeyMapping[]) mappings);
    }
}
