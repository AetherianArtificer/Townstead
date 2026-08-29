package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.client.animation.emote.loader.EmoteReflection;
import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.aetherianartificer.townstead.client.gui.pose.PosePickerScreen;
import com.aetherianartificer.townstead.fatigue.FatigueClientStore;
import com.aetherianartificer.townstead.fatigue.FatigueData;
import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.shift.ShiftClientStore;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.mixin.accessor.AbstractDynamicScreenAccessor;
import com.aetherianartificer.townstead.mixin.accessor.InteractScreenAccessor;
import com.aetherianartificer.townstead.thirst.ThirstClientStore;
import com.aetherianartificer.townstead.thirst.ThirstData;
import net.conczin.mca.client.gui.InteractScreen;
//? if >=1.21 {
import net.conczin.mca.client.gui.MCAButton;
//?} else {
/*import net.conczin.mca.client.gui.Button;
*///?}
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(InteractScreen.class)
public abstract class InteractScreenMixin extends Screen {
    // Icons live in the townstead_icons namespace (assets/townstead_icons/*.png).
    //? if >=1.21 {
    private static final ResourceLocation HUNGER_FULL = ResourceLocation.fromNamespaceAndPath("townstead_icons", "hunger_full.png");
    private static final ResourceLocation HUNGER_HALF = ResourceLocation.fromNamespaceAndPath("townstead_icons", "hunger_half.png");
    private static final ResourceLocation HUNGER_LOW = ResourceLocation.fromNamespaceAndPath("townstead_icons", "hunger_low.png");
    //?} else {
    /*private static final ResourceLocation HUNGER_FULL = new ResourceLocation("townstead_icons", "hunger_full.png");
    private static final ResourceLocation HUNGER_HALF = new ResourceLocation("townstead_icons", "hunger_half.png");
    private static final ResourceLocation HUNGER_LOW = new ResourceLocation("townstead_icons", "hunger_low.png");
    *///?}
    private static final int HUNGER_ICON_X = 70;
    private static final int HUNGER_ICON_Y = 121;
    private static final int HUNGER_ICON_SIZE = 24;
    private static final int THIRST_ICON_X = 70;
    private static final int THIRST_ICON_Y = 145;
    private static final int THIRST_ICON_SIZE = 24;
    private static final float THIRST_ICON_SCALE = 16.0f / 9.0f;
    private static final int FATIGUE_ICON_X = 70;
    private static final int FATIGUE_ICON_Y = 170;
    private static final int FATIGUE_ICON_SIZE = 24;
    // The icon art is a glyph-tight 12px sprite; render it at 24px so it fills the same
    // footprint as MCA's own status icons (16px sprites drawn at 1.5x = 24px). 12 -> 24
    // is a clean 2x.
    private static final int NEED_ICON_TEX = 12;
    private static final int NEED_ICON_PX = 24;
    private static final int HUNGER_ICON_PX = 20;
    private static final int ENERGY_ICON_PX = 18;
    //? if >=1.21 {
    private static final ResourceLocation ENERGY_FULL = ResourceLocation.fromNamespaceAndPath("townstead_icons", "energy_full.png");
    private static final ResourceLocation ENERGY_HALF = ResourceLocation.fromNamespaceAndPath("townstead_icons", "energy_half.png");
    private static final ResourceLocation ENERGY_LOW = ResourceLocation.fromNamespaceAndPath("townstead_icons", "energy_low.png");
    //?} else {
    /*private static final ResourceLocation ENERGY_FULL = new ResourceLocation("townstead_icons", "energy_full.png");
    private static final ResourceLocation ENERGY_HALF = new ResourceLocation("townstead_icons", "energy_half.png");
    private static final ResourceLocation ENERGY_LOW = new ResourceLocation("townstead_icons", "energy_low.png");
    *///?}

    @Shadow(remap = false) @Final private VillagerLike<?> villager;
    @Shadow(remap = false) private int timeSinceLastClick;

    @Unique
    private boolean townstead$transitioning;

    private InteractScreenMixin() {
        super(null);
    }

