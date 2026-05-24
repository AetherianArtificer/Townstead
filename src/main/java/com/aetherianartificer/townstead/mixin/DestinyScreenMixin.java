package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.client.gui.origin.OriginListWidget;
import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.origin.OriginSetC2SPayload;
import net.conczin.mca.client.gui.DestinyScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds an "origins" page to MCA's Destiny character-creation screen, beside the
 * model in the editor's content column. The Destiny screen is the player's own
 * character, so the picker always targets the player ({@link OriginSetC2SPayload#SELF}).
 * {@code DestinyScreen} overrides {@code getPages}/{@code setPage}, so it needs its
 * own injections.
 */
@Mixin(DestinyScreen.class)
public abstract class DestinyScreenMixin extends Screen {

    private DestinyScreenMixin() {
        super(null);
    }

    @Inject(method = "getPages", remap = false, at = @At("RETURN"), cancellable = true)
    private void townstead$appendOriginsPage(CallbackInfoReturnable<String[]> cir) {
        String[] original = cir.getReturnValue();
        for (String page : original) {
            if ("origins".equals(page)) return;
        }
        String[] out = new String[original.length + 1];
        System.arraycopy(original, 0, out, 0, original.length);
        out[original.length] = "origins";
        cir.setReturnValue(out);
    }

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$buildOriginsPage(String page, CallbackInfo ci) {
        if (!"origins".equals(page)) return;

        OriginListWidget widget = new OriginListWidget(
                Minecraft.getInstance(),
                this.width / 2, 178, 150, this.height / 2 - 75, OriginSetC2SPayload.SELF,
                this::townstead$sendOriginSet);
        widget.setEntries(OriginCatalogClient.get());
        addRenderableWidget(widget);

        townstead$sendOriginSet("");
    }

    @Unique
    private void townstead$sendOriginSet(String originId) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new OriginSetC2SPayload(OriginSetC2SPayload.SELF, originId));
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(
                new OriginSetC2SPayload(OriginSetC2SPayload.SELF, originId));
        *///?}
    }
}
