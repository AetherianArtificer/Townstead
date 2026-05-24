package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.origin.OriginPicker;
import com.aetherianartificer.townstead.origin.OriginSetC2SPayload;
import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * Adds an "origins" tab to MCA's Villager Editor. When editing an NPC the picker
 * targets that villager (by network id); when editing the player's own model
 * (villagerUUID == playerUUID) it targets the player ({@link OriginSetC2SPayload#SELF}).
 *
 * <p>The picker sits in the editor's right-hand content column
 * ({@code [width/2, width/2+175]}), beside the rotating model — the same region
 * MCA's other page widgets use. The Destiny screen is a subclass with its own
 * {@code getPages}/{@code setPage}, handled by {@link DestinyScreenMixin}, so this
 * mixin skips it.</p>
 */
@Mixin(VillagerEditorScreen.class)
public abstract class VillagerEditorOriginMixin extends Screen {

    @Shadow(remap = false) @Final protected VillagerEntityMCA villager;
    @Shadow(remap = false) @Final UUID villagerUUID;
    @Shadow(remap = false) @Final UUID playerUUID;

    private VillagerEditorOriginMixin() {
        super(null);
    }

    @Inject(method = "getPages", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$appendOriginsPage(CallbackInfoReturnable<String[]> cir) {
        if ((Object) this instanceof DestinyScreen) return;
        String[] original = cir.getReturnValue();
        String[] out = new String[original.length + 1];
        System.arraycopy(original, 0, out, 0, original.length);
        out[original.length] = "origins";
        cir.setReturnValue(out);
    }

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$buildOriginsPage(String page, CallbackInfo ci) {
        if ((Object) this instanceof DestinyScreen) return;
        if (!"origins".equals(page)) return;

        int target = villagerUUID.equals(playerUUID) ? OriginSetC2SPayload.SELF : villager.getId();
        // Span MCA's content column exactly: search top aligns with the General
        // name field (h/2-80); Apply lands on the Done button's row (h/2+85, height 20);
        // right edge aligned to the tab row (w/2+175).
        OriginPicker.Widgets ws = OriginPicker.build(
                Minecraft.getInstance(),
                this.width / 2, this.height / 2 - 80, 175, 185, target,
                originId -> townstead$sendOriginSet(target, originId));
        addRenderableWidget(ws.search());
        addRenderableWidget(ws.list());
        addRenderableWidget(ws.description());
        addRenderableWidget(ws.traits());
        addRenderableWidget(ws.apply());

        // Ask the server for the target's current origin so the row highlights.
        townstead$sendOriginSet(target, "");
    }

    @Unique
    private void townstead$sendOriginSet(int target, String originId) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new OriginSetC2SPayload(target, originId));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new OriginSetC2SPayload(target, originId));
        *///?}
    }
}
