package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.hook.PhenoHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pheno interception points on {@link Animal}. The {@code breeding_cooldown} modifier scales the
 * post-breeding cooldown (the {@code 6000}-tick age set on both parents) so an animal carrying the
 * power breeds again sooner or later. Vanilla sets it at the end of
 * {@code spawnChildFromBreeding} ({@code m_27564_} on 1.20.1); there is no event for the value, so
 * we rescale both parents at TAIL. Applies to whichever animal carries the power; MCA villager
 * pregnancy is a separate system and unaffected. All logic is in {@link PhenoHooks}.
 */
@Mixin(Animal.class)
public abstract class AnimalPhenoMixin {

    //? if neoforge {
    @Inject(method = "spawnChildFromBreeding(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;)V",
            at = @At("TAIL"))
    //?} else {
    /*@Inject(method = "m_27563_(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/animal/Animal;)V",
            at = @At("TAIL"), remap = false)
    *///?}
    private void townstead$scaleBreedingCooldown(ServerLevel level, Animal partner, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;
        self.setAge(PhenoHooks.breedingCooldown(self, self.getAge()));
        partner.setAge(PhenoHooks.breedingCooldown(partner, partner.getAge()));
    }
}