    //? if >=1.21 {
    @Inject(method = "buttonPressed", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$interceptTalkButton(MCAButton button, CallbackInfo ci) {
        String id = button.identifier();
        if (timeSinceLastClick <= 2) return;
        if ("gui.button.talk".equals(id)) {
            ci.cancel();
            townstead$transitioning = true;
            Minecraft.getInstance().setScreen(new RpgDialogueScreen(villager));
        } else if ("gui.button.pose".equals(id)) {
            ci.cancel();
            townstead$transitioning = true;
            PosePickerScreen.open(villager);
        }
    }
    //?} else {
    /*@Inject(method = "buttonPressed", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$interceptTalkButton(Button button, CallbackInfo ci) {
        String id = button.identifier();
        if (timeSinceLastClick <= 2) return;
        if ("gui.button.talk".equals(id)) {
            ci.cancel();
            townstead$transitioning = true;
            Minecraft.getInstance().setScreen(new RpgDialogueScreen(villager));
        } else if ("gui.button.pose".equals(id)) {
            ci.cancel();
            townstead$transitioning = true;
            PosePickerScreen.open(villager);
        }
    }
    *///?}

    // Click MCA's "genes" icon to open the full read-only Heritage view (the icon's
    // hover still shows MCA's quick gene tooltip). mouseClicked is a vanilla-signature
    // method, so the Forge build targets its SRG name; both pass remap=false.
    //? if >=1.21 {
    @Inject(method = "mouseClicked", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$openHeritageOnGenesClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        townstead$tryOpenHeritage(button, cir);
    }
    //?} else {
    /*@Inject(method = "m_6375_", remap = false, at = @At("HEAD"), cancellable = true)
    private void townstead$openHeritageOnGenesClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        townstead$tryOpenHeritage(button, cir);
    }
    *///?}

    @Unique
    private void townstead$tryOpenHeritage(int button, CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        if (((AbstractDynamicScreenAccessor) this).townstead$invokeHoveringOverIcon("genes")) {
            townstead$transitioning = true;
            com.aetherianartificer.townstead.client.gui.root.HeritageScreen.open(villager);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void townstead$hidePoseButtonWhenNoEmoteSource(CallbackInfo ci) {
        if (EmoteReflection.isAvailable()) return;
        // No emote source loaded — hide Pose so the button doesn't dead-end.
        // Quark/posing parity will go here when its loader is added.
        for (GuiEventListener listener : this.children()) {
            if (!(listener instanceof net.minecraft.client.gui.components.Button button)) continue;
            if (!(button.getMessage().getContents() instanceof TranslatableContents tc)) continue;
            if (!"gui.button.pose".equals(tc.getKey())) continue;
            button.visible = false;
            button.active = false;
        }
    }

    @Inject(method = "onClose", at = @At("HEAD"), cancellable = true)
    private void townstead$suppressCloseOnTransition(CallbackInfo ci) {
        if (townstead$transitioning) {
            townstead$transitioning = false;
            ci.cancel();
        }
    }

    @Redirect(
            method = "drawTextPopups",
            remap = false,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/conczin/mca/entity/VillagerLike;getProfessionText()Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent townstead$professionWithTier(VillagerLike<?> villagerLike) {
        MutableComponent base = villagerLike.getProfessionText().copy();
        if (!(villagerLike.asEntity() instanceof VillagerEntityMCA mca)) return base;
        int tier = HungerClientStore.getCareerTier(mca.getId());
        if (tier <= 0) return base;
        String levelKey = "townstead.profession.level." + Math.min(tier, 5);
        return base.append(Component.literal(" "))
                .append(Component.translatable(levelKey)
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    // Show a custom (data-pack) personality's name/description instead of the base MCA enum it reskins,
    // resolved server-side and carried on the life sync. Personality.getName/getDescription are MCA
    // methods (no remap), so one target covers both stonecutter versions.
    @Redirect(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/conczin/mca/entity/ai/relationship/Personality;getName()Lnet/minecraft/network/chat/Component;"))
    private Component townstead$personalityName(net.conczin.mca.entity.ai.relationship.Personality personality) {
        com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot life =
                com.aetherianartificer.townstead.calendar.LifeClientStore.get(villager.asEntity().getId());
        return life != null && life.hasCustomPersonality()
                ? Component.literal(life.personalityName())
                : personality.getName();
    }

    @Redirect(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/conczin/mca/entity/ai/relationship/Personality;getDescription()Lnet/minecraft/network/chat/Component;"))
    private Component townstead$personalityDescription(net.conczin.mca.entity.ai.relationship.Personality personality) {
        com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot life =
                com.aetherianartificer.townstead.calendar.LifeClientStore.get(villager.asEntity().getId());
        return life != null && life.personalityDesc() != null && !life.personalityDesc().isEmpty()
                ? Component.literal(life.personalityDesc())
                : personality.getDescription();
    }

    // Show the data-pack life stage's label (e.g. "Egg") instead of MCA's canonical AgeState name
    // (e.g. "Toddler") for a villager at a custom stage. AgeState.getName is an MCA method (no remap),
    // so one target covers both stonecutter versions; only the baby branch of drawTextPopups calls it.
    @Redirect(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE",
                    target = "Lnet/conczin/mca/entity/ai/relationship/AgeState;getName()Lnet/minecraft/network/chat/Component;"))
    private Component townstead$stageName(net.conczin.mca.entity.ai.relationship.AgeState ageState) {
        com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot life =
                com.aetherianartificer.townstead.calendar.LifeClientStore.get(villager.asEntity().getId());
        if (life != null) {
            int idx = life.currentStageIndex();
            if (idx >= 0) {
                Component label = life.stageLabel(idx);
                if (label != null && !label.getString().isEmpty()) return label;
            }
        }
        return ageState.getName();
    }

    @Inject(method = "drawTextPopups", remap = false, at = @At("TAIL"))
    private void townstead$drawHungerStatus(GuiGraphics context, CallbackInfo ci) {
        int entityId = villager.asEntity().getId();
        int hunger = HungerClientStore.get(entityId);
        HungerData.HungerState state = HungerData.getState(hunger);

        int h = 17;
        int y = 30 + h * 4;

        Activity activity = townstead$getCurrentScheduleActivity();
        if (activity != null) {
            Component scheduleLabel = Component.translatable("townstead.schedule.label",
                            Component.translatable(townstead$activityTranslationKey(activity)))
                    .withStyle(Style.EMPTY.withColor(0x7FB3FF));
            context.renderTooltip(font, scheduleLabel, 10, y);
        }

        if (TownsteadConfig.isVillagerHungerEnabled() && townstead$isHoveringHungerIcon()) {
            Component hungerLabel = Component.translatable(
                            "townstead.hunger.icon.tooltip",
                            Component.translatable(state.getTranslationKey()),
                            hunger
                    )
                    .withStyle(Style.EMPTY.withColor(state.getColor()));
            ((AbstractDynamicScreenAccessor) this).townstead$invokeDrawHoveringIconText(context, hungerLabel, "hunger");
        }

        if (ThirstBridgeResolver.isActive() && TownsteadConfig.isVillagerThirstEnabled() && townstead$isHoveringThirstIcon()) {
            int thirst = ThirstClientStore.getThirst(entityId);
            ThirstData.ThirstState thirstState = ThirstData.getState(thirst);
            Component thirstLabel = Component.translatable(
                            "townstead.thirst.icon.tooltip",
                            thirst
                    )
                    .withStyle(Style.EMPTY.withColor(thirstState.getColor()));
            ((AbstractDynamicScreenAccessor) this).townstead$invokeDrawHoveringIconText(context, thirstLabel, "thirst");
        }

        if (TownsteadConfig.isVillagerFatigueEnabled() && townstead$isHoveringFatigueIcon()) {
            int fatigue = FatigueClientStore.getFatigue(entityId);
            int energy = FatigueData.toEnergy(fatigue);
            FatigueData.FatigueState fatigueState = FatigueData.getState(fatigue);
            Component energyLabel = Component.translatable(
                            "townstead.energy.icon.tooltip",
                            Component.translatable(fatigueState.getTranslationKey()),
                            energy
                    )
                    .withStyle(Style.EMPTY.withColor(fatigueState.getColor()));
            context.renderTooltip(font, energyLabel, FATIGUE_ICON_X + 16, FATIGUE_ICON_Y + 20);
        }
    }

    private static final int TRAITS_Y = 30 + 17 * 4; // 98

    //? if >=1.21 {
    @ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V"),
            index = 3)
    //?} else {
    /*@ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;m_280557_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V"),
            index = 3)
    *///?}
    private int townstead$shiftTraitsTooltipY(int y) {
        return y >= TRAITS_Y ? y + 17 : y;
    }

    //? if >=1.21 {
    @ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            index = 3)
    //?} else {
    /*@ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;m_280666_(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"),
            index = 3)
    *///?}
    private int townstead$shiftTraitsComponentTooltipY(int y) {
        return y >= TRAITS_Y ? y + 17 : y;
    }

    //? if >=1.21 {
    @ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/conczin/mca/client/gui/InteractScreen;hoveringOverText(III)Z"),
            index = 1)
    //?} else {
    /*@ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/conczin/mca/client/gui/InteractScreen;hoveringOverText(III)Z"),
            index = 1)
    *///?}
    private int townstead$shiftTraitsHoverY(int y) {
        return y >= TRAITS_Y ? y + 17 : y;
    }

    @Inject(method = "drawIcons", remap = false, at = @At("TAIL"))
    private void townstead$drawHungerIcon(GuiGraphics context, CallbackInfo ci) {
        var pose = context.pose();
        if (TownsteadConfig.isVillagerHungerEnabled()) {
            int hunger = HungerClientStore.get(villager.asEntity().getId());
            ResourceLocation sprite = townstead$hungerIconSprite(HungerData.getState(hunger));
            townstead$drawNeedIcon(context, sprite, HUNGER_ICON_X, HUNGER_ICON_Y, HUNGER_ICON_PX);
        }

        // Draw energy bolt icon
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
            int fatigue = FatigueClientStore.getFatigue(villager.asEntity().getId());
            FatigueData.FatigueState fatigueState = FatigueData.getState(fatigue);
            ResourceLocation energySprite = townstead$energyIconSprite(fatigueState);
            townstead$drawNeedIcon(context, energySprite, FATIGUE_ICON_X, FATIGUE_ICON_Y, ENERGY_ICON_PX);
        }

        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null || !TownsteadConfig.isVillagerThirstEnabled()) return;

        int thirst = ThirstClientStore.getThirst(villager.asEntity().getId());
        ThirstCompatBridge.ThirstIconInfo icon = bridge.iconInfo(thirst);
        int thirstX = THIRST_ICON_X + ((THIRST_ICON_SIZE - 16) / 2);
        int thirstY = THIRST_ICON_Y + ((THIRST_ICON_SIZE - 16) / 2);

        pose.pushPose();
        pose.translate(thirstX, thirstY, 0);
        pose.scale(THIRST_ICON_SCALE, THIRST_ICON_SCALE, 1.0f);
        context.blit(icon.texture(), 0, 0, icon.u(), icon.v(), 9, 9, icon.texW(), icon.texH());
        pose.popPose();
    }

    // Draw a 16px icon sprite at MCA's icon footprint (1.5x -> 24px) with its origin at
    // the given screen slot, so our need icons match the size of MCA's own status icons.
    // Renders the 12px icon sprite at displayPx, centered within MCA's 24px icon slot at
    // (slotX, slotY). Hunger fills the slot; the battery is nudged a little smaller.
    @Unique
    private void townstead$drawNeedIcon(GuiGraphics context, ResourceLocation sprite, int slotX, int slotY, int displayPx) {
        float scale = (float) displayPx / NEED_ICON_TEX;
        int inset = (NEED_ICON_PX - displayPx) / 2;
        var pose = context.pose();
        pose.pushPose();
        pose.scale(scale, scale, 1.0f);
        context.blit(sprite, Math.round((slotX + inset) / scale), Math.round((slotY + inset) / scale),
                0, 0, NEED_ICON_TEX, NEED_ICON_TEX, NEED_ICON_TEX, NEED_ICON_TEX);
        pose.popPose();
    }

    private boolean townstead$isHoveringHungerIcon() {
        return ((AbstractDynamicScreenAccessor) this).townstead$invokeHoveringOverIcon("hunger");
    }

    private boolean townstead$isHoveringThirstIcon() {
        return ((AbstractDynamicScreenAccessor) this).townstead$invokeHoveringOverIcon("thirst");
    }

    private boolean townstead$isHoveringFatigueIcon() {
        if (minecraft == null) return false;
        double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        return mx >= FATIGUE_ICON_X && mx <= FATIGUE_ICON_X + FATIGUE_ICON_SIZE
                && my >= FATIGUE_ICON_Y && my <= FATIGUE_ICON_Y + FATIGUE_ICON_SIZE;
    }

    /**
     * Hover-flip on the name chip: swap the Component MCA passes to
     * {@code renderTooltip} for the name (ordinal 1 in {@code drawTextPopups})
     * so the chip natively shows "Born 22 Frostturn 1002 (age 24)" when the
     * cursor is over it. No second chip, no overlay z-fight.
     *
     * Ordinal 0 is MCA's gift-mode chip in the {@code inGiftMode} branch;
     * ordinal 1 is the name chip in the else branch. Targeting ordinal 1
     * leaves gift mode untouched.
     *
     * Hover bounds cover MCA's visible footprint (chip is at roughly
     * x=22..22+textW, y=14..26 because {@code renderTooltip} anchors
     * above-right of the (10, 28) point). Wider Born-text bounds are used
     * so the hover persists once the chip widens after the flip.
     */
    //? if >=1.21 {
    @ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V",
                    ordinal = 1),
            index = 1)
    //?} else {
    /*@ModifyArg(method = "drawTextPopups", remap = false,
            at = @At(value = "INVOKE", remap = false,
                    target = "Lnet/minecraft/client/gui/GuiGraphics;m_280557_(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V",
                    ordinal = 1),
            index = 1)
    *///?}
    private Component townstead$swapNameOnHover(Component originalName) {
        com.aetherianartificer.townstead.calendar.LifeClientStore.Snapshot life =
                com.aetherianartificer.townstead.calendar.LifeClientStore.get(villager.asEntity().getId());
        if (life == null) return originalName;

        // Birthday is shown as "Month Day" — no year (the absolute year confuses
        // players and isn't meaningful next to the apparent age).
        com.aetherianartificer.townstead.calendar.CalendarClientStore.Snapshot calSnap =
                com.aetherianartificer.townstead.calendar.CalendarClientStore.get();
        Component month;
        if (calSnap != null && life.birthMonthIndex() >= 1
                && life.birthMonthIndex() <= calSnap.monthsForYear(life.birthYear()).size()) {
            month = calSnap.monthsForYear(life.birthYear())
                    .get(life.birthMonthIndex() - 1).commonName();
        } else {
            month = life.birthMonthComponent();
            if (month.getString().isEmpty()) {
                month = Component.translatable("townstead.calendar.inspector.month_fallback", life.birthMonthIndex());
            }
        }
        Component date = Component.translatableWithFallback(
                "townstead.calendar.inspector.month_day", "%1$s %2$s", month, life.birthDayOfMonth());
        Component born = Component.translatableWithFallback(
                "townstead.calendar.inspector.born_with_age",
                "Born %1$s (age %2$s)",
                date, Math.round(life.narrativeAgeForBio(life.bioAgeDays())));

        int nameW = font.width(originalName);
        int bornW = font.width(born);
        int hoverLeft = 8;
        int hoverTop = 8;
        int hoverRight = 24 + Math.max(nameW, bornW) + 8;
        int hoverBottom = 30;
        if (!townstead$isHoveringRect(hoverLeft, hoverTop, hoverRight - hoverLeft, hoverBottom - hoverTop)) {
            return originalName;
        }
        return born;
    }

