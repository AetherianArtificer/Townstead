package com.aetherianartificer.townstead.mixin;

//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSetPayload;
import com.aetherianartificer.townstead.compat.thirst.ThirstWasTakenBridge;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.ThirstSetPayload;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VillagerEditorScreen.class)
public abstract class VillagerEditorMixin extends Screen {

    @Shadow(remap = false) protected String page;
    @Shadow(remap = false) @Final protected VillagerEntityMCA villager;
    @Shadow(remap = false) protected CompoundTag villagerData;

    private VillagerEditorMixin() {
        super(null);
    }

    @Unique private int townstead$editorHunger;
    @Unique private int townstead$editorThirst;
    @Unique private Button townstead$hungerDisplay;
    @Unique private Button townstead$thirstDisplay;
    @Unique private boolean townstead$hungerDirty;
    @Unique private boolean townstead$thirstDirty;

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$addHungerDebug(String page, CallbackInfo ci) {
        // Clean up callback when switching pages
        townstead$hungerDisplay = null;
        townstead$thirstDisplay = null;
        townstead$hungerDirty = false;
        townstead$thirstDirty = false;
        HungerClientStore.clearOnChange();
        ThirstClientStore.clearOnChange();

        if (!"debug".equals(page)) return;

        // Read current hunger from client store (synced from server)
        boolean thirstAvailable = ThirstWasTakenBridge.INSTANCE.isActive();
        townstead$editorHunger = HungerClientStore.get(villager.getId());
        townstead$editorThirst = thirstAvailable
                ? ThirstClientStore.getThirst(villager.getId())
                : ThirstData.DEFAULT_THIRST;

        // Position below the mood control (last widget on debug page)
        int hungerY = height / 2 - 80 + 130;
        int thirstY = hungerY + 24;
        int bw = 22;
        int dataWidth = 175;

        Button hungerDisplay = addRenderableWidget(
                Button.builder(townstead$hungerLabel(), b -> {})
                        .pos(width / 2 + bw * 2, hungerY)
                        .size(dataWidth - bw * 4, 20)
                        .build()
        );
        townstead$hungerDisplay = hungerDisplay;

        addRenderableWidget(
                Button.builder(Component.literal("-5"), b -> {
                    townstead$modHunger(-5);
                    hungerDisplay.setMessage(townstead$hungerLabel());
                }).pos(width / 2, hungerY).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("-50"), b -> {
                    townstead$modHunger(-50);
                    hungerDisplay.setMessage(townstead$hungerLabel());
                }).pos(width / 2 + bw, hungerY).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("+50"), b -> {
                    townstead$modHunger(50);
                    hungerDisplay.setMessage(townstead$hungerLabel());
                }).pos(width / 2 + dataWidth - bw * 2, hungerY).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("+5"), b -> {
                    townstead$modHunger(5);
                    hungerDisplay.setMessage(townstead$hungerLabel());
                }).pos(width / 2 + dataWidth - bw, hungerY).size(bw, 20).build()
        );

        if (thirstAvailable) {
            Button thirstDisplay = addRenderableWidget(
                    Button.builder(townstead$thirstLabel(), b -> {})
                            .pos(width / 2 + bw * 2, thirstY)
                            .size(dataWidth - bw * 4, 20)
                            .build()
            );
            townstead$thirstDisplay = thirstDisplay;

            addRenderableWidget(
                    Button.builder(Component.literal("-1"), b -> {
                        townstead$modThirst(-1);
                        thirstDisplay.setMessage(townstead$thirstLabel());
                    }).pos(width / 2, thirstY).size(bw, 20).build()
            );
            addRenderableWidget(
                    Button.builder(Component.literal("-5"), b -> {
                        townstead$modThirst(-5);
                        thirstDisplay.setMessage(townstead$thirstLabel());
                    }).pos(width / 2 + bw, thirstY).size(bw, 20).build()
            );
            addRenderableWidget(
                    Button.builder(Component.literal("+5"), b -> {
                        townstead$modThirst(5);
                        thirstDisplay.setMessage(townstead$thirstLabel());
                    }).pos(width / 2 + dataWidth - bw * 2, thirstY).size(bw, 20).build()
            );
            addRenderableWidget(
                    Button.builder(Component.literal("+1"), b -> {
                        townstead$modThirst(1);
                        thirstDisplay.setMessage(townstead$thirstLabel());
                    }).pos(width / 2 + dataWidth - bw, thirstY).size(bw, 20).build()
            );
        }

        // Register callback: when server sync arrives, update the display
        // (only if user hasn't manually edited yet)
        HungerClientStore.setOnChange(() -> {
            if (!townstead$hungerDirty && townstead$hungerDisplay != null && "debug".equals(this.page)) {
                townstead$editorHunger = HungerClientStore.get(villager.getId());
                townstead$hungerDisplay.setMessage(townstead$hungerLabel());
            }
        });
        if (thirstAvailable) {
            ThirstClientStore.setOnChange(() -> {
                if (!townstead$thirstDirty && townstead$thirstDisplay != null && "debug".equals(this.page)) {
                    townstead$editorThirst = ThirstClientStore.getThirst(villager.getId());
                    townstead$thirstDisplay.setMessage(townstead$thirstLabel());
                }
            });
        }

        // Request fresh hunger data from server
        //? if neoforge {
        PacketDistributor.sendToServer(new HungerSetPayload(villager.getId(), -1));
        if (thirstAvailable) {
            PacketDistributor.sendToServer(new ThirstSetPayload(villager.getId(), -1));
        }
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new HungerSetPayload(villager.getId(), -1));
        if (thirstAvailable) {
            TownsteadNetwork.sendToServer(new ThirstSetPayload(villager.getId(), -1));
        }
        *///?}
    }

    //? if neoforge {
    @Inject(method = "removed", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_7861_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$cleanupOnClose(CallbackInfo ci) {
        HungerClientStore.clearOnChange();
        ThirstClientStore.clearOnChange();
        townstead$hungerDisplay = null;
        townstead$thirstDisplay = null;
    }

    //? if neoforge {
    @Inject(method = "tick", at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_86600_", remap = false, at = @At("TAIL"))
    *///?}
    private void townstead$refreshDebugValue(CallbackInfo ci) {
        if (!"debug".equals(this.page)) return;
        if (!townstead$hungerDirty && townstead$hungerDisplay != null) {
            int syncedHunger = HungerClientStore.get(villager.getId());
            if (syncedHunger != townstead$editorHunger) {
                townstead$editorHunger = syncedHunger;
                townstead$hungerDisplay.setMessage(townstead$hungerLabel());
            }
        }
        if (!townstead$thirstDirty && townstead$thirstDisplay != null) {
            int syncedThirst = ThirstClientStore.getThirst(villager.getId());
            if (syncedThirst != townstead$editorThirst) {
                townstead$editorThirst = syncedThirst;
                townstead$thirstDisplay.setMessage(townstead$thirstLabel());
            }
        }
    }

    @Unique
    private void townstead$modHunger(int delta) {
        townstead$hungerDirty = true;
        townstead$editorHunger = Math.max(0, Math.min(townstead$editorHunger + delta, HungerData.MAX_HUNGER));
        HungerClientStore.set(villager.getId(), townstead$editorHunger, 1, 0, 0, 1, 0, 0, 1, 0, 0);
        // Write into villagerData — MCA's syncVillagerData() will carry these to the server
        // when the user clicks "Done"
        villagerData.putInt(HungerData.EDITOR_KEY_HUNGER, townstead$editorHunger);
        villagerData.putFloat(HungerData.EDITOR_KEY_SATURATION,
                delta > 0 ? Math.min(townstead$editorHunger, HungerData.MAX_SATURATION) : 0f);
        villagerData.putFloat(HungerData.EDITOR_KEY_EXHAUSTION, 0f);
    }

    @Unique
    private void townstead$modThirst(int delta) {
        townstead$thirstDirty = true;
        townstead$editorThirst = Math.max(0, Math.min(townstead$editorThirst + delta, ThirstData.MAX_THIRST));
        int quenched = Math.min(ThirstData.MAX_QUENCHED, townstead$editorThirst);
        ThirstClientStore.set(villager.getId(), townstead$editorThirst, quenched);
        villagerData.putInt(ThirstData.EDITOR_KEY_THIRST, townstead$editorThirst);
        villagerData.putInt(ThirstData.EDITOR_KEY_QUENCHED, quenched);
        villagerData.putFloat(ThirstData.EDITOR_KEY_EXHAUSTION, 0f);
    }

    @Unique
    private Component townstead$hungerLabel() {
        return Component.translatable("townstead.hunger.editor", townstead$editorHunger);
    }

    @Unique
    private Component townstead$thirstLabel() {
        return Component.translatable("townstead.thirst.editor", townstead$editorThirst);
    }
}
