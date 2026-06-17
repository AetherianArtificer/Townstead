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
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightBaristaAssignment;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
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
import net.minecraft.world.entity.npc.VillagerProfession;
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
    //? if >=1.21 {
    private static final ResourceLocation FOOD_FULL = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_full.png");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_half.png");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_empty.png");
    //?} else {
    /*private static final ResourceLocation ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    *///?}
    private static final int HUNGER_ICON_X = 70;
    private static final int HUNGER_ICON_Y = 120;
    private static final int HUNGER_ICON_SIZE = 24;
    private static final float HUNGER_ICON_SCALE = 16.0f / 9.0f;
    private static final int THIRST_ICON_X = 70;
    private static final int THIRST_ICON_Y = 145;
    private static final int THIRST_ICON_SIZE = 24;
    private static final float THIRST_ICON_SCALE = 16.0f / 9.0f;
    private static final int FATIGUE_ICON_X = 70;
    private static final int FATIGUE_ICON_Y = 170;
    private static final int FATIGUE_ICON_SIZE = 24;
    private static final float FATIGUE_ICON_SCALE = 16.0f / 9.0f;
    //? if >=1.21 {
    private static final ResourceLocation ENERGY_FULL = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_full.png");
    private static final ResourceLocation ENERGY_THREE_QUARTER = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_three_quarter.png");
    private static final ResourceLocation ENERGY_HALF = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_half.png");
    private static final ResourceLocation ENERGY_QUARTER = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_quarter.png");
    private static final ResourceLocation ENERGY_EMPTY = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_empty.png");
    //?} else {
    /*private static final ResourceLocation ENERGY_FULL = new ResourceLocation("townstead", "textures/gui/energy_full.png");
    private static final ResourceLocation ENERGY_THREE_QUARTER = new ResourceLocation("townstead", "textures/gui/energy_three_quarter.png");
    private static final ResourceLocation ENERGY_HALF = new ResourceLocation("townstead", "textures/gui/energy_half.png");
    private static final ResourceLocation ENERGY_QUARTER = new ResourceLocation("townstead", "textures/gui/energy_quarter.png");
    private static final ResourceLocation ENERGY_EMPTY = new ResourceLocation("townstead", "textures/gui/energy_empty.png");
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
            com.aetherianartificer.townstead.client.gui.origin.HeritageScreen.open(villager);
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
        int tier;
        if (mca.getVillagerData().getProfession() == VillagerProfession.FARMER) {
            tier = Math.max(1, HungerClientStore.getFarmerTier(mca.getId()));
        } else if (FarmersDelightCookAssignment.isExternalCookProfession(mca.getVillagerData().getProfession())) {
            tier = Math.max(1, HungerClientStore.getCookTier(mca.getId()));
        } else if (FarmersDelightBaristaAssignment.isBaristaProfession(mca.getVillagerData().getProfession())) {
            tier = Math.max(1, mca.getVillagerData().getLevel());
        } else {
            return base;
        }
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
                    target = "Lforge/net/mca/client/gui/InteractScreen;hoveringOverText(III)Z"),
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
            int iconX = HUNGER_ICON_X + ((HUNGER_ICON_SIZE - 16) / 2);
            int iconY = HUNGER_ICON_Y + ((HUNGER_ICON_SIZE - 16) / 2);

            pose.pushPose();
            pose.translate(iconX, iconY, 0);
            pose.scale(HUNGER_ICON_SCALE, HUNGER_ICON_SCALE, 1.0f);
            //? if >=1.21 {
            ResourceLocation sprite = townstead$hungerIconSprite(HungerData.getState(hunger));
            context.blit(sprite, 0, 0, 0, 0, 9, 9, 9, 9);
            //?} else {
            /*int u = townstead$hungerIconU(HungerData.getState(hunger));
            context.blit(ICONS, 0, 0, u, 27, 9, 9, 256, 256);
            *///?}
            pose.popPose();
        }

        // Draw energy bolt icon
        if (TownsteadConfig.isVillagerFatigueEnabled()) {
        int fatigue = FatigueClientStore.getFatigue(villager.asEntity().getId());
        FatigueData.FatigueState fatigueState = FatigueData.getState(fatigue);
        int energyIconX = FATIGUE_ICON_X + ((FATIGUE_ICON_SIZE - 16) / 2);
        int energyIconY = FATIGUE_ICON_Y + ((FATIGUE_ICON_SIZE - 16) / 2);

        pose.pushPose();
        pose.translate(energyIconX, energyIconY, 0);
        pose.scale(FATIGUE_ICON_SCALE, FATIGUE_ICON_SCALE, 1.0f);
        ResourceLocation energySprite = townstead$energyIconSprite(fatigueState);
        context.blit(energySprite, 0, 0, 0, 0, 9, 9, 9, 9);
        pose.popPose();
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

    //? if >=1.21 {
    private ResourceLocation townstead$hungerIconSprite(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> FOOD_FULL;
            case HUNGRY -> FOOD_HALF;
            case FAMISHED, STARVING -> FOOD_EMPTY;
        };
    }
    //?} else {
    /*private int townstead$hungerIconU(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> 52;
            case HUNGRY -> 61;
            case FAMISHED, STARVING -> 16;
        };
    }
    *///?}

    private ResourceLocation townstead$energyIconSprite(FatigueData.FatigueState state) {
        return switch (state) {
            case RESTED -> ENERGY_FULL;
            case ALERT -> ENERGY_THREE_QUARTER;
            case TIRED -> ENERGY_HALF;
            case DROWSY -> ENERGY_QUARTER;
            case EXHAUSTED -> ENERGY_EMPTY;
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
