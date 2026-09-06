package com.aetherianartificer.townstead.hangout;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Extensible posture and animation boundary with a dependency-free vanilla implementation. */
public final class HangoutEmbodiment {
    public static final ResourceLocation VANILLA = id("townstead:vanilla");
    private static final String SEAT_TAG = "townstead_hangout_seat";
    private static final String TABLE_FACING_TAG = "townstead_table_facing";
    private static final String VISIT_TAG_PREFIX = "townstead_visit_";
    private static final Map<ResourceLocation, PostureAdapter> POSTURES = new LinkedHashMap<>();

    /** Defer registry-key construction until live embodiment, keeping data parsing bootstrap-free. */
    private static final class Tables {
        private static final TagKey<Block> VALUE = TagKey.create(Registries.BLOCK,
                id("townstead_hangouts:tables"));
    }

    public interface Handle { void close(ServerLevel level, VillagerEntityMCA villager); }
    /** Adapter understood the furniture but refused it; callers must not try another adapter. */
    public static final Handle BLOCKED = (level, villager) -> {};

    public interface PostureAdapter {
        ResourceLocation id();
        @Nullable Handle enter(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                               ResourceLocation posture, Vec3 position, UUID visitId);
        default void maintain(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                              ResourceLocation posture, Vec3 position, UUID visitId) {}
    }

    private HangoutEmbodiment() {}

    public static synchronized void bootstrap() {
        POSTURES.putIfAbsent(VANILLA, new VanillaPostureAdapter());
        POSTURES.putIfAbsent(ReclinePostureAdapter.ID, new ReclinePostureAdapter());
        POSTURES.putIfAbsent(com.aetherianartificer.townstead.compat.beachparty.BeachpartyChairAdapter.ID,
                new com.aetherianartificer.townstead.compat.beachparty.BeachpartyChairAdapter());
    }

    public static synchronized void register(PostureAdapter adapter) {
        if (adapter != null && adapter.id() != null) POSTURES.put(adapter.id(), adapter);
    }

    public static @Nullable Handle enter(ServerLevel level, VillagerEntityMCA villager,
                                         BlockPos spot, ResourceLocation adapter,
                                         ResourceLocation posture, Vec3 position, UUID visitId) {
        bootstrap();
        PostureAdapter selected;
        synchronized (HangoutEmbodiment.class) {
            selected = POSTURES.getOrDefault(adapter, POSTURES.get(VANILLA));
        }
        Handle handle = selected.enter(level, villager, spot, posture, position, visitId);
        if (handle == null && !selected.id().equals(VANILLA)) {
            synchronized (HangoutEmbodiment.class) { selected = POSTURES.get(VANILLA); }
            handle = selected.enter(level, villager, spot, posture, position, visitId);
        }
        return handle;
    }

    public static boolean blocked(@Nullable Handle handle) { return handle == BLOCKED; }

    public static void maintain(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                                ResourceLocation adapter, ResourceLocation posture, Vec3 position, UUID visitId) {
        bootstrap();
        PostureAdapter selected;
        synchronized (HangoutEmbodiment.class) { selected = POSTURES.get(adapter); }
        if (selected != null) selected.maintain(level, villager, spot, posture, position, visitId);
    }

    /** Tags any adapter-created mount for individual visit ownership and orphan recovery. */
    public static void markVisitAnchor(Entity entity, UUID visitId) {
        entity.addTag(SEAT_TAG);
        entity.addTag(VISIT_TAG_PREFIX + visitId);
    }

    /** Removes only Townstead-provenance anchors whose owning visit no longer exists. */
    public static void recoverNearby(ServerLevel level, BlockPos center) {
        for (Entity stand : level.getEntitiesOfClass(Entity.class,
                new AABB(center).inflate(32), entity -> entity.getTags().contains(SEAT_TAG))) {
            UUID visitId = visitTag(stand);
            if (visitId == null || !HangoutEngine.isLive(visitId)) stand.discard();
        }
    }

    private static final class VanillaPostureAdapter implements PostureAdapter {
        @Override public ResourceLocation id() { return VANILLA; }

        @Override
        public @Nullable Handle enter(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                                      ResourceLocation posture, Vec3 position, UUID visitId) {
            villager.getNavigation().stop();
            String path = posture.getPath().toLowerCase(Locale.ROOT);
            if (!(path.contains("sit") || path.contains("seat") || path.contains("stool"))) {
                return (ignoredLevel, ignoredVillager) -> {};
            }
            Entity created = EntityType.ARMOR_STAND.create(level);
            if (!(created instanceof ArmorStand seat)) return null;
            seat.setInvisible(true);
            seat.setNoGravity(true);
            markVisitAnchor(seat, visitId);
            seat.setPos(position.x, position.y, position.z);
            faceNearestTable(level, villager, seat, spot, position);
            if (!level.addFreshEntity(seat) || !villager.startRiding(seat, true)) {
                seat.discard();
                return null;
            }
            UUID seatId = seat.getUUID();
            return (closeLevel, closeVillager) -> {
                if (closeVillager.isPassenger() && closeVillager.getVehicle() != null
                        && seatId.equals(closeVillager.getVehicle().getUUID())) closeVillager.stopRiding();
                Entity loaded = closeLevel.getEntity(seatId);
                if (loaded != null && loaded.getTags().contains(SEAT_TAG)) loaded.discard();
            };
        }

