package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.seek.Seeks;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.WorkTaskDef;
import com.aetherianartificer.townstead.work.WorkTaskDeclarations;
import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import org.jetbrains.annotations.Nullable;

/**
 * Executes data-authored {@code rounds} Jobs: work done by going about rather than at a station.
 * When a round is due and the worker is not still on one, the Job's action runs on the worker,
 * usually a {@code pheno:seek} that finds who to visit and says what to do on arrival. The walking
 * belongs to the seek; this only keeps the working day's rhythm, so a physician's rounds of the
 * sick and a vet's of the pens are the same JSON with different targets.
 */
public final class RoundsWorkTask extends Behavior<VillagerEntityMCA> {

    /** When each Job's next round is due, kept on the villager so a reload doesn't reset it. */
    private static final String NEXT = "townstead_rounds_next";
    private static final int SCAN_INTERVAL_TICKS = 20;

    private long nextScan = Long.MIN_VALUE;
    private @Nullable WorkJobDef due;

    public RoundsWorkTask() {
        super(ImmutableMap.of(), 1);
    }

    /** Whether a round is due for this task, for the order list and other jobs to defer to. */
    public static boolean hasWork(ServerLevel level, VillagerEntityMCA villager, @Nullable ResourceLocation task) {
        return pick(level, villager, task) != null;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        long gameTime = level.getGameTime();
        if (nextScan == Long.MIN_VALUE) nextScan = gameTime + Math.floorMod(villager.getId(), SCAN_INTERVAL_TICKS);
        if (gameTime < nextScan) return false;
        nextScan = gameTime + SCAN_INTERVAL_TICKS;
        due = pick(level, villager, null);
        return due != null;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        WorkJobDef job = due;
        due = null;
        if (job == null || job.rounds() == null) return;
        WorkJobDef.Rounds rounds = job.rounds();
        nextRounds(villager).putLong(job.id().toString(), gameTime + rounds.every());
        rounds.action().run(new ActionContext(villager));
        if (rounds.xp() > 0) {
            ResourceLocation career = ProfessionDefs.canonicalId(BuiltInRegistries.VILLAGER_PROFESSION
                    .getKey(villager.getVillagerData().getProfession()));
            CareerProgression.completeWork(villager, career, rounds.xp(), gameTime, job.activityKey(),
                    null, null, 1, java.util.Map.of("job", job.id().toString()));
        }
    }

    /** The first declared rounds Job that is due and free to start, or null. */
    private static @Nullable WorkJobDef pick(ServerLevel level, VillagerEntityMCA villager,
                                             @Nullable ResourceLocation onlyTask) {
        long gameTime = level.getGameTime();
        CompoundTag next = nextRounds(villager);
        for (WorkTaskDef task : WorkTaskDeclarations.all(villager)) {
            if (onlyTask != null && !onlyTask.equals(task.type())) continue;
            for (WorkJobDef job : WorkJobs.forTask(task.type())) {
                WorkJobDef.Rounds rounds = job.rounds();
                if (rounds == null) continue;
                if (gameTime < next.getLong(job.id().toString())) continue;
                if (busy(villager, rounds)) continue;
                if (rounds.condition() != null && !rounds.condition().test(new ConditionContext(villager))) continue;
                return job;
            }
        }
        return null;
    }

    private static boolean busy(VillagerEntityMCA villager, WorkJobDef.Rounds rounds) {
        for (ResourceLocation seek : rounds.busyWhile()) {
            if (Seeks.seeking(villager, seek)) return true;
        }
        return false;
    }

    private static CompoundTag nextRounds(VillagerEntityMCA villager) {
        CompoundTag data = villager.getPersistentData();
        if (!data.contains(NEXT)) data.put(NEXT, new CompoundTag());
        return data.getCompound(NEXT);
    }
}
