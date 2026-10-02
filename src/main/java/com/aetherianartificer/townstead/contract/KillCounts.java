package com.aetherianartificer.townstead.contract;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a player has killed, kept as Chronicle counters: {@code townstead:killed/<entity type>} and,
 * for creatures in a disposition group, {@code townstead:killed_group/<group>}. The kill goes to
 * whoever the game credits, the same rule its death messages use.
 */
public final class KillCounts {
    static final String BY_TYPE = "townstead:killed/";
    static final String BY_GROUP = "townstead:killed_group/";

    private KillCounts() {}

    /** Death hook. */
    public static void onDeath(LivingEntity victim, DamageSource source) {
        if (!(victim.level() instanceof ServerLevel level)) return;
        ServerPlayer player = victim.getKillCredit() instanceof ServerPlayer credited ? credited
                : source.getEntity() instanceof ServerPlayer direct ? direct : null;
        if (player == null) return;
        MinecraftServer server = level.getServer();
        Chronicles.addCounter(server, player.getUUID(), BY_TYPE + EntityType.getKey(victim.getType()), 1);
        String group = DispositionGroups.of(victim);
        if (group != null && !group.isBlank()) Chronicles.addCounter(server, player.getUUID(), BY_GROUP + group, 1);
    }

    /** How many creatures matching any of the given types, tags or groups this player has killed. */
    static long count(MinecraftServer server, UUID player, List<ResourceLocation> types, List<ResourceLocation> tags,
                      List<String> groups) {
        long total = 0;
        Map<String, Integer> counters = Chronicles.countersFor(server, player);
        for (Map.Entry<String, Integer> counter : counters.entrySet()) {
            String key = counter.getKey();
            if (key.startsWith(BY_GROUP)) {
                if (groups.contains(key.substring(BY_GROUP.length()))) total += counter.getValue();
                continue;
            }
            if (!key.startsWith(BY_TYPE) || types.isEmpty() && tags.isEmpty()) continue;
            ResourceLocation id = ResourceLocation.tryParse(key.substring(BY_TYPE.length()));
            if (id == null) continue;
            if (types.contains(id)) {
                total += counter.getValue();
                continue;
            }
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
            if (type == null) continue;
            for (ResourceLocation tag : tags) {
                if (type.is(TagKey.create(Registries.ENTITY_TYPE, tag))) {
                    total += counter.getValue();
                    break;
                }
            }
        }
        return total;
    }
}
