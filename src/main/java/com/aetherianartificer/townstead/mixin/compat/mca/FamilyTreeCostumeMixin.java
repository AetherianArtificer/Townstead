package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.replace.CostumeFamily;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * The person a costumed mob shows has no place in the family tree until a cure makes them real, so
 * anything that asks for their node gets one the tree does not hold.
 */
@Mixin(value = FamilyTree.class, remap = false)
public abstract class FamilyTreeCostumeMixin {
    @Inject(method = "getOrCreate(Lnet/minecraft/world/entity/Entity;)Lnet/conczin/mca/server/world/data/FamilyTreeNode;",
            at = @At("HEAD"), cancellable = true)
    private void townstead$costumeEntity(Entity entity, CallbackInfoReturnable<FamilyTreeNode> cir) {
        if (!CostumeFamily.keptOut(entity)) return;
        Gender gender = EntityRelationship.of(entity).map(EntityRelationship::getGender).orElse(Gender.MALE);
        cir.setReturnValue(CostumeFamily.node(entity.getUUID(), entity.getName().getString(), gender));
    }

    @Inject(method = "getOrCreate(Ljava/util/UUID;Ljava/lang/String;Lnet/conczin/mca/entity/ai/relationship/Gender;Z)Lnet/conczin/mca/server/world/data/FamilyTreeNode;",
            at = @At("HEAD"), cancellable = true)
    private void townstead$costumeRelative(UUID id, String name, Gender gender, boolean isPlayer,
                                           CallbackInfoReturnable<FamilyTreeNode> cir) {
        if (!isPlayer && CostumeFamily.active()) cir.setReturnValue(CostumeFamily.node(id, name, gender));
    }
}
