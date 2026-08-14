package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.work.producer.ProducerWorkTask;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteDrivers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Generic fetch, engage and release lifecycle for entity-powered V2 stations. */
public final class StationDriverCoordinator {
    private static final float WORKER_SPEED = 0.56f;
    private static final double FETCH_DISTANCE_SQ = 6.25d;
    private static final double STATION_DISTANCE_SQ = 2.25d;

    private @Nullable UUID activeDriver;
    private @Nullable Vec3 home;
    private @Nullable BlockPos activeStation;
    private com.aetherianartificer.townstead.pheno.reservation.ReservationScope reservationScope =
            new com.aetherianartificer.townstead.pheno.reservation.ReservationScope();

    /**
     * Walks to the selected animal, escorts it to the station and waits for the station's authored
     * operational predicate to acknowledge the binding. No content-mod class or block id appears
     * here; stepping onto the machine is the universal player-visible engagement gesture.
     */
    public ProducerWorkTask.PreparationResult prepare(
            ServerLevel level, VillagerEntityMCA worker, Worksite site,
            BlockPos station, WorkstationV2Def def,
            @Nullable com.aetherianartificer.townstead.work.order.Order order) {
        if (!def.hasReservation()) {
            release(level);
            return ProducerWorkTask.PreparationResult.ready();
        }
        var reservation = def.reservation();
        if (activeStation != null && !activeStation.equals(station)) {
            release(level);
            return ProducerWorkTask.PreparationResult.waiting();
        }
        var operation = order == null
                ? com.aetherianartificer.townstead.work.order.Order.Operation.AUTOMATIC
                : order.operation();
        UUID selected = operation == com.aetherianartificer.townstead.work.order.Order.Operation.ENTITY
                && order != null ? order.operator()
                : operation == com.aetherianartificer.townstead.work.order.Order.Operation.WORKER
                        ? worker.getUUID() : site.driver();
        if (selected != null && activeDriver != null && !selected.equals(activeDriver)) {
            release(level);
            return ProducerWorkTask.PreparationResult.waiting();
        }
        if (def.isOperational(level, station)) {
            // A world reload loses task-local state but not the block's real binding. Re-adopt the
            // assigned/nearest entity so the end-of-shift release still has something concrete to
            // free. Its old home is unknowable after a reload, so release uses the safe fallback.
            Mob bound = activeMob(level, site, station, reservation, worker);
            if (bound == null) {
                Mob resolved = WorksiteDrivers.resolve(
                        level, site, reservation, station, reservationScope, worker,
                        operation, order == null ? null : order.operator());
                if (resolved == null && selected != null) {
                    return ProducerWorkTask.PreparationResult.blocked(
                            "A different or unavailable animal is bound to this station.");
                }
                if (resolved != null && reservationScope.reserve(resolved)) activeDriver = resolved.getUUID();
            }
            bound = activeMob(level, site, station, reservation, worker);
            if (bound != null && bound.isLeashed()) bound.dropLeash(true, false);
            activeStation = station.immutable();
            return ProducerWorkTask.PreparationResult.ready();
        }

        Mob driver = activeMob(level, site, station, reservation, worker);
        if (driver == null) {
            driver = WorksiteDrivers.resolve(
                    level, site, reservation, station, reservationScope, worker,
                    operation, order == null ? null : order.operator());
        }
        if (driver == null) {
            return ProducerWorkTask.PreparationResult.blocked(
                    selected == null
                            ? "No matching assignment is currently available."
                            : "The assigned entity is unavailable or no longer matches this station.");
        }
        if (!driver.getUUID().equals(activeDriver)) {
            if (!reservationScope.reserve(driver)) {
                return ProducerWorkTask.PreparationResult.blocked(
                        "The selected entity is reserved by another task.");
            }
            activeDriver = driver.getUUID();
            home = driver.position();
        }
        activeStation = station.immutable();

        // A worker selected as the fallback is already "fetched".  Put their own brain target on
        // top of the machine; asking them to look at/follow themselves cancels the navigation and
        // is why a self-powered mill would otherwise never begin moving.
        if (driver == worker) {
            BlockPos top = station.above();
            if (worker.distanceToSqr(top.getX() + 0.5d, top.getY(), top.getZ() + 0.5d)
                    > STATION_DISTANCE_SQ) {
                BehaviorUtils.setWalkAndLookTargetMemories(worker, top, WORKER_SPEED, 0);
            } else {
                worker.getNavigation().moveTo(
                        top.getX() + 0.5d, top.getY(), top.getZ() + 0.5d, 1.05d);
            }
            return ProducerWorkTask.PreparationResult.waiting();
        }

        if (worker.distanceToSqr(driver) > FETCH_DISTANCE_SQ) {
            BehaviorUtils.setWalkAndLookTargetMemories(worker, driver, WORKER_SPEED, 1);
            return ProducerWorkTask.PreparationResult.waiting();
        }

        if (driver.isLeashed() && driver.getLeashHolder() != worker) {
            return ProducerWorkTask.PreparationResult.blocked(
                    "The selected entity is already tied somewhere else.");
        }
        //? if >=1.21 {
        boolean mayLeash = driver.canBeLeashed();
        //?} else {
        /*boolean mayLeash = driver.canBeLeashed(null);
        *///?}
        if (!driver.isLeashed() && mayLeash) {
            driver.setLeashedTo(worker, true);
        }

        // The worker stays with the animal while its own navigation takes it onto the physical
        // station. The block's step event performs the real bind and `requires` confirms it.
        boolean canMove = driver.getNavigation().moveTo(
                station.getX() + 0.5d, station.getY() + 1.0d, station.getZ() + 0.5d, 1.05d);
        if (!canMove && driver.distanceToSqr(station.getX() + 0.5d,
                station.getY() + 1.0d, station.getZ() + 0.5d) > STATION_DISTANCE_SQ) {
            driver.dropLeash(true, false);
            return ProducerWorkTask.PreparationResult.blocked(
                    "The selected entity cannot reach this station.");
        }
        BehaviorUtils.setWalkAndLookTargetMemories(worker, driver, WORKER_SPEED, 1);
        if (driver.distanceToSqr(station.getX() + 0.5d,
                station.getY() + 1.0d, station.getZ() + 0.5d) <= STATION_DISTANCE_SQ) {
            worker.getLookControl().setLookAt(driver, 30.0f, 30.0f);
        }
        return ProducerWorkTask.PreparationResult.waiting();
    }

