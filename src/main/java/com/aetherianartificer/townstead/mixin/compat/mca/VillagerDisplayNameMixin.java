package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.naming.DisplayNames;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Puts a villager's family name on every surface that draws their name.
 *
 * <p>{@code getDisplayName} is what a screen, a tooltip, a chat line and a nameplate all reach for,
 * so composing here reaches surfaces Townstead does not own: MCA's own screens, MCA Capitals', and
 * any screen a future mod draws. The alternative was finding and patching every call site, which
 * cannot reach another mod's code at all and silently regresses every time a screen is added.</p>
 *
 * <p>{@code getName} is deliberately left alone. That is the villager's identity rather than their
 * label: the editor edits it, MCA's family tree stores it, and a patronymic is built from it, so a
 * surname appearing there would compound into the next generation's name.</p>
 */
@Mixin(VillagerEntityMCA.class)
public abstract class VillagerDisplayNameMixin {

    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void townstead$composeDisplayName(CallbackInfoReturnable<Component> cir) {
        VillagerEntityMCA self = (VillagerEntityMCA) (Object) this;
        Component composed = DisplayNames.of(self, cir.getReturnValue());
        if (composed != cir.getReturnValue()) {
            cir.setReturnValue(composed);
        }
    }
}
