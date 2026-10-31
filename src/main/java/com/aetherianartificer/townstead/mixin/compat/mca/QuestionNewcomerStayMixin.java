package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.villager.NonResidents;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.resources.data.dialogue.Answer;
import net.conczin.mca.resources.data.dialogue.Question;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Set;

/** MCA's constraints are a closed list, so the newcomer's stay answer is gated here instead. */
@Mixin(value = Question.class, remap = false)
public abstract class QuestionNewcomerStayMixin {
    @WrapOperation(method = "getValidAnswers", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/resources/data/dialogue/Answer;isValidForConstraint(Ljava/util/Set;)Z"), expect = 1)
    private boolean townstead$newcomerStay(Answer answer, Set<Constraint> constraints, Operation<Boolean> original,
                                           @Local(argsOnly = true) VillagerEntityMCA villager) {
        return NonResidents.offers(answer.getName(), villager) && original.call(answer, constraints);
    }
}
