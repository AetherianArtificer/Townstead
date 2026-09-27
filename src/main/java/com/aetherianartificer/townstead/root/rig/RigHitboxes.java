package com.aetherianartificer.townstead.root.rig;

import com.aetherianartificer.townstead.calendar.LifeClientStore;
import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.root.*;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;

/** Shared final dimensions for collision, pose clearance, navigation and physical eye height. */
public final class RigHitboxes {
    private RigHitboxes() {}
    private static final java.util.Map<LivingEntity, GrowthStamp> LAST_GROWTH =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<LivingEntity, Boolean> MANAGED =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private record GrowthStamp(float scale, String rig) {}
    private static final RigDefinition.Hitbox HUMANOID = new RigDefinition.Hitbox(.6f, 2f, 1.65f);

    public record Inputs(RigDefinition.Hitbox base, float widthScale, float heightScale,
                         float growth, float rigScale, float entityScale) {
        public BodySize resolve(Pose pose) {
            var posture = pose == Pose.CROUCHING ? BodySize.Posture.CROUCHING
                    : pose == Pose.SWIMMING || pose == Pose.FALL_FLYING || pose == Pose.SPIN_ATTACK
                    ? BodySize.Posture.SWIMMING : BodySize.Posture.STANDING;
            return BodySize.resolve(base, (double)widthScale * growth * rigScale * entityScale,
                    (double)heightScale * growth * rigScale * entityScale, posture);
        }
    }

