package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.root.RootPicker;
import net.conczin.mca.client.gui.DestinyScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds the "origins" page to MCA's Destiny character-creation screen. Destiny
 * overrides {@code getPages} and never calls super, so the parent editor mixin's
 * page insertion can't reach it; this supplies it.
 *
 * <p>Everything else is inherited, not duplicated: Destiny routes its real pages
 * through {@code default -> super.setPage(page)}, so {@link VillagerEditorRootMixin}'s
 * {@code setPage}/{@code syncVillagerData} injections run for the Destiny screen too.
 * Since Destiny's {@code villagerUUID == playerUUID}, that mixin's self-edit path
 * targets the player ({@code RootSetC2SPayload.SELF}) and handles the preview tint,
 * live gene preview, revert-on-leave, the Body skin-picker recolor, and Apply.</p>
 */
@Mixin(DestinyScreen.class)
public abstract class DestinyScreenMixin extends Screen {

    private DestinyScreenMixin() {
        super(null);
    }

    @Inject(method = "getPages", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$appendRootsPage(CallbackInfoReturnable<String[]> cir) {
        if (!townstead$rootChoiceAllowed()) return;
        cir.setReturnValue(RootPicker.insertRootsPage(cir.getReturnValue()));
    }

    private static boolean townstead$rootChoiceAllowed() {
        try {
            return com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.ROOTS)
                    && com.aetherianartificer.townstead.switchboard.Switchboard.get(
                    com.aetherianartificer.townstead.TownsteadConfig.ALLOW_ROOT_CHOICE_IN_DESTINY);
        } catch (IllegalStateException e) {
            return true;
        }
    }
}
