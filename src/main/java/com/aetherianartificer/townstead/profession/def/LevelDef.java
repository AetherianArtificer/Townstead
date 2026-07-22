package com.aetherianartificer.townstead.profession.def;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One level of a profession, the v2 authoring unit: the optional rank name shown for it, the XP
 * span to complete it (the final level omits this), the skill points granted on reaching it, the
 * trades it unlocks, and the pool of skills it makes available. A profession's level count is
 * simply the length of its {@code levels} array, so tracks past the vanilla five are ordinary.
 */
public record LevelDef(
        @Nullable Component name,
        int xp,
        int skillPoints,
        List<TradeDef> trades,
        List<net.minecraft.resources.ResourceLocation> skills) {
}
