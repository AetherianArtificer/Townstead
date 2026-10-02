package com.aetherianartificer.townstead.culture;

import net.minecraft.resources.ResourceLocation;

/** Resolves a culture-owned settlement name while keeping an already valid choice stable. */
public final class SettlementNaming {
    private SettlementNaming() {}

    public static String resolve(ResourceLocation cultureId, String current) {
        Culture culture = Cultures.get(cultureId);
        SettlementNamePool pool = culture == null
                ? null : SettlementNamePools.get(culture.settlementNames());
        String existing = current == null ? "" : current.trim();
        if (pool == null || pool.contains(existing)) return existing;
        String picked = pool.pick();
        return picked == null || picked.isBlank() ? existing : picked.trim();
    }
}
