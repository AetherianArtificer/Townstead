package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.item.AccordLetterItem;
import com.aetherianartificer.townstead.politics.charter.CharterAccords;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * An accord letter handed to a villager is delivered, not kept as a gift: the villager answers it
 * if they speak for the faction it is addressed to. The villager lives on MCA's {@code Relationship}
 * superclass, so it is read by reflection. MCA's method, hence {@code remap=false}.
 */
@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class AccordLetterGiftMixin {
    @Unique
    private static volatile Field townstead$accordEntity;

    @Inject(method = "giveGift", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$deliverAccord(ServerPlayer player, Memories memory, CallbackInfo ci) {
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof AccordLetterItem)) return;
        try {
            Field field = townstead$accordEntity;
            if (field == null) {
                field = net.conczin.mca.entity.ai.Relationship.class.getDeclaredField("entity");
                field.setAccessible(true);
                townstead$accordEntity = field;
            }
            if (field.get(this) instanceof VillagerEntityMCA villager && CharterAccords.give(player, villager, held)) {
                ci.cancel();
            }
        } catch (Throwable ignored) {
            // An MCA without this shape keeps its own gift handling.
        }
    }
}
