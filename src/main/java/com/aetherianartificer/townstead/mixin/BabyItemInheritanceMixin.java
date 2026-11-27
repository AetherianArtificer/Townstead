package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.Heredity;
import com.aetherianartificer.townstead.root.RootSpawnHandler;
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
 * attachment-stored state). An unloaded parent is read from the snapshot taken when MCA made
 * the item; with one resolvable parent the child copies it, with none the founder seeding stands.</p>
 *
 * <p>{@code remap=false}: the target lives in MCA (stable names both branches).</p>
 */
@Mixin(value = net.conczin.mca.item.BabyItem.class, remap = false)
public abstract class BabyItemInheritanceMixin {

    // Named in full: TownsteadMixinPlugin can put a stand-in first for Capitals, which a bare name would pick.
    //? if neoforge {
    @Inject(method = {"birthChild(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)Ljava/util/Optional;", "birthChild(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)Lnet/conczin/mca/entity/VillagerEntityMCA;"}, at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "birthChild", at = @At("HEAD"))
    *///?}
    private void townstead$beginBirth(CallbackInfoReturnable<Object> cir) {
        com.aetherianartificer.townstead.naming.SpawnNaming.beginBirth();
    }

    /** Saves both parents' heredity while they are loaded, for a birth that may come far away. */
    @Inject(method = "createItem", at = @At("RETURN"))
    private static void townstead$rememberParents(Entity mother, Entity father, long seed,
                                                  CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir) {
        if (mother == null || mother.level().isClientSide || mother.level().getServer() == null) return;
        var snapshots = com.aetherianartificer.townstead.root.ParentSnapshots.get(mother.level().getServer());
        snapshots.remember(mother, mother.level().getRandom());
        if (father != null) snapshots.remember(father, mother.level().getRandom());
    }

    // Newer MCA returns Optional<VillagerEntityMCA> (empty when there is no room to place the baby).
    //? if neoforge {
    @Inject(method = {"birthChild(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/core/BlockPos;)Ljava/util/Optional;", "birthChild(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/server/level/ServerPlayer;)Lnet/conczin/mca/entity/VillagerEntityMCA;"}, at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "birthChild", at = @At("RETURN"))
    *///?}
    private void townstead$inheritOnBirth(CallbackInfoReturnable<Object> cir) {
        com.aetherianartificer.townstead.naming.SpawnNaming.endBirth();
        Object result = cir.getReturnValue();
        if (result instanceof java.util.Optional<?> optional) result = optional.orElse(null);
        if (!(result instanceof VillagerEntityMCA child) || child.level().isClientSide) return;
        List<Entity> parents = child.getRelationships().getParents().toList();
        // MCA hands the parents over unordered; its family tree knows which one is the mother.
        var family = child.getRelationships().getFamilyEntry();
        if (child.level().getServer() != null) {
            Heredity.inheritFromFamily(TownsteadVillagers.get(child).life(), parents,
                    java.util.Arrays.asList(family.mother(), family.father()),
                    child.level().getServer(), child.getRandom());
        }
        com.aetherianartificer.townstead.root.appearance.IrisColors.seed(child);
        net.minecraft.world.entity.LivingEntity mother = null, father = null;
        for (Entity parent : parents) {
            if (!(parent instanceof net.minecraft.world.entity.LivingEntity living)) continue;
            if (living.getUUID().equals(family.mother())) mother = living;
            else if (living.getUUID().equals(family.father())) father = living;
        }
        com.aetherianartificer.townstead.pheno.state.EntityStates.receiveAtBirth(child, mother, father);
        com.aetherianartificer.townstead.root.appearance.HairColors.inherit(child, parents);
        // Re-align stage durations to the (possibly newly inherited) origin's cycle.
        RootSpawnHandler.backfillIfMissing(child);
        // birthChild spawned the child mid-method, so nearby players already received
        // the founder-seeded placeholder sync; re-push the inherited state.
        RootSpawnHandler.broadcastLateInheritance(child);
        com.aetherianartificer.townstead.compat.mcacapitals.CapitalsNameBridge.recordBirth(child);
        // Both parents are in hand here, which is exactly what a family name needs.
        com.aetherianartificer.townstead.naming.Naming.settleBirth(child);
        com.aetherianartificer.townstead.naming.VillagerNames.publish(child);
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.birth(child);
    }
}
