package com.aetherianartificer.townstead.origin.ability;

import com.aetherianartificer.townstead.origin.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-entity on/off state for toggle-mode ability genes (transient; resets on
 * reload, like cooldowns). Flipped by the Origin Ability key; read by the ability
 * ticker to decide whether a toggle ability applies this tick.
 */
public final class AbilityToggles {

    private static final Map<UUID, Set<ResourceLocation>> ON = new ConcurrentHashMap<>();

    private AbilityToggles() {}

    public static boolean isOn(LivingEntity entity, ResourceLocation geneId) {
        Set<ResourceLocation> set = ON.get(entity.getUUID());
        return set != null && set.contains(geneId);
    }

    /** Flip a toggle ability's state; returns the new state. */
    public static boolean flip(LivingEntity entity, ResourceLocation geneId) {
        Set<ResourceLocation> set = ON.computeIfAbsent(entity.getUUID(), k -> ConcurrentHashMap.newKeySet());
        if (set.contains(geneId)) {
            set.remove(geneId);
            return false;
        }
        set.add(geneId);
        return true;
    }

    public static void clear(UUID uuid) {
        ON.remove(uuid);
    }

    /**
     * Send the player their live on-set so the controlling client can predict
     * toggle-driven movement abilities. Carries only currently-expressed toggle
     * genes that are on, keyed by the player's network id; player-only.
     */
    public static void syncTo(ServerPlayer player) {
        List<String> on = new ArrayList<>();
        for (Power gene : Powers.active(player)) {
            if (gene.component() instanceof AbilityGeneType.Instance ability
                    && ability.mode() == AbilityGeneType.Mode.TOGGLE
                    && isOn(player, gene.id())) {
                on.add(gene.id().toString());
            }
        }
        AbilityTogglesS2CPayload payload = new AbilityTogglesS2CPayload(player.getId(), on);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
