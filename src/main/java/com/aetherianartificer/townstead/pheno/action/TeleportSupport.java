package com.aetherianartificer.townstead.pheno.action;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Shared placement mechanics for entity- and block-domain teleport actions. */
public final class TeleportSupport {

    private TeleportSupport() {}

    public static boolean teleport(LivingEntity entity, Vec3 target, boolean safe) {
        if (safe) {
            if (entity.randomTeleport(target.x, target.y, target.z, false)) return true;
            for (int dy = 1; dy <= 4; dy++) {
                if (entity.randomTeleport(target.x, target.y + dy, target.z, false)) return true;
                if (entity.randomTeleport(target.x, target.y - dy, target.z, false)) return true;
            }
            return false;
        }
        entity.teleportTo(target.x, target.y, target.z);
        return true;
    }
}
