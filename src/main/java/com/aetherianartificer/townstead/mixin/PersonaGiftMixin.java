package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.persona.PersonaGifts;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BreedableRelationship;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

/**
 * A gift named in a Persona's {@code gifts} is taken the Persona's way (see {@link PersonaGifts}).
 * Every other gift, and every ordinary villager, keeps MCA's handling.
 */
@Mixin(value = BreedableRelationship.class, remap = false)
public abstract class PersonaGiftMixin {
    @Unique
    private static volatile Field townstead$personaGiftEntity;

    @Inject(method = "giveGift", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$personaGift(ServerPlayer player, Memories memory, CallbackInfo ci) {
        try {
            Field field = townstead$personaGiftEntity;
            if (field == null) {
                field = net.conczin.mca.entity.ai.Relationship.class.getDeclaredField("entity");
                field.setAccessible(true);
                townstead$personaGiftEntity = field;
            }
            if (field.get(this) instanceof VillagerEntityMCA villager && PersonaGifts.give(player, villager, memory)) {
                ci.cancel();
            }
        } catch (ReflectiveOperationException ignored) {
            // An MCA without this shape keeps its own gift handling.
        }
    }
}
