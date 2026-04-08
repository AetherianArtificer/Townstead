package com.aetherianartificer.townstead.client;

import com.aetherianartificer.townstead.client.gui.dialogue.RpgDialogueScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Keybinds for Townstead's RPG dialogue system.
 */
public final class TownsteadKeybinds {
    public static final KeyMapping TALK = new KeyMapping(
            "townstead.key.talk",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            "townstead.key.category"
    );

    private TownsteadKeybinds() {}

    public static void onClientTick() {
        while (TALK.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.screen != null) continue;

            HitResult hit = mc.hitResult;
            if (hit instanceof EntityHitResult entityHit) {
                Entity entity = entityHit.getEntity();
                if (entity instanceof VillagerLike<?> villager) {
                    mc.setScreen(new RpgDialogueScreen(villager));
                }
            }
        }
    }
}
