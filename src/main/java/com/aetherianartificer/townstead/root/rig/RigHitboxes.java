package com.aetherianartificer.townstead.root.rig;

import com.aetherianartificer.townstead.calendar.LifeClientStore;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
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
     * Max collision WIDTH any rig's hitbox is clamped to, so a rig can render large yet still path through a
     * 1-block doorway. An open door leaf juts ~3px (0.1875) into the opening, leaving ~0.81 clear, and MCA's
     * village/building navigation assumes a roughly vanilla width; 0.9 could not fit. The on-screen size is a
     * separate client render scale, so this clamp never changes how big the entity looks.
     */
    private static final float DOOR_SAFE_WIDTH = 0.7f;

    /**
     * Max collision HEIGHT any rig's hitbox is clamped to, for the same reason as {@link #DOOR_SAFE_WIDTH}
     * and with the same "renders large, paths normally" split. Two separate rules force this number under
     * 2.0: the collision box has to clear a 2-block doorway outright, and {@code NodeEvaluator.prepare}
     * asks for {@code floor(height + 1)} blocks of vertical clearance on every node, so 1.9 asks for 2 but
     * 2.0 asks for 3 and no path through a normal building is ever found. A rig taller than this still
     * DRAWS at its authored size (render scale is separate); only its collision column is capped, so a
     * tall head clips a door header instead of the villager being stranded in its own house.
     */
    private static final float DOOR_SAFE_HEIGHT = 1.9f;

    private static final boolean DEBUG = false; // flip true to log per-side hitbox resolution (latest.log, townstead/rig)
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("townstead/rig");

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
        boolean client = entity.level().isClientSide;
        RigDefinition.Hitbox box = client ? forClient(living) : forServer(living);
        if (DEBUG && client && entity instanceof VillagerEntityMCA) {
            LOG.info("hitbox CLIENT id={} pose={} -> {}", entity.getId(), pose,
                    box == null ? "NULL (keeps MCA default)" : (box.width() + "x" + box.height()));
        }
        if (box == null) return null;
        // Clamp both axes to stay door-passable (see DOOR_SAFE_WIDTH / DOOR_SAFE_HEIGHT). Visual size is
        // unaffected — it is a separate client render scale. Crouching swaps in the rig's crouch height,
        // which is what makes the pose actually squeeze: without a shorter box, crouching would be a
        // pure animation and the entity would still not fit anywhere new.
        float width = Math.min(box.width(), DOOR_SAFE_WIDTH);
        float standing = Math.min(box.height(), DOOR_SAFE_HEIGHT);
        // Crouching shrinks by the same proportion the clamp already applied, so a rig tall enough to be
        // clamped still gains real headroom by ducking. Taking the raw crouch height instead would clamp
        // it to the same value as standing and the pose would buy nothing.
        float shrink = box.height() > 0f ? standing / box.height() : 1f;
        float height = pose == Pose.CROUCHING ? box.crouchedHeight() * shrink : standing;
        return EntityDimensions.scalable(width, height);
    }

    private static RigDefinition.Hitbox forServer(LivingEntity entity) {
        RigDefinition def = ServerRig.defFor(entity);
        return def == null ? null : def.hitbox();
    }

    private static RigDefinition.Hitbox forClient(LivingEntity entity) {
        String rootId = RootClientStore.resolve(entity);
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null) {
            if (DEBUG) LOG.info("forClient id={} rootId={} -> origin NULL", entity.getId(), rootId);
            return null;
        }
        String rigBase = clientStageRig(entity, origin);
        if (rigBase == null || rigBase.isEmpty()) rigBase = origin.rigBase();
        RigDefinition def = RootCatalogClient.rig(rigBase);
        if (DEBUG) LOG.info("forClient id={} rootId={} rigBase={} def={} hitbox={}", entity.getId(), rootId,
                rigBase, def == null ? "NULL" : "ok", def == null || def.hitbox() == null ? "NULL" : (def.hitbox().width() + "x" + def.hitbox().height()));
        return def == null ? null : def.hitbox();
    }

    /** The current stage's rig override from the synced catalog + client life snapshot, or null. */
    private static String clientStageRig(LivingEntity entity, RootCatalogEntry origin) {
        List<String> rigs = origin.stageRigs();
        if (rigs == null || rigs.isEmpty()) return null;
        LifeClientStore.Snapshot snap = LifeClientStore.get(entity.getId());
        if (snap == null) return null;
        int idx = snap.currentStageIndex();
        return idx >= 0 && idx < rigs.size() ? rigs.get(idx) : null;
    }
}
