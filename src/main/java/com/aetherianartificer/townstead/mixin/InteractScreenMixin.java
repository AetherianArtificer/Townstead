package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.hunger.HungerClientStore;
import com.aetherianartificer.townstead.hunger.HungerData;
import net.conczin.mca.client.gui.InteractScreen;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
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

        HungerData.FarmBlockedReason blocked = HungerClientStore.getFarmBlockedReason(entityId);
        if (blocked != HungerData.FarmBlockedReason.NONE) {
            Component blockedLabel = Component.translatable("townstead.farm.blocked.label",
                            Component.translatable(blocked.translationKey()))
                    .withStyle(Style.EMPTY.withColor(0xFFAA00));
            context.renderTooltip(font, blockedLabel, 10, y + h);
        }
    }
}
