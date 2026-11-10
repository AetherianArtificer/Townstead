package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.naming.NamingRow;
import com.aetherianartificer.townstead.client.gui.naming.NamingScreen;
import net.conczin.mca.client.gui.LimitedVillagerEditorScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** The surname row for players without the full editor. The name moves up one row to make room. */
@Mixin(LimitedVillagerEditorScreen.class)
public abstract class LimitedVillagerEditorSurnameMixin extends VillagerEditorScreen {

    private LimitedVillagerEditorSurnameMixin(UUID villagerUUID, UUID playerUUID) {
        super(villagerUUID, playerUUID);
    }

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$addNamingRow(String page, CallbackInfo ci) {
        if (!"general".equals(page) || this.minecraft == null || this.minecraft.player == null) return;
        if (!NamingRow.available()) return;

        int nameY = height / 2 - 40;
        for (net.minecraft.client.gui.components.events.GuiEventListener child : children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.getY() < nameY + 5) {
                widget.setY(widget.getY() - 22);
            }
        }
        // MCA only opens this screen on the player using it.
        NamingRow.add(this, new NamingScreen.Target(this.minecraft.player.getId(),
                this.minecraft.player.getUUID(), () -> getName().getString(), true),
                this.font, width / 2, nameY - 22, nameY, new java.util.ArrayList<>(children()), this::addRenderableWidget);
    }
}
