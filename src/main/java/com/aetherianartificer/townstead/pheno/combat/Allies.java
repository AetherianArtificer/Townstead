package com.aetherianartificer.townstead.pheno.combat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who fights on whose side, for summons, dashes and other effects that must not strike friends:
 * the same entity, scoreboard teammates, an owner and what they own (summons and tamed pets), and
 * two things owned by the same owner.
 */
public final class Allies {

    private Allies() {}

    public static boolean areAllied(@Nullable LivingEntity a, @Nullable LivingEntity b) {
        if (a == null || b == null) return false;
        if (a == b || a.isAlliedTo(b)) return true;
        UUID ownerA = ownerOf(a);
        UUID ownerB = ownerOf(b);
        if (ownerA != null && ownerA.equals(b.getUUID())) return true;
        if (ownerB != null && ownerB.equals(a.getUUID())) return true;
        return ownerA != null && ownerA.equals(ownerB);
    }

    /** The summoner or tamer of {@code entity}, or null. */
    public static @Nullable UUID ownerOf(Entity entity) {
        UUID summoner = Minions.ownerId(entity);
        if (summoner != null) return summoner;
        if (entity instanceof OwnableEntity ownable) return ownable.getOwnerUUID();
        return null;
    }
}
