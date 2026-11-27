package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.root.RootPicker;
import net.conczin.mca.client.gui.DestinyScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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

    /** The surname row, directly under the name. Accept takes up the gap above it to stay on screen. */
    @Inject(method = "setPage", remap = false, at = @At("RETURN"))
    private void townstead$addNamingRow(String page, CallbackInfo ci) {
        com.aetherianartificer.townstead.client.gui.naming.NamingRow.commitActive();
        if (!"general".equals(page) || this.minecraft == null || this.minecraft.player == null) return;
        if (!com.aetherianartificer.townstead.client.gui.naming.NamingRow.available()) return;
        int nameY = height / 2;
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getY() > nameY + 5) {
                widget.setY(widget.getY() + (widget.getY() > nameY + 70 ? 10 : 22));
            }
        }
        net.conczin.mca.client.gui.VillagerEditorScreen self = (net.conczin.mca.client.gui.VillagerEditorScreen) (Object) this;
        com.aetherianartificer.townstead.client.gui.naming.NamingRow.add(this,
                new com.aetherianartificer.townstead.client.gui.naming.NamingScreen.Target(this.minecraft.player.getId(),
                        this.minecraft.player.getUUID(), () -> self.getName().getString(), true),
                this.font, width / 2 - 175 / 2, nameY, nameY + 22, new java.util.ArrayList<>(children()), this::addRenderableWidget);
    }

    @Inject(method = "getPages", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$appendRootsPage(CallbackInfoReturnable<String[]> cir) {
        String[] pages = cir.getReturnValue();
        if (townstead$rootChoiceAllowed()) pages = RootPicker.insertRootsPage(pages);
        if (townstead$aspectStartAllowed()) {
            pages = com.aetherianartificer.townstead.client.gui.aspect.AspectPage.insertPage(pages, "origins");
        }
        cir.setReturnValue(pages);
    }

    private static boolean townstead$aspectStartAllowed() {
        if (!com.aetherianartificer.townstead.client.gui.aspect.AspectPage.available()) return false;
        try {
            return com.aetherianartificer.townstead.switchboard.Switchboard.get(
                    com.aetherianartificer.townstead.TownsteadConfig.ALLOW_ASPECT_START);
        } catch (IllegalStateException e) {
            return true;
        }
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
