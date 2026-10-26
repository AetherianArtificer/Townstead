package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.villager.NonResidents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.resources.Dialogues;
import net.conczin.mca.resources.data.dialogue.Answer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/**
 * The server's own check on a chosen answer holds the newcomer's stay answer to newcomers too.
 * MCA's 1.20.1 line does not check chosen answers at all.
 */
@Mixin(value = Dialogues.class, remap = false)
public abstract class DialoguesNewcomerStayMixin {
    @WrapOperation(method = "selectAnswer", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/resources/data/dialogue/Answer;isValidForConstraint(Ljava/util/Set;)Z"), require = 0)
    private boolean townstead$newcomerStay(Answer answer, Set<Constraint> constraints, Operation<Boolean> original,
                                           @Local(argsOnly = true) VillagerEntityMCA villager) {
        return NonResidents.offers(answer.getName(), villager) && original.call(answer, constraints);
    }
}
