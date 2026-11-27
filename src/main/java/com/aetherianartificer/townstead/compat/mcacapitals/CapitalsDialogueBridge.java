package com.aetherianartificer.townstead.compat.mcacapitals;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * MCA Capitals' lines for villagers of an active capital: rank greetings and its capital line pools.
 * Capitals and Townstead both add {@code getTranslatable} to the villager and only one can stay, so
 * Townstead's asks Capitals for a line here.
 */
public final class CapitalsDialogueBridge {

    private static final String SERVICE = "com.majesttyx.mcacapitals.dialogue.CapitalDialogueService";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("townstead/CapitalsDialogueBridge");

    private static Method phraseLine;
    private static boolean phraseLineMissing;

    private CapitalsDialogueBridge() {}

    /** Capitals' line for this phrase, or null to use the usual one. */
    public static @Nullable MutableComponent line(Entity speaker, Player target, String phraseId) {
        if (!(target instanceof ServerPlayer player) || !ModCompat.isLoaded(CapitalsSurnameRegisters.MOD_ID)) return null;
        Method lookup = phraseLookup();
        if (lookup == null) return null;
        try {
            return lookup.invoke(null, player, speaker, phraseId) instanceof Component line ? line.copy() : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Method phraseLookup() {
        if (phraseLine != null || phraseLineMissing) return phraseLine;
        try {
            phraseLine = Class.forName(SERVICE).getMethod("maybeFormatMcaPhraseLine",
                    ServerPlayer.class, Entity.class, String.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            phraseLineMissing = true;
            LOGGER.warn("MCA Capitals has no phrase line lookup; its capital dialogue will not play", e);
        }
        return phraseLine;
    }
}
