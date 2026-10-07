package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.compat.hats.HatsCompat;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.conczin.mca.client.gui.InteractScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies on every layout rebuild, including Back and constraint updates, not just initial opening. */
@Mixin(AbstractDynamicScreen.class)
public abstract class VillagerDressMenuMixin extends Screen {
    protected VillagerDressMenuMixin(Component title) { super(title); }

    @Inject(method = "setLayout", remap = false, at = @At("TAIL"))
    private void townstead$configureDressButtons(String layout, CallbackInfo ci) {
        if (!((Object) this instanceof InteractScreen)) return;
        int villager = (Object) this instanceof com.aetherianartificer.townstead.client.persona.PersonaMenuClient.VillagerScreen screen
                ? screen.townstead$villagerEntityId() : -1;
        boolean persona = com.aetherianartificer.townstead.client.persona.PersonaMenuClient.isPersona(villager);
        boolean hats = HatsCompat.present();
        java.util.List<Button> more = new java.util.ArrayList<>();
        for (var child : children()) {
            if (!(child instanceof Button button)
                    || !(button.getMessage().getContents() instanceof TranslatableContents text)) continue;
            String key = text.getKey();
            if ("townstead_more".equals(layout)) more.add(button);
            boolean hidden = switch (key) {
                // More shows only when something is on it.
                case "gui.button.townstead_more" -> !hats && !persona;
                case "gui.button.give_hat" -> !hats;
                case "gui.button.townstead_persona_leave" -> !persona;
                default -> false;
            };
            if (hidden) {
                button.visible = false;
                button.active = false;
            } else if (key.equals("gui.button.armor")) {
                button.setMessage(Component.translatable("gui.townstead.button.auto_armor"));
                button.setTooltip(Tooltip.create(Component.translatable("gui.townstead.button.auto_armor.tooltip")));
            }
        }
        // Close the gaps hidden buttons leave on the More page: the shown ones take the top rows.
        if (more.isEmpty()) return;
        java.util.List<Integer> rows = more.stream().map(Button::getY).sorted().toList();
        java.util.List<Button> shown = more.stream().filter(b -> b.visible)
                .sorted(java.util.Comparator.comparingInt(Button::getY)).toList();
        for (int i = 0; i < shown.size(); i++) shown.get(i).setY(rows.get(i));
    }
}
