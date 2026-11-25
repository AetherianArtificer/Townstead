package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.profession.ProfessionCarriers;
import net.conczin.mca.entity.ai.brain.tasks.LoseUnimportantJobTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Leaves seat-hired workers to the seat allocator, and keeps a job-block worker employed while
 * the village still has a block of their type (see
 * {@link com.aetherianartificer.townstead.profession.JobRetention}).
 *
 * <p>MCA takes the job from any novice with no job-site memory. A building seat never writes
 * that memory, so a worker the allocator had just hired lost the job on the next brain tick and
 * was hired again at the allocator's next pass. One authority decides each job: seats hire and
 * release these professions, and this task does not.</p>
 */
@Mixin(LoseUnimportantJobTask.class)
public abstract class LoseUnimportantJobTaskSeatedMixin {

    @Inject(method = "shouldRun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void townstead$seatsDecideSeatedJobs(ServerLevel world, Villager entity,
                                                        CallbackInfoReturnable<Boolean> cir) {
        if (ProfessionCarriers.seatHired(entity.getVillagerData().getProfession())
                || !com.aetherianartificer.townstead.profession.JobRetention.mayLoseJob(world, entity)) {
            cir.setReturnValue(false);
        }
    }
}
