package com.aetherianartificer.townstead.mixin.compat.vampirism;

import com.aetherianartificer.townstead.compat.vampirism.VampireVillagers;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;

/**
 * Vampirism's AI targeting only sees entities that implement its faction interface. A vampire
 * villager is judged as a vampire here (so hunters hunt it and vampires leave it be); every other
 * villager keeps Vampirism's own answer. Fields read by reflection, no {@code @Shadow}.
 */
@Pseudo
@Mixin(targets = "de.teamlapen.vampirism.entity.factions.FactionPredicate", remap = false)
public abstract class FactionPredicateMixin {
    @Unique
    private static volatile Field[] townstead$fields;

    @Inject(method = "apply(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$vampireVillager(LivingEntity input, CallbackInfoReturnable<Boolean> cir) {
        if (input == null || !VampireVillagers.isVampire(input)) return;
        Object vampire = VampireVillagers.vampireFaction();
        if (vampire == null) return;
        try {
            Field[] fields = townstead$fields;
            if (fields == null) {
                Class<?> type = this.getClass();
                fields = new Field[]{type.getDeclaredField("thisFaction"), type.getDeclaredField("otherFaction"),
                        type.getDeclaredField("nonPlayer")};
                for (Field field : fields) field.setAccessible(true);
                townstead$fields = fields;
            }
            Object thisFaction = fields[0].get(this);
            Object otherFaction = fields[1].get(this);
            boolean nonPlayer = fields[2].getBoolean(this);
            cir.setReturnValue(nonPlayer && !vampire.equals(thisFaction)
                    && (otherFaction == null || vampire.equals(otherFaction)));
        } catch (Throwable ignored) {
            // A reshaped predicate keeps Vampirism's own answer.
        }
    }
}
