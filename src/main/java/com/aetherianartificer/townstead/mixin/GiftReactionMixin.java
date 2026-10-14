package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.gift.GiftReactions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * Every gift a player gives a villager passes Townstead's gift reactions first (see
 * {@link GiftReactions}); a gift none of them takes keeps MCA's handling. The villager lives on
 * MCA's {@code Relationship} superclass, so it is read by reflection instead of an inherited
 * {@code @Shadow}. MCA's method, hence {@code remap=false}.
 */
@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class GiftReactionMixin {
    @Unique
    private static volatile Field townstead$giftEntity;

    @Inject(method = "giveGift", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$giftReaction(ServerPlayer player, Memories memory, CallbackInfo ci) {
        try {
            Field field = townstead$giftEntity;
            if (field == null) {
                field = net.conczin.mca.entity.ai.Relationship.class.getDeclaredField("entity");
                field.setAccessible(true);
                townstead$giftEntity = field;
            }
            if (field.get(this) instanceof VillagerEntityMCA villager && GiftReactions.give(player, villager, memory)) {
                ci.cancel();
            }
        } catch (ReflectiveOperationException ignored) {
            // An MCA without this shape keeps its own gift handling.
        }
    }
}
