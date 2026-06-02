package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.Heredity;
import com.aetherianartificer.townstead.origin.OriginSpawnHandler;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Applies inheritance to a baby-item birth (the player × villager path: a player
 * grows and places the baby item their villager spouse handed them). At
 * {@code birthChild}'s return the child is spawned and its parents are assigned,
 * so we resolve both parent entities and draw the child's heritage + alleles from
 * them. The player parent contributes its own origin's heritage (default Human).
 *
 * <p>This runs after MCA serialized/restored the dummy child, so working with the
 * live spawned entity avoids the baby-item NBT round-trip (which drops our
 * attachment-stored state). If a parent is unloaded it's simply skipped; with one
 * resolvable parent the child copies it, with none the founder seeding stands.</p>
 *
 * <p>{@code remap=false}: the target lives in MCA (stable names both branches).</p>
 */
@Mixin(value = net.conczin.mca.item.BabyItem.class, remap = false)
public abstract class BabyItemInheritanceMixin {

    @Inject(method = "birthChild", at = @At("RETURN"))
    private void townstead$inheritOnBirth(CallbackInfoReturnable<VillagerEntityMCA> cir) {
        VillagerEntityMCA child = cir.getReturnValue();
        if (child == null || child.level().isClientSide) return;
        List<Entity> parents = child.getRelationships().getParents().toList();
        Heredity.inheritFromEntities(TownsteadVillagers.get(child).life(), parents, child.getRandom());
        // Re-align stage durations to the (possibly newly inherited) origin's cycle.
        OriginSpawnHandler.backfillIfMissing(child);
    }
}
