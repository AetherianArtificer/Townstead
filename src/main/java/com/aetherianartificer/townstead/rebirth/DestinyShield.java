package com.aetherianartificer.townstead.rebirth;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A player making their character in Destiny cannot defend themselves, so they take no damage
 * from when Destiny opens until it reports closed. The void and /kill still apply.
 */
public final class DestinyShield {
    private static final Set<UUID> OPEN = ConcurrentHashMap.newKeySet();

    private DestinyShield() {}

    public static void opened(ServerPlayer player) {
        OPEN.add(player.getUUID());
    }

    public static void closed(UUID player) {
        OPEN.remove(player);
    }

    public static boolean blocksDamage(Entity entity, DamageSource source) {
        return entity instanceof ServerPlayer player && OPEN.contains(player.getUUID())
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }
}