    public static Inputs inputs(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA) && !(entity instanceof Player)) return null;
        // EntityEvent.Size also fires from Entity's constructor, before LivingEntity attributes
        // and MCA genetics exist. Leave that provisional box alone; assignment/tick refresh it.
        if (entity.level() == null || entity.getAttributes() == null) return null;
        if (entity instanceof VillagerEntityMCA villager && villager.getGenetics() == null) return null;
        RigDefinition def = definition(entity);
        RigDefinition.Hitbox box = def == null ? null : def.hitbox();
        // Ordinary players retain vanilla mechanics. MCA villagers get the shared humanoid policy.
        if (box == null && !(entity instanceof VillagerEntityMCA)) return null;
        float width = 1, height = 1, growth = 1;
        if (entity instanceof VillagerEntityMCA villager) {
            width = BodySize.factor(villager.getRawHorizontalScaleFactor());
            //? if neoforge {
            height = BodySize.factor(villager.getRawVerticalScaleFactor());
            //?} else {
            /*height = BodySize.factor(villager.getRawVerticalScaleFactor());
            *///?}
            growth = BodySize.factor(LifeStageScale.forVillager(entity));
        }
        //? if neoforge {
        float entityScale = BodySize.factor(entity.getScale());
        //?} else {
        /*float entityScale = 1f;
        *///?}
        return new Inputs(box == null ? HUMANOID : box, width, height, growth,
                box == null ? 1f : speciesScale(entity), entityScale);
    }

    public static BodySize desired(LivingEntity entity, Pose pose) {
        if (pose == Pose.SLEEPING || pose == Pose.DYING || entity.isPassenger()) return null;
        Inputs in = inputs(entity);
        return in == null ? null : in.resolve(pose);
    }

    public static EntityDimensions dimensionsFor(Entity entity, Pose pose) {
        if (!(entity instanceof LivingEntity living)) return null;
        BodySize size = desired(living, pose);
        if (size == null) return null;
        // Pose-clearance queries see the full requested pose. Expansion of the currently occupied
        // pose may wait for room instead of repeatedly pushing a growing entity through walls.
        if (pose == entity.getPose() && entity.tickCount > 1 && !entity.noPhysics
                && entity.getBbWidth() >= BodySize.MIN_WIDTH && entity.getBbHeight() >= BodySize.MIN_HEIGHT
                && (size.width() > entity.getBbWidth() + .001f || size.height() > entity.getBbHeight() + .001f)
                && !entity.level().noBlockCollision(entity, dimensions(size).makeBoundingBox(entity.position()).deflate(1e-7))) {
            size = size.deferGrowth(entity.getBbWidth(), entity.getBbHeight());
        }
        return dimensions(size);
    }

    private static EntityDimensions dimensions(BodySize size) {
        //? if neoforge {
        return EntityDimensions.scalable(size.width(), size.height()).withEyeHeight(size.eyeHeight());
        //?} else {
        /*return EntityDimensions.scalable(size.width(), size.height());
        *///?}
    }

    public static float eyeHeightFor(LivingEntity entity, Pose pose, float actualHeight) {
        BodySize desired = desired(entity, pose);
        return desired == null ? actualHeight * .85f
                : Math.min(actualHeight - .05f, Math.max(.05f, desired.eyeHeight() * actualHeight / desired.height()));
    }

    /** Vanilla multiplies getDefaultDimensions by the scale attribute AFTER returning (1.21). */
    public static EntityDimensions defaultDimensionsFor(LivingEntity entity, Pose pose) {
        EntityDimensions result = dimensionsFor(entity, pose);
        //? if neoforge {
        return result == null ? null : result.scale(1f / BodySize.factor(entity.getScale()));
        //?} else {
        /*return result;
        *///?}
    }

    /** Refresh only meaningful changes; discard paths calculated for the previous body. */
    public static void tick(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA) && !(entity instanceof Player)) return;
        if ((entity.tickCount + entity.getId()) % 10 != 0) return;
        if (!entity.level().isClientSide && entity instanceof VillagerEntityMCA villager) {
            float growth = LifeStageScale.forVillager(villager);
            GrowthStamp stamp = new GrowthStamp(growth, ServerRig.rigIdFor(villager));
            GrowthStamp previous = LAST_GROWTH.put(entity, stamp);
            if (!stamp.equals(previous)) {
                var payload = com.aetherianartificer.townstead.Townstead.townstead$lifeSync(villager);
                if (payload != null) {
                    //? if neoforge {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
                    //?} else {
                    /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, payload);
                    *///?}
                }
            }
        }
        EntityDimensions next = dimensionsFor(entity, entity.getPose());
        if (next == null) {
            // A reload can remove a rig without changing the entity's pose or root id.
            if (MANAGED.remove(entity) != null) entity.refreshDimensions();
            return;
        }
        MANAGED.put(entity, Boolean.TRUE);
        //? if neoforge {
        float width = next.width(), height = next.height(), eye = next.eyeHeight();
        //?} else {
        /*float width = next.width, height = next.height, eye = eyeHeightFor(entity, entity.getPose(), height);
        *///?}
        if (Math.abs(width - entity.getBbWidth()) > .001f || Math.abs(height - entity.getBbHeight()) > .001f
                || Math.abs(eye - entity.getEyeHeight()) > .001f) {
            entity.refreshDimensions();
            if (!entity.level().isClientSide && entity instanceof Mob mob) mob.getNavigation().recomputePath();
        }
    }

    public static RigDefinition definition(LivingEntity entity) {
        if (!entity.level().isClientSide) return ServerRig.defFor(entity);
        RootCatalogEntry origin = RootCatalogClient.origin(RootClientStore.resolve(entity));
        if (origin == null) return null;
        String rig = origin.rigBase();
        LifeClientStore.Snapshot life = LifeClientStore.get(entity.getId());
        if (life != null && origin.stageRigs() != null && life.currentStageIndex() >= 0
                && life.currentStageIndex() < origin.stageRigs().size()) {
            String stage = origin.stageRigs().get(life.currentStageIndex());
            if (stage != null && !stage.isEmpty()) rig = stage;
        }
        return RootCatalogClient.rig(rig);
    }

    private static float speciesScale(LivingEntity entity) {
        if (entity.level().isClientSide) {
            var origin = RootCatalogClient.origin(RootClientStore.resolve(entity));
            return origin == null ? 1f : BodySize.factor(origin.rigScale());
        }
        String root = entity instanceof Player player ? PlayerRoot.getRootId(player)
                : TownsteadVillagers.get((VillagerEntityMCA)entity).life().rootId();
        var speciesId = RootRegistry.effectiveSpecies(ResourceLocation.tryParse(root));
        var species = speciesId == null ? null : SpeciesRegistry.byId(speciesId);
        return species == null ? 1f : BodySize.factor(species.rig().scale());
    }
}
