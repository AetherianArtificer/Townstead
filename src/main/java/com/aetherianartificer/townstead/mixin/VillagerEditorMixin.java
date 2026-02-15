package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSetPayload;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEditorScreen.class)
public abstract class VillagerEditorMixin extends Screen {

    @Shadow protected String page;
    @Shadow @Final protected VillagerEntityMCA villager;
    @Shadow protected CompoundTag villagerData;

    private VillagerEditorMixin() {
        super(null);
    }

    @Unique private int townstead$editorHunger;
    @Unique private Button townstead$hungerDisplay;
    @Unique private boolean townstead$editorDirty;

    @Inject(method = "setPage", at = @At("TAIL"))
    private void townstead$addHungerDebug(String page, CallbackInfo ci) {
        // Clean up callback when switching pages
        townstead$hungerDisplay = null;
        townstead$editorDirty = false;
        HungerClientStore.clearOnChange();

        if (!"debug".equals(page)) return;

        // Read current hunger from client store (synced from server)
        townstead$editorHunger = HungerClientStore.get(villager.getId());

        // Position below the mood control (last widget on debug page)
        int y = height / 2 - 80 + 130;
        int bw = 22;
        int dataWidth = 175;

        Button display = addRenderableWidget(
                Button.builder(townstead$hungerLabel(), b -> {})
                        .pos(width / 2 + bw * 2, y)
                        .size(dataWidth - bw * 4, 20)
                        .build()
        );
        townstead$hungerDisplay = display;

        addRenderableWidget(
                Button.builder(Component.literal("-5"), b -> {
                    townstead$modHunger(-5);
                    display.setMessage(townstead$hungerLabel());
                }).pos(width / 2, y).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("-50"), b -> {
                    townstead$modHunger(-50);
                    display.setMessage(townstead$hungerLabel());
                }).pos(width / 2 + bw, y).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("+50"), b -> {
                    townstead$modHunger(50);
                    display.setMessage(townstead$hungerLabel());
                }).pos(width / 2 + dataWidth - bw * 2, y).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("+5"), b -> {
                    townstead$modHunger(5);
                    display.setMessage(townstead$hungerLabel());
                }).pos(width / 2 + dataWidth - bw, y).size(bw, 20).build()
        );

        // Register callback: when server sync arrives, update the display
        // (only if user hasn't manually edited yet)
        HungerClientStore.setOnChange(() -> {
            if (!townstead$editorDirty && townstead$hungerDisplay != null && "debug".equals(this.page)) {
                townstead$editorHunger = HungerClientStore.get(villager.getId());
                townstead$hungerDisplay.setMessage(townstead$hungerLabel());
            }
        });

        // Request fresh hunger data from server
        PacketDistributor.sendToServer(new HungerSetPayload(villager.getId(), -1));
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void townstead$cleanupOnClose(CallbackInfo ci) {
        HungerClientStore.clearOnChange();
        townstead$hungerDisplay = null;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void townstead$refreshDebugValue(CallbackInfo ci) {
        if (!"debug".equals(this.page) || townstead$editorDirty || townstead$hungerDisplay == null) return;
        int synced = HungerClientStore.get(villager.getId());
        if (synced != townstead$editorHunger) {
            townstead$editorHunger = synced;
            townstead$hungerDisplay.setMessage(townstead$hungerLabel());
        }
    }

    @Unique
    private void townstead$modHunger(int delta) {
        townstead$editorDirty = true;
        townstead$editorHunger = Math.max(0, Math.min(townstead$editorHunger + delta, HungerData.MAX_HUNGER));
        HungerClientStore.set(villager.getId(), townstead$editorHunger);
        // Write into villagerData — MCA's syncVillagerData() will carry these to the server
        // when the user clicks "Done"
        villagerData.putInt(HungerData.EDITOR_KEY_HUNGER, townstead$editorHunger);
        villagerData.putFloat(HungerData.EDITOR_KEY_SATURATION,
                delta > 0 ? Math.min(townstead$editorHunger, HungerData.MAX_SATURATION) : 0f);
        villagerData.putFloat(HungerData.EDITOR_KEY_EXHAUSTION, 0f);
    }

    @Unique
    private Component townstead$hungerLabel() {
        HungerData.HungerState state = HungerData.getState(townstead$editorHunger);
        return Component.translatable("townstead.hunger.editor", townstead$editorHunger,
                Component.translatable(state.getTranslationKey()));
    }
}
