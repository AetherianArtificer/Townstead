package com.aetherianartificer.townstead.leatherworking;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * One unit of leatherworker work. Vanilla jobs (e.g. cauldron tanning) live
 * under {@code leatherworking/vanilla/}; mod-specific jobs (e.g. Butchery's
 * Skin Rack) live under their respective {@code compat/<mod>/} package and
 * implement this interface so the brain task does not depend on any mod.
 *
 * <p>Each job runs single-action per task session: the brain task asks each
 * registered job in priority order whether it has a {@link Plan}, picks the
 * first one that does, walks the villager to {@link Plan#anchor()}, and
 * calls {@link #execute}. The next session re-evaluates from scratch.
 */
public interface LeatherworkerJob {

    /**
     * Cheap availability gate. Should return false when the underlying mod
     * (or feature) is absent so the brain task can skip the job entirely
     * without scanning the world.
     */
    boolean isAvailable();

    /**
     * Look for actionable work for this villager right now. Returns
     * {@link Optional#empty()} when nothing to do; otherwise the returned
     * {@link Plan} carries the target position the villager must approach
     * and any per-job state the {@link #execute} call will need.
     */
    Optional<Plan> findWork(ServerLevel level, VillagerEntityMCA villager);

    /**
     * Apply the planned transition. Called once per session, after the
     * villager has reached {@link Plan#anchor()}. Implementations are
     * responsible for animation (swing), sound, particle, world mutation,
     * and inventory updates. They should NOT path the villager.
     */
    void execute(ServerLevel level, VillagerEntityMCA villager, Plan plan);

    /**
     * Optional missing-supply complaint key for the chat throttle. Returned
     * value is a dialogue key (e.g.
     * {@code dialogue.chat.leatherworker_request.no_salt}). The complaints
     * ticker appends a /1..N variant suffix at emit time. Returning
     * {@code null} means "no complaint to make right now".
     */
    @Nullable
    default String missingSupplyDialogueKey(ServerLevel level, VillagerEntityMCA villager) {
        return null;
    }

    /**
     * Pull one missing supply for this job from nearby storage into the
     * villager's inventory. Called by the supply acquisition ticker when
     * the job has work pending but the villager is short on inputs.
     * Implementations should return true if a pull happened, false if no
     * pull was needed or possible. Default: no-op.
     */
    default boolean tryPullMissingSupply(ServerLevel level, VillagerEntityMCA villager) {
        return false;
    }

    /**
     * Carries the result of {@link #findWork} from start to execute. Jobs
     * subclass this freely to attach additional state.
     */
    class Plan {
        private final BlockPos anchor;

        public Plan(BlockPos anchor) {
            this.anchor = anchor.immutable();
        }

        public BlockPos anchor() {
            return anchor;
        }
    }
}
