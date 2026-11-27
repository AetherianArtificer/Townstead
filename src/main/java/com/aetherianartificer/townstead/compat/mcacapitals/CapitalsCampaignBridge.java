package com.aetherianartificer.townstead.compat.mcacapitals;

import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Capitals' wider guard range for enemies in a campaign. Capitals' own hook cannot reach MCA's current
 * range check, so it lands on a stand-in (see {@code TownsteadMixinPlugin}) and this applies the rule.
 */
public final class CapitalsCampaignBridge {

    private static final double CAMPAIGN_ENEMY_RANGE_SQR = 96.0D * 96.0D;
    private static final Method OPPOSING = lookup();

    private CapitalsCampaignBridge() {}

    public static boolean withinCampaignRange(LivingEntity guard, LivingEntity target) {
        if (OPPOSING == null || !(guard.level() instanceof ServerLevel level)) return false;
        if (guard.distanceToSqr(target) > CAMPAIGN_ENEMY_RANGE_SQR) return false;
        try {
            return Boolean.TRUE.equals(OPPOSING.invoke(null, level, target.getUUID(), guard.getUUID()));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static Method lookup() {
        if (!ModCompat.isLoaded(CapitalsSurnameRegisters.MOD_ID)) return null;
        try {
            return Class.forName("com.majesttyx.mcacapitals.capital.CapitalCampaignService")
                    .getMethod("areOpposingCampaignCombatants", ServerLevel.class, UUID.class, UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
