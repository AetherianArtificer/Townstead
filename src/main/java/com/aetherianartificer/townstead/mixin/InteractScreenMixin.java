package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import com.aetherianartificer.townstead.mixin.accessor.AbstractDynamicScreenAccessor;
import com.aetherianartificer.townstead.mixin.accessor.InteractScreenAccessor;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(InteractScreen.class)
public abstract class InteractScreenMixin extends Screen {
    private static final ResourceLocation FOOD_FULL = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_full.png");
    private static final ResourceLocation FOOD_HALF = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_half.png");
    private static final ResourceLocation FOOD_EMPTY = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_empty.png");
    private static final int HUNGER_ICON_X = 70;
    private static final int HUNGER_ICON_Y = 120;
    private static final int HUNGER_ICON_SIZE = 24;
    private static final float HUNGER_ICON_SCALE = 16.0f / 9.0f;

    @Shadow @Final private VillagerLike<?> villager;

    private InteractScreenMixin() {
        super(null);
    }

    @Redirect(
            method = "drawTextPopups",
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
        } else if (mca.getVillagerData().getProfession() == VillagerProfession.BUTCHER
                || FarmersDelightCookAssignment.isExternalCookProfession(mca.getVillagerData().getProfession())) {
            tier = Math.max(1, HungerClientStore.getButcherTier(mca.getId()));
        } else {
            return base;
        }
        return base.append(Component.literal(" "))
                .append(Component.translatable("townstead.farmer.tier.inline", tier)
                        .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Inject(method = "drawTextPopups", at = @At("TAIL"))
    private void townstead$drawHungerStatus(GuiGraphics context, CallbackInfo ci) {
        int entityId = villager.asEntity().getId();
        int hunger = HungerClientStore.get(entityId);
        HungerData.HungerState state = HungerData.getState(hunger);

        // Position after traits row (row index 5, using h=17 spacing matching MCA's layout)
        int h = 17;
        int y = 30 + h * 4;

        Activity activity = townstead$getCurrentScheduleActivity();
        if (activity != null) {
            Component scheduleLabel = Component.translatable("townstead.schedule.label",
                            Component.translatable(townstead$activityTranslationKey(activity)))
                    .withStyle(Style.EMPTY.withColor(0x7FB3FF));
            context.renderTooltip(font, scheduleLabel, 10, y);
        }

        if (townstead$isHoveringHungerIcon()) {
            Component hungerLabel = Component.translatable(
                            "townstead.hunger.icon.tooltip",
                            Component.translatable(state.getTranslationKey()),
                            hunger
                    )
                    .withStyle(Style.EMPTY.withColor(state.getColor()));
            ((AbstractDynamicScreenAccessor) this).townstead$invokeDrawHoveringIconText(context, hungerLabel, "hunger");
        }
    }

    @Redirect(
            method = "drawTextPopups",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderTooltip(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;II)V"
            )
    )
    private void townstead$shiftTraitsTooltip(
            GuiGraphics context,
            net.minecraft.client.gui.Font font,
            Component text,
            int x,
            int y
    ) {
        int shiftedY = y;
        if (x == 10 && y == 98 && text.getString().startsWith("Traits")) {
            shiftedY = y + 17;
        }
        context.renderTooltip(font, text, x, shiftedY);
    }

    @Redirect(
            method = "drawTextPopups",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;II)V"
            )
    )
    private void townstead$shiftTraitsComponentTooltip(
            GuiGraphics context,
            net.minecraft.client.gui.Font font,
            List<Component> text,
            int x,
            int y
    ) {
        int shiftedY = y;
        if (x == 10 && y == 98 && !text.isEmpty() && text.get(0).getString().startsWith("Traits")) {
            shiftedY = y + 17;
        }
        context.renderComponentTooltip(font, text, x, shiftedY);
    }

    @Redirect(
            method = "drawTextPopups",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/conczin/mca/client/gui/InteractScreen;hoveringOverText(III)Z"
            )
    )
    private boolean townstead$shiftTraitsHoverHitbox(InteractScreen instance, int x, int y, int w) {
        int shiftedY = y;
        // Traits line moved down by one row, so move its hover hitbox too.
        if (x == 10 && y == 98 && w == 128) {
            shiftedY = y + 17;
        }
        return ((InteractScreenAccessor) instance).townstead$invokeHoveringOverText(x, shiftedY, w);
    }

    @Inject(method = "drawIcons", at = @At("TAIL"))
    private void townstead$drawHungerIcon(GuiGraphics context, CallbackInfo ci) {
        int hunger = HungerClientStore.get(villager.asEntity().getId());
        ResourceLocation sprite = townstead$hungerIconSprite(HungerData.getState(hunger));
        int iconX = HUNGER_ICON_X + ((HUNGER_ICON_SIZE - 16) / 2);
        int iconY = HUNGER_ICON_Y + ((HUNGER_ICON_SIZE - 16) / 2);

        var pose = context.pose();
        pose.pushPose();
        pose.translate(iconX, iconY, 0);
        pose.scale(HUNGER_ICON_SCALE, HUNGER_ICON_SCALE, 1.0f);
        context.blit(sprite, 0, 0, 0, 0, 9, 9, 9, 9);
        pose.popPose();
    }

    private boolean townstead$isHoveringHungerIcon() {
        return ((AbstractDynamicScreenAccessor) this).townstead$invokeHoveringOverIcon("hunger");
    }

    private ResourceLocation townstead$hungerIconSprite(HungerData.HungerState state) {
        return switch (state) {
            case WELL_FED, ADEQUATE -> FOOD_FULL;
            case HUNGRY -> FOOD_HALF;
            case FAMISHED, STARVING -> FOOD_EMPTY;
        };
    }

    private Activity townstead$getCurrentScheduleActivity() {
        if (!(villager.asEntity() instanceof VillagerEntityMCA mca)) return null;
        long dayTime = mca.level().getDayTime() % 24000L;
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
