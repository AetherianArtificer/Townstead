package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.PlayerBites;
import com.aetherianartificer.townstead.compat.vampirism.VampireVillagers;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.function.Function;

/**
 * Vampirism turns a creature by replacing its entity through a converter entry, and only types with
 * one can catch Sanguinare. For MCA villagers Townstead answers instead: any villager whose Root
 * allows vampirism can be infected, and when Sanguinare ends it becomes a vampire in place (no entity
 * swap, so family and memories stay), ahead of any other mod's converter. The creature's entity is
 * read by reflection with no {@code @Shadow}, so a reshaped class stays inert. A vampire villager
 * has no blood to give, and a player's bite that drains a villager dry is charged to that player.
 */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.entity.ExtendedCreature", remap = false)
public abstract class ExtendedCreatureMixin {
    @Unique
    private static volatile Field townstead$entityField;

    @Unique
    private VillagerEntityMCA townstead$villager() {
        try {
            Field field = townstead$entityField;
            if (field == null) {
                field = this.getClass().getDeclaredField("entity");
                field.setAccessible(true);
                townstead$entityField = field;
            }
            return field.get(this) instanceof VillagerEntityMCA villager ? villager : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Inject(method = "canBecomeVampire", at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$villagerCanTurn(CallbackInfoReturnable<Boolean> cir) {
        VillagerEntityMCA villager = townstead$villager();
        if (villager != null) cir.setReturnValue(VampireVillagers.canTurn(villager));
    }

    @Inject(method = "canBeInfected", at = @At("RETURN"), cancellable = true, require = 0)
    private void townstead$villagerCanBeInfected(CallbackInfoReturnable<Boolean> cir) {
        VillagerEntityMCA villager = townstead$villager();
        if (villager != null) cir.setReturnValue(VampireVillagers.canBeInfected(villager));
    }

    @Inject(method = "makeVampire", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$turnInPlace(CallbackInfoReturnable<Object> cir) {
        VillagerEntityMCA villager = townstead$villager();
        if (villager == null) return;
        VampireVillagers.turn(villager);
        cir.setReturnValue(null);
    }

    @Inject(method = "canBeBitten", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$vampireHasNoBlood(CallbackInfoReturnable<Boolean> cir) {
        VillagerEntityMCA villager = townstead$villager();
        if (villager != null && VampireVillagers.isVampire(villager)) cir.setReturnValue(false);
    }

    @WrapOperation(method = "onBite", require = 0, at = @At(value = "INVOKE",
            target = "Lde/teamlapen/vampirism/util/DamageHandler;hurtModded(Lnet/minecraft/world/entity/Entity;Ljava/util/function/Function;F)Z"))
    private boolean townstead$chargeDrain(Entity entity, Function<?, ?> source, float amount, Operation<Boolean> original) {
        Player biter = PlayerBites.biter();
        if (entity instanceof VillagerEntityMCA villager && biter != null && PlayerBites.drainKill(villager, biter, amount)) {
            return true;
        }
        return original.call(entity, source, amount);
    }
}