    private @Nullable Mob activeMob(ServerLevel level, Worksite site, BlockPos station,
                                    com.aetherianartificer.townstead.pheno.reservation.ReservationSpec reservation,
                                    VillagerEntityMCA worker) {
        if (activeDriver == null) return null;
        net.minecraft.world.entity.Entity entity = level.getEntity(activeDriver);
        return entity instanceof Mob mob && mob.isAlive()
                && reservation.accepts(level, station, site.villageId(), mob, worker) ? mob : null;
    }

    /**
     * The producer state machine normally keeps its worker at an interaction stand. When the
     * worker is the reserved participant, the machine itself owns their physical movement; clear
     * the stale stand navigation so their brain does not continually fight that public behavior.
     */
    public void maintainWorkerEngagement(VillagerEntityMCA worker) {
        if (worker == null || activeDriver == null || !activeDriver.equals(worker.getUUID())) return;
        worker.getNavigation().stop();
        worker.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
    }

    /**
     * Frees the active driver at a shift/session boundary. Entity-powered machines are allowed to
     * hold an animal in place, so normal pathing cannot release one; restoring its pre-work
     * position makes the machine observe that the driver left, after which normal animal AI owns
     * it again. This is immediate and state-based rather than a guessed delay.
     */
    public void release(ServerLevel level) {
        net.minecraft.world.entity.Entity entity = activeDriver == null ? null : level.getEntity(activeDriver);
        if (entity instanceof Mob driver && driver.isAlive()) {
            if (driver.isLeashed()) driver.dropLeash(true, false);
            Vec3 destination = home;
            if (activeStation != null && (destination == null
                    || destination.distanceToSqr(Vec3.atCenterOf(activeStation)) < 30.25d)) {
                BlockPos safe = safeReleasePosition(level, activeStation);
                destination = new Vec3(safe.getX() + 0.5d, safe.getY(), safe.getZ() + 0.5d);
            }
            if (destination != null) {
                driver.stopRiding();
                driver.teleportTo(destination.x, destination.y, destination.z);
                driver.getNavigation().stop();
                // The path was authored by this engagement. Leaving it in the brain can walk the
                // participant straight back inside the machine's bind radius before the block
                // entity observes the physical departure on its next tick.
                driver.getBrain().eraseMemory(
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                driver.getBrain().eraseMemory(
                        net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET);
            }
        }
        clear();
    }

    /**
     * Releases the participant observed at an operational station even when this task did not
     * acquire it.  Task-local coordinator state is intentionally not persisted, while a physical
     * machine's binding is; after a world reload an already-satisfied order must therefore recover
     * the participant from the authored reservation before it can free them.
     */
    public void releaseObserved(ServerLevel level, VillagerEntityMCA worker, @Nullable Worksite site,
                                @Nullable BlockPos station, @Nullable WorkstationV2Def def) {
        if (activeDriver == null && station != null && def != null && def.hasReservation()
                && def.isOperational(level, station)) {
            Integer villageId = site == null ? null : site.villageId();
            Mob observed = def.reservation().candidates(level, station, villageId, worker).stream()
                    .filter(Mob.class::isInstance)
                    .map(Mob.class::cast)
                    .filter(Mob::isAlive)
                    // Entity-driven stations keep their participant physically at the machine.
                    // Limit recovery to that engagement area so an unrelated village candidate
                    // is never moved merely because the station's persistent flag is stale.
                    .filter(candidate -> candidate.distanceToSqr(
                            station.getX() + 0.5d, station.getY() + 0.5d,
                            station.getZ() + 0.5d) <= 30.25d)
                    .min(java.util.Comparator.comparingDouble(candidate -> candidate.distanceToSqr(
                            station.getX() + 0.5d, station.getY() + 0.5d,
                            station.getZ() + 0.5d)))
                    .orElse(null);
            if (observed != null) {
                activeDriver = observed.getUUID();
                activeStation = station.immutable();
                home = null;
            }
        }
        release(level);
    }

    /** Nearest ordinary stand cell outside the machine's five-block hold radius. */
    private static BlockPos safeReleasePosition(ServerLevel level, BlockPos station) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                int horizontalSq = dx * dx + dz * dz;
                if (horizontalSq < 36 || horizontalSq > 64) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos candidate = station.offset(dx, dy, dz);
                    if (!com.aetherianartificer.townstead.work.WorkPathing
                            .isSafeStandPosition(level, candidate)) continue;
                    double distance = candidate.distSqr(station);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.immutable();
                    }
                }
            }
        }
        return best == null ? station.offset(6, 1, 0) : best;
    }

    public void clear() {
        reservationScope.release();
        reservationScope = new com.aetherianartificer.townstead.pheno.reservation.ReservationScope();
        activeDriver = null;
        home = null;
        activeStation = null;
    }
}
