package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.Townstead;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
import com.aetherianartificer.townstead.compat.butchery.ButcherSettings;
import com.aetherianartificer.townstead.compat.butchery.ButcheryCompat;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.fatigue.FatigueSetPayload;
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.hunger.HungerSetPayload;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.ThirstSetPayload;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.npc.VillagerProfession;
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
    @Unique private int townstead$editorFatigue;
    @Unique private Button townstead$hungerDisplay;
    @Unique private Button townstead$thirstDisplay;
    @Unique private Button townstead$fatigueDisplay;
    @Unique private boolean townstead$hungerDirty;
    @Unique private boolean townstead$thirstDirty;
    @Unique private boolean townstead$fatigueDirty;
    @Unique private ButcherSettings.SlaughterOverride townstead$editorSlaughterOverride;

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$addHungerDebug(String page, CallbackInfo ci) {
        // Clean up callback when switching pages
        townstead$hungerDisplay = null;
        townstead$thirstDisplay = null;
        townstead$fatigueDisplay = null;
        townstead$hungerDirty = false;
        townstead$thirstDirty = false;
        townstead$fatigueDirty = false;
        HungerClientStore.clearOnChange();
        ThirstClientStore.clearOnChange();
        FatigueClientStore.clearOnChange();

        if (!"debug".equals(page)) return;

        // Seed the editor from the villager's current data so first open does not
        // show client-store defaults before the refresh packet comes back.
        boolean thirstAvailable = ThirstBridgeResolver.isActive();
        townstead$editorHunger = villagerData.contains(HungerData.EDITOR_KEY_HUNGER)
                ? villagerData.getInt(HungerData.EDITOR_KEY_HUNGER)
                : HungerClientStore.get(villager.getId());
        townstead$editorThirst = thirstAvailable
                ? (villagerData.contains(ThirstData.EDITOR_KEY_THIRST)
                    ? villagerData.getInt(ThirstData.EDITOR_KEY_THIRST)
                    : townstead$getCurrentThirst())
                : ThirstData.DEFAULT_THIRST;
        townstead$editorFatigue = villagerData.contains(FatigueData.EDITOR_KEY_FATIGUE)
                ? villagerData.getInt(FatigueData.EDITOR_KEY_FATIGUE)
                : townstead$getCurrentFatigue();

        // Position below the mood control (last widget on debug page)
        int hungerY = height / 2 - 80 + 130;
        int thirstY = hungerY + 24;
        int fatigueY = thirstAvailable ? thirstY + 24 : hungerY + 24;
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

        // Fatigue editor controls
        Button fatigueDisplay = addRenderableWidget(
                Button.builder(townstead$fatigueLabel(), b -> {})
                        .pos(width / 2 + bw * 2, fatigueY)
                        .size(dataWidth - bw * 4, 20)
                        .build()
        );
        townstead$fatigueDisplay = fatigueDisplay;

        addRenderableWidget(
                Button.builder(Component.literal("-1"), b -> {
                    townstead$modFatigue(-1);
                    fatigueDisplay.setMessage(townstead$fatigueLabel());
                }).pos(width / 2, fatigueY).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("-5"), b -> {
                    townstead$modFatigue(-5);
                    fatigueDisplay.setMessage(townstead$fatigueLabel());
                }).pos(width / 2 + bw, fatigueY).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("+5"), b -> {
                    townstead$modFatigue(5);
                    fatigueDisplay.setMessage(townstead$fatigueLabel());
                }).pos(width / 2 + dataWidth - bw * 2, fatigueY).size(bw, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("+1"), b -> {
                    townstead$modFatigue(1);
                    fatigueDisplay.setMessage(townstead$fatigueLabel());
                }).pos(width / 2 + dataWidth - bw, fatigueY).size(bw, 20).build()
        );

        // Butcher-only: per-villager slaughter toggle.
        if (ButcheryCompat.isLoaded()
                && villager.getVillagerData().getProfession() == VillagerProfession.BUTCHER) {
            int slaughterY = fatigueY + 24;
            townstead$editorSlaughterOverride = villagerData.contains(
                    ButcherSettings.EDITOR_KEY_SLAUGHTER_OVERRIDE)
                    ? ButcherSettings.SlaughterOverride.fromCode(
                            villagerData.getByte(ButcherSettings.EDITOR_KEY_SLAUGHTER_OVERRIDE))
                    : ButcherSettings.SlaughterOverride.FOLLOW_CONFIG;
            Button[] slaughterButton = new Button[1];
            slaughterButton[0] = addRenderableWidget(
                    Button.builder(townstead$slaughterLabel(), b -> {
                        townstead$cycleSlaughterOverride();
                        slaughterButton[0].setMessage(townstead$slaughterLabel());
                    }).pos(width / 2, slaughterY).size(dataWidth, 20).build()
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
                    townstead$editorThirst = townstead$getCurrentThirst();
                    townstead$thirstDisplay.setMessage(townstead$thirstLabel());
                }
            });
        }

        FatigueClientStore.setOnChange(() -> {
            if (!townstead$fatigueDirty && townstead$fatigueDisplay != null && "debug".equals(this.page)) {
                townstead$editorFatigue = townstead$getCurrentFatigue();
                townstead$fatigueDisplay.setMessage(townstead$fatigueLabel());
            }
        });

        // Request fresh data from server
        //? if neoforge {
        PacketDistributor.sendToServer(new HungerSetPayload(villager.getId(), -1));
        if (thirstAvailable) {
            PacketDistributor.sendToServer(new ThirstSetPayload(villager.getId(), -1));
        }
        PacketDistributor.sendToServer(new FatigueSetPayload(villager.getId(), -1));
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(new HungerSetPayload(villager.getId(), -1));
        if (thirstAvailable) {
            TownsteadNetwork.sendToServer(new ThirstSetPayload(villager.getId(), -1));
        }
        TownsteadNetwork.sendToServer(new FatigueSetPayload(villager.getId(), -1));
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
        FatigueClientStore.clearOnChange();
        townstead$hungerDisplay = null;
        townstead$thirstDisplay = null;
        townstead$fatigueDisplay = null;
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
            int syncedThirst = townstead$getCurrentThirst();
            if (syncedThirst != townstead$editorThirst) {
                townstead$editorThirst = syncedThirst;
                townstead$thirstDisplay.setMessage(townstead$thirstLabel());
            }
        }
        if (!townstead$fatigueDirty && townstead$fatigueDisplay != null) {
            int syncedFatigue = townstead$getCurrentFatigue();
            if (syncedFatigue != townstead$editorFatigue) {
                townstead$editorFatigue = syncedFatigue;
                townstead$fatigueDisplay.setMessage(townstead$fatigueLabel());
            }
        }
    }

    @Unique
    private void townstead$modHunger(int delta) {
        townstead$hungerDirty = true;
        townstead$editorHunger = Math.max(0, Math.min(townstead$editorHunger + delta, HungerData.MAX_HUNGER));
        HungerClientStore.set(villager.getId(), townstead$editorHunger, 1, 0, 0, 1, 0, 0);
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

    @Unique
    private int townstead$getCurrentThirst() {
        if (ThirstClientStore.hasThirst(villager.getId())) {
            return ThirstClientStore.getThirst(villager.getId());
        }
        //? if neoforge {
        return ThirstData.getThirst(villager.getData(Townstead.THIRST_DATA));
        //?} else if forge {
        /*return ThirstData.getThirst(villager.getPersistentData().getCompound("townstead_thirst"));
        *///?}
    }

    @Unique
    private void townstead$modFatigue(int delta) {
        townstead$fatigueDirty = true;
        // delta is in energy terms (positive = more energy = less fatigue)
        // so negate for internal fatigue storage
        townstead$editorFatigue = Math.max(0, Math.min(townstead$editorFatigue - delta, FatigueData.MAX_FATIGUE));
        FatigueClientStore.set(villager.getId(), townstead$editorFatigue, false);
        villagerData.putInt(FatigueData.EDITOR_KEY_FATIGUE, townstead$editorFatigue);
    }

    @Unique
    private Component townstead$fatigueLabel() {
        return Component.translatable("townstead.energy.editor", FatigueData.toEnergy(townstead$editorFatigue));
    }

    @Unique
    private void townstead$cycleSlaughterOverride() {
        townstead$editorSlaughterOverride = townstead$editorSlaughterOverride.next();
        villagerData.putByte(ButcherSettings.EDITOR_KEY_SLAUGHTER_OVERRIDE,
                townstead$editorSlaughterOverride.code);
    }

    @Unique
    private Component townstead$slaughterLabel() {
        String subKey = switch (townstead$editorSlaughterOverride) {
            case FOLLOW_CONFIG -> "follow";
            case ENABLED -> "enabled";
            case DISABLED -> "disabled";
        };
        return Component.translatable("townstead.butchery.slaughter." + subKey);
    }

    @Unique
    private int townstead$getCurrentFatigue() {
        if (FatigueClientStore.hasFatigue(villager.getId())) {
            return FatigueClientStore.getFatigue(villager.getId());
        }
        //? if neoforge {
        return FatigueData.getFatigue(villager.getData(Townstead.FATIGUE_DATA));
        //?} else if forge {
        /*return FatigueData.getFatigue(villager.getPersistentData().getCompound("townstead_fatigue"));
        *///?}
    }
}