    private boolean townstead$isHoveringRect(int x, int y, int w, int h) {
        if (minecraft == null) return false;
        double mx = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double my = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private ResourceLocation townstead$hungerIconSprite(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> HUNGER_FULL;
            case HUNGRY -> HUNGER_HALF;
            case FAMISHED, STARVING -> HUNGER_LOW;
        };
    }

    private ResourceLocation townstead$energyIconSprite(FatigueData.FatigueState state) {
        return switch (state) {
            case RESTED, ALERT -> ENERGY_FULL;
            case TIRED -> ENERGY_HALF;
            case DROWSY, EXHAUSTED -> ENERGY_LOW;
        };
    }

    private Activity townstead$getCurrentScheduleActivity() {
        if (!(villager.asEntity() instanceof VillagerEntityMCA mca)) return null;
        long dayTime = mca.level().getDayTime() % 24000L;
        // Use custom shift data from the client store if available
        if (ShiftClientStore.has(mca.getUUID())) {
            int tickHour = (int) (dayTime / ShiftData.TICKS_PER_HOUR) % ShiftData.HOURS_PER_DAY;
            int[] shifts = ShiftClientStore.get(mca.getUUID());
            int ord = shifts[tickHour];
            if (ord >= 0 && ord < ShiftData.ORDINAL_TO_ACTIVITY.length) {
                return ShiftData.ORDINAL_TO_ACTIVITY[ord];
            }
        }
        return mca.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private String townstead$activityTranslationKey(Activity activity) {
        if (activity == Activity.WORK) return "townstead.schedule.activity.work";
        if (activity == Activity.REST) return "townstead.schedule.activity.rest";
        if (activity == Activity.PLAY) return "townstead.schedule.activity.play";
        if (activity == Activity.MEET) return "townstead.schedule.activity.meet";
        if (activity == Activity.IDLE) return "townstead.schedule.activity.idle";
        if (activity == Activity.PANIC) return "townstead.schedule.activity.panic";
        if (activity == Activity.PRE_RAID) return "townstead.schedule.activity.pre_raid";
        if (activity == Activity.RAID) return "townstead.schedule.activity.raid";
        if (activity == Activity.HIDE) return "townstead.schedule.activity.hide";
        return "townstead.schedule.activity.unknown";
    }
}
