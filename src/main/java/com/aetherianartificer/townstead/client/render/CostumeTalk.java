package com.aetherianartificer.townstead.client.render;

import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.aetherianartificer.townstead.replace.WildPlateS2CPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Talking to a costumed mob opens the conversation view with the person it shows, unless the
 * mob is hostile to the player: then it only refuses.
 */
public final class CostumeTalk {
    private CostumeTalk() {}

    /** True when the click was a costumed mob's, handled here. */
    public static boolean open(Entity target) {
        if (!(target instanceof LivingEntity mob)) return false;
        // A sneaking click with something in hand is a gift; the server takes it.
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.isShiftKeyDown() && !client.player.getMainHandItem().isEmpty()) return false;
        VillagerEntityMCA person = WildCostumeRender.personOf(mob);
        if (person == null) return false;
        Minecraft minecraft = Minecraft.getInstance();
        WildPlateS2CPayload plate = WildPlateS2CPayload.of(mob.getId());
        if (plate != null && plate.disposition() == 2) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.townstead.costume.hostile", plate.name()), true);
            }
            return true;
        }
        minecraft.setScreen(new RpgDialogueScreen(person));
        return true;
    }

    /** Whether the speaker is a costumed mob's person rather than a real villager. */
    public static boolean isCostume(Entity speaker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || com.aetherianartificer.townstead.replace.WildCostumeS2CPayload.of(speaker.getId()) == null) return false;
        return !(minecraft.level.getEntity(speaker.getId()) instanceof VillagerEntityMCA);
    }
}
