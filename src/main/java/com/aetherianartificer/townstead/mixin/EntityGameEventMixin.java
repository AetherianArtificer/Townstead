package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.origin.gameevent.PreventGameEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses a {@code prevent_game_event} bearer's own game events (e.g. velvet paws and
 * {@code minecraft:step}). Hooks the single-arg {@code Entity.gameEvent}; the parameter is
 * a {@code GameEvent} on 1.20.1 and a {@code Holder<GameEvent>} on 1.21.1, so the method is
 * version-split ({@code m_146850_} SRG on Forge).
 */
@Mixin(Entity.class)
public abstract class EntityGameEventMixin {

    //? if neoforge {
    @Inject(method = "gameEvent(Lnet/minecraft/core/Holder;)V", at = @At("HEAD"), cancellable = true)
    private void townstead$preventGameEvent(net.minecraft.core.Holder<net.minecraft.world.level.gameevent.GameEvent> event,
                                            CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living)) return;
        net.minecraft.resources.ResourceLocation id =
                event.unwrapKey().map(net.minecraft.resources.ResourceKey::location).orElse(null);
        if (PreventGameEvents.shouldPrevent(living, id)) ci.cancel();
    }
    //?} else {
    /*@Inject(method = "m_146850_", at = @At("HEAD"), cancellable = true, remap = false)
    private void townstead$preventGameEvent(net.minecraft.world.level.gameevent.GameEvent event, CallbackInfo ci) {
        if (!((Object) this instanceof LivingEntity living)) return;
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.core.registries.BuiltInRegistries.GAME_EVENT.getKey(event);
        if (PreventGameEvents.shouldPrevent(living, id)) ci.cancel();
    }
    *///?}
}
