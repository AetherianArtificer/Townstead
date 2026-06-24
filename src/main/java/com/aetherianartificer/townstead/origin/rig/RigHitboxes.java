package com.aetherianartificer.townstead.origin.rig;

import com.aetherianartificer.townstead.calendar.LifeClientStore;
import com.aetherianartificer.townstead.client.origin.OriginCatalogClient;
import com.aetherianartificer.townstead.client.origin.OriginClientStore;
import com.aetherianartificer.townstead.origin.OriginCatalogEntry;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Resolves the {@link RigDefinition.Hitbox} an MCA villager should use from the rig it currently renders
 * as (its life-stage rig override, else its species rig), and turns it into {@link EntityDimensions} for
 * the {@code EntityEvent.Size} hook. Both the server (collision/pathing) and the client (interaction
 * raycast) need it, but they read different stores, so the lookup is split by side: server registries vs.
 * the synced client catalog. The client path touches only rendering-free data classes, so this class is
 * safe to load on a dedicated server (the client branch simply never runs there).
 */
public final class RigHitboxes {

    private RigHitboxes() {}

    /**
     * The dimensions this entity's rig imposes, or null to leave MCA's scale-derived default. Sleeping
     * villagers keep MCA's sleeping box. Non-villagers and rigs without a declared hitbox return null.
     */
    public static EntityDimensions dimensionsFor(Entity entity, Pose pose) {
        if (pose == Pose.SLEEPING) return null;
        // Villagers (life-stage / species rig) and players (origin's species rig) both take their rig's
        // box; any other entity keeps its vanilla dimensions. A player without a hitbox-declaring rig
        // (the default humanoid origins) resolves null below and stays vanilla 0.6 x 1.8.
        if (!(entity instanceof VillagerEntityMCA) && !(entity instanceof Player)) return null;
        if (!(entity instanceof LivingEntity living)) return null;
        RigDefinition.Hitbox box = entity.level().isClientSide ? forClient(living) : forServer(living);
        if (box == null) return null;
        return EntityDimensions.scalable(box.width(), box.height());
    }

    private static RigDefinition.Hitbox forServer(LivingEntity entity) {
        RigDefinition def = ServerRig.defFor(entity);
        return def == null ? null : def.hitbox();
    }

    private static RigDefinition.Hitbox forClient(LivingEntity entity) {
        String originId = OriginClientStore.resolve(entity);
        OriginCatalogEntry origin = OriginCatalogClient.origin(originId);
        if (origin == null) return null;
        String rigBase = clientStageRig(entity, origin);
        if (rigBase == null || rigBase.isEmpty()) rigBase = origin.rigBase();
        RigDefinition def = OriginCatalogClient.rig(rigBase);
        return def == null ? null : def.hitbox();
    }

    /** The current stage's rig override from the synced catalog + client life snapshot, or null. */
    private static String clientStageRig(LivingEntity entity, OriginCatalogEntry origin) {
        List<String> rigs = origin.stageRigs();
        if (rigs == null || rigs.isEmpty()) return null;
        LifeClientStore.Snapshot snap = LifeClientStore.get(entity.getId());
        if (snap == null) return null;
        int idx = snap.currentStageIndex();
        return idx >= 0 && idx < rigs.size() ? rigs.get(idx) : null;
    }
}
