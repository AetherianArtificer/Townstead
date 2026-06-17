package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.TownsteadConfig;
//? if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;
*///?}
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
import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.calendar.LifeClientStore;
import com.aetherianartificer.townstead.calendar.LifeData;
import com.aetherianartificer.townstead.client.gui.life.LifeAgeSlider;
import com.aetherianartificer.townstead.client.skin.SeniorHairDesat;
import com.aetherianartificer.townstead.origin.LifeStageScale;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
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
    // MCA's `villager` is a throwaway client preview entity; the real villager's
    // identity is this UUID. Key all life lookups/requests on it, not getId().
    @Shadow(remap = false) @Final protected java.util.UUID villagerUUID;
    // Bottom-anchored "Done" button; must stay put when the life-stage row shift runs.
    @Shadow(remap = false) private ButtonWidget doneWidget;

    @Shadow(remap = false) protected abstract void setPage(String page);

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
    @Unique private boolean townstead$lifeBuilt;
    @Unique private String townstead$lifeSig;

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
        boolean hungerAvailable = TownsteadConfig.isVillagerHungerEnabled();
        boolean thirstAvailable = ThirstBridgeResolver.isActive() && TownsteadConfig.isVillagerThirstEnabled();
        boolean fatigueAvailable = TownsteadConfig.isVillagerFatigueEnabled();
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

        // Position below the mood control (last widget on debug page).
        // Rows flow top-down, skipping disabled systems so there are no gaps.
        int rowY = height / 2 - 80 + 130;
        int hungerY = rowY;
        int bw = 22;
        int dataWidth = 175;

        if (hungerAvailable) {
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
            rowY += 24;
        }
        int thirstY = rowY;
        if (thirstAvailable) rowY += 24;
        int fatigueY = rowY;
        if (fatigueAvailable) rowY += 24;

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
        if (fatigueAvailable) {
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
        }

        // Register callback: when server sync arrives, update the display
        // (only if user hasn't manually edited yet)
        if (hungerAvailable) {
            HungerClientStore.setOnChange(() -> {
                if (!townstead$hungerDirty && townstead$hungerDisplay != null && "debug".equals(this.page)) {
                    townstead$editorHunger = HungerClientStore.get(villager.getId());
                    townstead$hungerDisplay.setMessage(townstead$hungerLabel());
                }
            });
        }
        if (thirstAvailable) {
            ThirstClientStore.setOnChange(() -> {
                if (!townstead$thirstDirty && townstead$thirstDisplay != null && "debug".equals(this.page)) {
                    townstead$editorThirst = townstead$getCurrentThirst();
                    townstead$thirstDisplay.setMessage(townstead$thirstLabel());
                }
            });
        }

        if (fatigueAvailable) {
            FatigueClientStore.setOnChange(() -> {
                if (!townstead$fatigueDirty && townstead$fatigueDisplay != null && "debug".equals(this.page)) {
                    townstead$editorFatigue = townstead$getCurrentFatigue();
                    townstead$fatigueDisplay.setMessage(townstead$fatigueLabel());
                }
            });
        }

        // Request fresh data from server
        //? if neoforge {
        if (hungerAvailable) {
            PacketDistributor.sendToServer(new HungerSetPayload(villager.getId(), -1));
        }
        if (thirstAvailable) {
            PacketDistributor.sendToServer(new ThirstSetPayload(villager.getId(), -1));
        }
        if (fatigueAvailable) {
            PacketDistributor.sendToServer(new FatigueSetPayload(villager.getId(), -1));
        }
        //?} else if forge {
        /*if (hungerAvailable) {
            TownsteadNetwork.sendToServer(new HungerSetPayload(villager.getId(), -1));
        }
        if (thirstAvailable) {
            TownsteadNetwork.sendToServer(new ThirstSetPayload(villager.getId(), -1));
        }
        if (fatigueAvailable) {
            TownsteadNetwork.sendToServer(new FatigueSetPayload(villager.getId(), -1));
        }
        *///?}
    }

    @Inject(method = "setPage", remap = false, at = @At("TAIL"))
    private void townstead$addLifeStageControls(String page, CallbackInfo ci) {
        townstead$lifeBuilt = false;
        if ("general".equals(page)) {
            // The tracking-start push is unreliable for villagers loaded after login,
            // so pull a fresh snapshot from the server on open and rebuild the page
            // once it lands (the General slider swap is structural, not just a label).
            townstead$requestLifeSync();
            LifeClientStore.setOnChange(townstead$onLifeSyncArrived());
        } else {
            LifeClientStore.clearOnChange();
        }

        LifeClientStore.Snapshot snap = LifeClientStore.get(villager.getId());
        if (snap == null || !snap.hasCycle()) return;
        if ("general".equals(page)) {
            townstead$replaceAgeSlider(snap);
        }
        townstead$lifeBuilt = true;
        townstead$lifeSig = townstead$lifeSig(snap);
    }

    @Unique
    private void townstead$requestLifeSync() {
        //? if neoforge {
        PacketDistributor.sendToServer(
                new com.aetherianartificer.townstead.calendar.VillagerLifeRequestC2SPayload(villager.getId(), villagerUUID));
        //?} else if forge {
        /*TownsteadNetwork.sendToServer(
                new com.aetherianartificer.townstead.calendar.VillagerLifeRequestC2SPayload(villager.getId(), villagerUUID));
        *///?}
    }

    @Unique
    private Runnable townstead$onLifeSyncArrived() {
        return () -> {
            if (this.minecraft == null || this.minecraft.screen != this) return;
            if (!"general".equals(this.page)) return;
            LifeClientStore.Snapshot snap = LifeClientStore.get(villager.getId());
            if (snap == null || !snap.hasCycle()) return;
            // Rebuild only when the authoritative sync changes what we'd display (especially the
            // frozen-vs-mortal control). An equal signature means no change, so we don't loop on the
            // re-request our own rebuild fires. This fixes the stale-cache case where the first build
            // showed the wrong control and the old guard then blocked the corrective rebuild.
            if (townstead$lifeSig(snap).equals(townstead$lifeSig)) return;
            setPage(this.page); // rebuild with the changed data
        };
    }

    /** Signature of the snapshot fields that decide which life control is built and its start value. */
    @Unique
    private static String townstead$lifeSig(LifeClientStore.Snapshot snap) {
        if (snap == null) return "";
        return (snap.immortal() ? "I" : "") + (snap.ageless() ? "A" : "")
                + ":" + snap.currentStageIndex() + ":" + snap.bioAgeDays() + ":" + snap.stageCount();
    }

    @Unique
    private void townstead$replaceAgeSlider(LifeClientStore.Snapshot snap) {
        // MCA's age slider is the only AbstractSliderButton on the general page;
        // match the vanilla base so this is independent of the MCA fork's widget class.
        AbstractSliderButton mcaSlider = null;
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractSliderButton asb) {
                mcaSlider = asb;
                break;
            }
        }
        if (mcaSlider == null) return; // self-edit hides the age slider; nothing to replace

        int sx = mcaSlider.getX();
        int sy = mcaSlider.getY();
        int sw = mcaSlider.getWidth();
        int sh = mcaSlider.getHeight();
        removeWidget(mcaSlider);

        // Stage-frozen (immortal OR ageless, e.g. skeletons) gets the frozen-stage picker; everyone
        // else gets the bio-age slider. Ageless villagers ignore birth, so the bio-age slider would
        // be a no-op for them.
        if (snap.immortal() || snap.ageless()) townstead$buildImmortalLife(snap, sx, sy, sw, sh);
        else townstead$buildMortalLife(snap, sx, sy, sw, sh);
    }

    // Width reserved at the right edge of the stage bar for the read-only age field.
    @Unique private static final int TOWNSTEAD_AGE_W = 52;
    // One extra row to shift MCA's family/UUID fields down by, freeing the DOB row.
    @Unique private static final int TOWNSTEAD_LIFE_SHIFT = 26;

    @Unique
    private void townstead$buildMortalLife(LifeClientStore.Snapshot snap, int sx, int sy, int sw, int sh) {
        CalendarClientStore.Snapshot cal = CalendarClientStore.get();
        int sliderW = Math.max(40, sw - TOWNSTEAD_AGE_W - 4);

        Button ageField = addRenderableWidget(Button.builder(Component.empty(), b -> {})
                .pos(sx + sw - TOWNSTEAD_AGE_W, sy).size(TOWNSTEAD_AGE_W, sh).build());
        int[] ymd = townstead$seedYmd(snap, cal);
        int total = snap.totalDays();
        // Apparent ("narrative") years for the current biological age, mirroring the
        // inspect screen. Boxed so the slider/DOB callbacks can both refresh it.
        int[] bioRef = {0};
        Runnable ageRefresh = () -> ageField.setMessage(Component.translatable(
                "townstead.life_stage.age_short", Math.round(snap.narrativeAgeForBio(bioRef[0]))));

        // Initial biological age: an in-progress slider edit wins, else the snapshot.
        int startBio = Math.max(0, Math.min(
                villagerData.contains(LifeData.EDITOR_KEY_BIO_AGE_DAYS)
                        ? villagerData.getInt(LifeData.EDITOR_KEY_BIO_AGE_DAYS)
                        : snap.bioAgeDays(), total));

        // The slider is the SOLE age control: it stamps biological age only and never
        // touches the birthday month/day. The editable Month/Day row below sets only the
        // celebrated birthday and never touches age. The two are fully decoupled.
        LifeAgeSlider slider = new LifeAgeSlider(sx, sy, sliderW, sh,
                townstead$sliderValueForBio(snap, startBio),
                v -> townstead$lifeReadout(snap, townstead$bioForSliderValue(snap, v)),
                v -> {
                    int bio = townstead$bioForSliderValue(snap, v);
                    bioRef[0] = bio;
                    villagerData.putInt(LifeData.EDITOR_KEY_BIO_AGE_DAYS, bio);
                    ageRefresh.run();
                    townstead$previewAge(townstead$modelAgeForBio(snap, bio),
                            LifeStageScale.interpolate(snap.stageScales(), snap.stageDays(), bio));
                    SeniorHairDesat.setPreviewProgress(villager.getId(), snap.seniorProgressForBio(bio));
                });
        addRenderableWidget(slider);
        int bio0 = townstead$bioForSliderValue(snap, slider.sliderValue());
        bioRef[0] = bio0;
        townstead$previewAge(townstead$modelAgeForBio(snap, bio0),
                LifeStageScale.interpolate(snap.stageScales(), snap.stageDays(), bio0));
        SeniorHairDesat.setPreviewProgress(villager.getId(), snap.seniorProgressForBio(bio0));
        ageRefresh.run();

        // Editable "< Month > < Day >" birthday row — celebrated date only, decoupled
        // from the slider/age. Writes EDITOR_KEY_BIRTH_MONTH/DAY (no year, no bio-age).
        townstead$shiftBelow(sy + sh + 2, TOWNSTEAD_LIFE_SHIFT);
        townstead$buildBirthdayRow(sx, sy + sh + 4, sw, ymd, cal);
    }

    /**
     * Editable {@code < Month > < Day >} celebrated-birthday row. Independent of age:
     * writes only {@link LifeData#EDITOR_KEY_BIRTH_MONTH}/{@code _DAY}, never bio-age.
     * {@code ymd[1]}/{@code [2]} are the celebrated month/day; {@code ymd[0]} (year) is
     * only used to look up month names/lengths and is never written as a birth year.
     */
    @Unique
    private void townstead$buildBirthdayRow(int x, int y, int w, int[] ymd, CalendarClientStore.Snapshot cal) {
        int half = w / 2;
        int bw = 14;
        Button mDisp = addRenderableWidget(Button.builder(Component.empty(), b -> {})
                .pos(x + bw, y).size(half - bw * 2, 20).build());
        Button dDisp = addRenderableWidget(Button.builder(Component.empty(), b -> {})
                .pos(x + half + bw, y).size(w - half - bw * 2, 20).build());

        Runnable display = () -> {
            if (cal != null) {
                int mc = Math.max(1, cal.monthsForYear(ymd[0]).size());
                ymd[1] = Math.max(1, Math.min(ymd[1], mc));
                ymd[2] = Math.max(1, Math.min(ymd[2], Math.max(1, cal.daysInMonth(ymd[0], ymd[1]))));
                mDisp.setMessage(cal.monthsForYear(ymd[0]).get(ymd[1] - 1).commonName());
            } else {
                mDisp.setMessage(Component.literal(String.format("%02d", ymd[1])));
            }
            dDisp.setMessage(Component.literal(String.valueOf(ymd[2])));
        };
        Runnable commit = () -> {
            display.run();
            villagerData.putInt(LifeData.EDITOR_KEY_BIRTH_MONTH, ymd[1]);
            villagerData.putInt(LifeData.EDITOR_KEY_BIRTH_DAY, ymd[2]);
        };
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            int mc = cal == null ? 12 : Math.max(1, cal.monthsForYear(ymd[0]).size());
            ymd[1] = ymd[1] <= 1 ? mc : ymd[1] - 1;
            commit.run();
        }).pos(x, y).size(bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            int mc = cal == null ? 12 : Math.max(1, cal.monthsForYear(ymd[0]).size());
            ymd[1] = ymd[1] >= mc ? 1 : ymd[1] + 1;
            commit.run();
        }).pos(x + half - bw, y).size(bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            int md = cal == null ? 31 : Math.max(1, cal.daysInMonth(ymd[0], ymd[1]));
            ymd[2] = ymd[2] <= 1 ? md : ymd[2] - 1;
            commit.run();
        }).pos(x + half, y).size(bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            int md = cal == null ? 31 : Math.max(1, cal.daysInMonth(ymd[0], ymd[1]));
            ymd[2] = ymd[2] >= md ? 1 : ymd[2] + 1;
            commit.run();
        }).pos(x + w - bw, y).size(bw, 20).build());
        display.run();
    }

    @Unique
    private void townstead$buildImmortalLife(LifeClientStore.Snapshot snap, int sx, int sy, int sw, int sh) {
        CalendarClientStore.Snapshot cal = CalendarClientStore.get();
        int sliderW = Math.max(40, sw - TOWNSTEAD_AGE_W - 4);

        Button ageField = addRenderableWidget(Button.builder(Component.empty(), b -> {})
                .pos(sx + sw - TOWNSTEAD_AGE_W, sy).size(TOWNSTEAD_AGE_W, sh).build());
        int[] ymd = townstead$seedYmd(snap, cal);
        // Immortal: the bar picks the frozen biological appearance (a stage); the age
        // field shows the apparent age of that frozen stage, not the calendar age.
        int n = Math.max(1, snap.stageCount());
        int[] idxRef = {0};
        Runnable ageRefresh = () -> ageField.setMessage(Component.translatable(
                "townstead.life_stage.age_short", Math.round(snap.narrativeAgeAtIndex(idxRef[0]))));

        int start = Math.max(0, Math.min(
                villagerData.contains(LifeData.EDITOR_KEY_FROZEN_STAGE_INDEX)
                        ? villagerData.getInt(LifeData.EDITOR_KEY_FROZEN_STAGE_INDEX)
                        : snap.currentStageIndex(), n - 1));
        idxRef[0] = start;
        double init = n <= 1 ? 0.0 : (double) start / (n - 1);
        LifeAgeSlider slider = new LifeAgeSlider(sx, sy, sliderW, sh, init,
                v -> townstead$frozenReadout(snap, n <= 1 ? 0 : (int) Math.round(v * (n - 1))),
                v -> {
                    int idx = n <= 1 ? 0 : (int) Math.round(v * (n - 1));
                    idxRef[0] = idx;
                    villagerData.putInt(LifeData.EDITOR_KEY_FROZEN_STAGE_INDEX, idx);
                    ageRefresh.run();
                    townstead$previewAge(townstead$modelAgeAtIndex(snap, idx), townstead$scaleAtIndex(snap, idx));
                    SeniorHairDesat.setPreviewProgress(villager.getId(), townstead$frozenSeniorProgress(snap, idx));
                });
        addRenderableWidget(slider);
        townstead$previewAge(townstead$modelAgeAtIndex(snap, start), townstead$scaleAtIndex(snap, start));
        SeniorHairDesat.setPreviewProgress(villager.getId(), townstead$frozenSeniorProgress(snap, start));
        ageRefresh.run();

        townstead$shiftBelow(sy + sh + 2, TOWNSTEAD_LIFE_SHIFT);
        townstead$buildBirthdayRow(sx, sy + sh + 4, sw, ymd, cal);
    }

    @Unique
    private void townstead$shiftBelow(int below, int dy) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof AbstractWidget w && w != doneWidget && w.getY() >= below) w.setY(w.getY() + dy);
        }
    }

    @Unique
    private int[] townstead$seedYmd(LifeClientStore.Snapshot snap, CalendarClientStore.Snapshot cal) {
        return new int[]{
                villagerData.contains(LifeData.EDITOR_KEY_BIRTH_YEAR)
                        ? villagerData.getInt(LifeData.EDITOR_KEY_BIRTH_YEAR) : snap.birthYear(),
                Math.max(1, villagerData.contains(LifeData.EDITOR_KEY_BIRTH_MONTH)
                        ? villagerData.getInt(LifeData.EDITOR_KEY_BIRTH_MONTH) : snap.birthMonthIndex()),
                Math.max(1, villagerData.contains(LifeData.EDITOR_KEY_BIRTH_DAY)
                        ? villagerData.getInt(LifeData.EDITOR_KEY_BIRTH_DAY) : snap.birthDayOfMonth())
        };
    }

    /**
     * Equidistant slider: each life stage owns an equal slice of the bar, so the
     * tiny childhood stages are as draggable as the long adult one. Maps a 0..1
     * position to a biological age in days (piecewise-linear within each stage).
     */
    @Unique
    private int townstead$bioForSliderValue(LifeClientStore.Snapshot snap, double v) {
        int[] days = snap.stageDays();
        int n = (days == null) ? 0 : days.length;
        if (n == 0) return 0;
        double scaled = Math.max(0.0, Math.min(n, v * n));
        int idx = Math.min(n - 1, (int) Math.floor(scaled));
        double f = Math.max(0.0, Math.min(1.0, scaled - idx));
        int cum = 0;
        for (int i = 0; i < idx; i++) cum += Math.max(0, days[i]);
        return cum + (int) Math.round(f * Math.max(0, days[idx]));
    }

    /** Inverse of {@link #townstead$bioForSliderValue}: slider position for a biological age. */
    @Unique
    private double townstead$sliderValueForBio(LifeClientStore.Snapshot snap, int bio) {
        int[] days = snap.stageDays();
        int n = (days == null) ? 0 : days.length;
        if (n == 0) return 0.0;
        int idx = Math.max(0, snap.stageIndexForBioAge(bio));
        int cum = 0;
        for (int i = 0; i < idx; i++) cum += Math.max(0, days[i]);
        double f = Math.max(0.0, Math.min(1.0, (bio - cum) / (double) Math.max(1, days[idx])));
        return (idx + f) / n;
    }

    /**
     * Drive the preview model's age from the slider position the same way MCA's
     * own age slider does, so dragging smoothly scales the rendered villager.
     */
    @Unique
    private void townstead$previewAge(int mcaAge, float stageScale) {
        // Age the preview model to match the CURRENT STAGE (not a linear sweep), so
        // the rendered proportions track the stage label. Plus the stage size override.
        villager.setAge(mcaAge);
        LifeStageScale.setPreviewOverride(villager.getId(), stageScale);
        villager.refreshDimensions(); // MCA's slider does this too; without it the model won't rescale
    }

    /** MCA model-age for a biological age, interpolated across the cycle's stages. */
    @Unique
    private int townstead$modelAgeForBio(LifeClientStore.Snapshot snap, int bio) {
        int[] ages = snap.stageModelAges();
        int[] days = snap.stageDays();
        if (ages == null || ages.length == 0) return 0;
        int cum = 0;
        for (int i = 0; i < ages.length; i++) {
            int d = Math.max(1, (days != null && i < days.length) ? days[i] : 1);
            if (bio < cum + d || i == ages.length - 1) {
                float frac = Math.max(0f, Math.min(1f, (bio - cum) / (float) d));
                int a = ages[i];
                int b = (i + 1 < ages.length) ? ages[i + 1] : ages[i];
                return townstead$offBoundary(Math.round(a + (b - a) * frac), a, b);
            }
            cum += d;
        }
        return ages[ages.length - 1];
    }

    /** Immortal preview: full grey when the frozen stage is the senior stage, else none. */
    @Unique
    private float townstead$frozenSeniorProgress(LifeClientStore.Snapshot snap, int idx) {
        return snap.seniorStageIndex() >= 0 && idx == snap.seniorStageIndex() ? 1f : 0f;
    }

    /** Immortal: show the stage's mid appearance, off the AgeState boundary. */
    @Unique
    private int townstead$modelAgeAtIndex(LifeClientStore.Snapshot snap, int idx) {
        int[] ages = snap.stageModelAges();
        if (ages == null || idx < 0 || idx >= ages.length) return 0;
        int a = ages[idx];
        int b = (idx + 1 < ages.length) ? ages[idx + 1] : ages[idx];
        return (a + b) / 2;
    }

    /**
     * Keep a model age a hair inside its AgeState band. MCA's per-age dimension
     * interpolation spikes toward the next stage's size exactly at a band boundary
     * (the representative ages land on those boundaries), so a value sitting on one
     * makes the preview briefly balloon at every stage transition. A small margin
     * off both edges removes the spike without visibly changing the proportions.
     */
    @Unique
    private int townstead$offBoundary(int value, int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        if (hi - lo < 4) return value; // adult/senior collapse: nothing to guard
        int g = Math.max(1, (hi - lo) / 48);
        if (value < lo + g) return lo + g;
        if (value > hi - g) return hi - g;
        return value;
    }

    @Unique
    private float townstead$scaleAtIndex(LifeClientStore.Snapshot snap, int idx) {
        float[] scales = snap.stageScales();
        return (scales != null && idx >= 0 && idx < scales.length) ? scales[idx] : 1f;
    }

    @Unique
    private Component townstead$lifeReadout(LifeClientStore.Snapshot snap, int bioAgeDays) {
        int idx = snap.stageIndexForBioAge(bioAgeDays);
        Component stage = idx >= 0 ? snap.stageLabel(idx)
                : Component.translatable("townstead.life_stage.unknown");
        return Component.translatable("townstead.life_stage.editor_slider", stage);
    }

    @Unique
    private Component townstead$frozenReadout(LifeClientStore.Snapshot snap, int stageIndex) {
        Component stage = stageIndex >= 0 ? snap.stageLabel(stageIndex)
                : Component.translatable("townstead.life_stage.unknown");
        return Component.translatable("townstead.life_stage.editor_frozen", stage);
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
        LifeClientStore.clearOnChange();
        LifeStageScale.clearPreviewOverride(villager.getId());
        SeniorHairDesat.clearPreviewProgress(villager.getId());
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
        return TownsteadVillagers.get(villager).needs().thirst();
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
    private int townstead$getCurrentFatigue() {
        if (FatigueClientStore.hasFatigue(villager.getId())) {
            return FatigueClientStore.getFatigue(villager.getId());
        }
        return TownsteadVillagers.get(villager).needs().fatigue();
    }
}