        @Override
        public void maintain(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                             ResourceLocation posture, Vec3 position, UUID visitId) {
            villager.getNavigation().stop();
            Entity vehicle = villager.getVehicle();
            if (vehicle == null || !vehicle.getTags().contains(TABLE_FACING_TAG)) return;
            float yaw = vehicle.getYRot();
            villager.setYRot(yaw);
            villager.yBodyRot = yaw;
            villager.yBodyRotO = yaw;
        }

        private static void faceNearestTable(ServerLevel level, VillagerEntityMCA villager,
                                             ArmorStand seat, BlockPos spot, Vec3 position) {
            BlockPos nearest = null;
            double nearestDistance = Double.MAX_VALUE;
            for (BlockPos candidate : BlockPos.betweenClosed(
                    spot.offset(-4, -2, -4), spot.offset(4, 2, 4))) {
                if (!level.getBlockState(candidate).is(Tables.VALUE)) continue;
                double distance = candidate.distToCenterSqr(position.x, position.y, position.z);
                if (distance < nearestDistance) {
                    nearest = candidate.immutable();
                    nearestDistance = distance;
                }
            }
            if (nearest == null) return;

            double dx = nearest.getX() + 0.5D - position.x;
            double dz = nearest.getZ() + 0.5D - position.z;
            if (dx * dx + dz * dz < 1.0E-6D) return;
            float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90F;
            seat.addTag(TABLE_FACING_TAG);
            seat.setYRot(yaw);
            seat.yRotO = yaw;
            villager.setYRot(yaw);
            villager.yBodyRot = yaw;
            villager.yBodyRotO = yaw;
            villager.setYHeadRot(yaw);
            villager.yHeadRotO = yaw;
        }
    }

    /** Leisure recline: a prone render pose on a mount, never a bed/sleep state. */
    private static final class ReclinePostureAdapter implements PostureAdapter {
        private static final ResourceLocation ID = HangoutEmbodiment.id("townstead:recline");

        @Override public ResourceLocation id() { return ID; }

        @Override
        public @Nullable Handle enter(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                                      ResourceLocation posture, Vec3 position, UUID visitId) {
            Entity created = EntityType.ARMOR_STAND.create(level);
            if (!(created instanceof ArmorStand anchor)) return null;
            anchor.setInvisible(true);
            anchor.setNoGravity(true);
            CompoundTag form = new CompoundTag();
            anchor.saveWithoutId(form);
            form.putBoolean("Marker", true);
            form.putBoolean("Small", true);
            anchor.load(form);
            markVisitAnchor(anchor, visitId);
            anchor.setPos(position.x, position.y, position.z);
            if (!level.addFreshEntity(anchor) || !villager.startRiding(anchor, true)) {
                anchor.discard();
                return BLOCKED;
            }
            UUID anchorId = anchor.getUUID();
            recline(villager, level.getBlockState(spot));
            return (closeLevel, closeVillager) -> {
                if (closeVillager.isPassenger() && closeVillager.getVehicle() != null
                        && anchorId.equals(closeVillager.getVehicle().getUUID())) closeVillager.stopRiding();
                closeVillager.setPose(Pose.STANDING);
                Entity loaded = closeLevel.getEntity(anchorId);
                if (loaded != null && loaded.getTags().contains(SEAT_TAG)) loaded.discard();
            };
        }

        @Override
        public void maintain(ServerLevel level, VillagerEntityMCA villager, BlockPos spot,
                             ResourceLocation posture, Vec3 position, UUID visitId) {
            villager.getNavigation().stop();
            recline(villager, level.getBlockState(spot));
        }

        private static void recline(VillagerEntityMCA villager, net.minecraft.world.level.block.state.BlockState state) {
            // The client furniture layer supplies the authored recline, without vanilla swimming limbs.
            villager.setPose(Pose.STANDING);
            var facing = state.getBlock().getStateDefinition().getProperty("facing");
            if (facing != null) {
                Comparable<?> value = state.getValue(cast(facing));
                if (value instanceof net.minecraft.core.Direction direction && direction.getAxis().isHorizontal()) {
                    villager.setYRot(direction.toYRot());
                    villager.yBodyRot = direction.toYRot();
                    villager.yBodyRotO = direction.toYRot();
                    villager.setYHeadRot(direction.toYRot());
                }
            }
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static net.minecraft.world.level.block.state.properties.Property cast(
                net.minecraft.world.level.block.state.properties.Property<?> property) { return property; }
    }

    private static @Nullable UUID visitTag(Entity entity) {
        for (String tag : entity.getTags()) {
            if (!tag.startsWith(VISIT_TAG_PREFIX)) continue;
            try { return UUID.fromString(tag.substring(VISIT_TAG_PREFIX.length())); }
            catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalArgumentException(value);
        return id;
    }
}
