package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge;
import com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.gene.types.ThermalToleranceGeneType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

//? if neoforge {
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
//?} else {
/*import net.minecraft.world.effect.MobEffect;
*///?}

/**
 * A player whose Root has {@code "climate": "any"} holds each installed temperature mod's own
 * immunity effect, kept topped up and hidden. Townstead does not simulate player body heat, so
 * this is how the gene reaches players. When the gene goes, the effect runs out by itself.
 */
public final class ClimateImmunity {
    private static final int CHECK_INTERVAL = 40;
    private static final int DURATION = 200;

    private ClimateImmunity() {}

    public static void tick(ServerPlayer player) {
        if (player.tickCount % CHECK_INTERVAL != 0 || !careless(player)) return;
        for (AmbientTemperatureBridge bridge : TemperatureBridgeResolver.installed()) {
            ResourceLocation id = bridge.playerImmunityEffect();
            if (id != null) topUp(player, id);
        }
    }

    private static boolean careless(ServerPlayer player) {
        for (ThermalToleranceGeneType.Instance gene
                : ExpressedGenes.instancesOf(player, ThermalToleranceGeneType.Instance.class)) {
            if (gene.climateAny()) return true;
        }
        return false;
    }

    private static void topUp(ServerPlayer player, ResourceLocation id) {
        if (!BuiltInRegistries.MOB_EFFECT.containsKey(id)) return;
        //? if neoforge {
        Holder<MobEffect> effect = BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id)).orElse(null);
        //?} else {
        /*MobEffect effect = BuiltInRegistries.MOB_EFFECT.get(id);
        *///?}
        if (effect == null) return;
        MobEffectInstance current = player.getEffect(effect);
        if (current != null && current.getDuration() > DURATION / 2) return;
        player.addEffect(new MobEffectInstance(effect, DURATION, 0, true, false, false));
    }
}
