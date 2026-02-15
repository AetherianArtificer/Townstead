package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InteractScreen.class)
public abstract class InteractScreenMixin extends Screen {

    @Shadow @Final private VillagerLike<?> villager;

    private InteractScreenMixin() {
        super(null);
    }

    @Inject(method = "drawTextPopups", at = @At("TAIL"))
    private void townstead$drawHungerStatus(GuiGraphics context, CallbackInfo ci) {
        int entityId = villager.asEntity().getId();
        int hunger = HungerClientStore.get(entityId);
        HungerData.HungerState state = HungerData.getState(hunger);

        // Position after traits row (row index 5, using h=17 spacing matching MCA's layout)
        int h = 17;
        int y = 30 + h * 5;

        Component label = Component.translatable("townstead.hunger.label",
                Component.translatable(state.getTranslationKey()))
                .withStyle(Style.EMPTY.withColor(state.getColor()));

        context.renderTooltip(font, label, 10, y);
        int row = 1;

        Activity activity = townstead$getCurrentScheduleActivity();
        if (activity != null) {
            Component scheduleLabel = Component.translatable("townstead.schedule.label",
                            Component.translatable(townstead$activityTranslationKey(activity)))
                    .withStyle(Style.EMPTY.withColor(0x7FB3FF));
            context.renderTooltip(font, scheduleLabel, 10, y + (h * row));
        }
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
