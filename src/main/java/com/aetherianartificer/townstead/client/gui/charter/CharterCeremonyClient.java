package com.aetherianartificer.townstead.client.gui.charter;

import com.aetherianartificer.townstead.client.accessibility.Accessibility;
import com.aetherianartificer.townstead.politics.charter.CharterCeremonyS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;

/** Accessible local rendering for the civic wave; the settlement name is never motion-dependent. */
public final class CharterCeremonyClient {
    private CharterCeremonyClient() {}

    public static void play(CharterCeremonyS2CPayload cue) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        minecraft.gui.setTitle(Component.literal(cue.settlement()));
        minecraft.gui.setSubtitle(Component.translatable("charter.townstead.founded_title"));
        if (Accessibility.effectIntensity() <= 0.0F) return;
        var random = minecraft.level.random;
        for (int radius = 1; radius <= 8; radius++) {
            int points = Math.max(12, radius * 8);
            for (int point = 0; point < points; point++) {
                double angle = Math.PI * 2.0D * point / points;
                minecraft.level.addParticle(ParticleTypes.WAX_ON,
                        cue.bell().getX() + 0.5D + Math.cos(angle) * radius,
                        cue.bell().getY() + 0.15D + random.nextDouble() * 0.08D,
                        cue.bell().getZ() + 0.5D + Math.sin(angle) * radius,
                        0.0D, 0.025D, 0.0D);
            }
        }
    }
}
