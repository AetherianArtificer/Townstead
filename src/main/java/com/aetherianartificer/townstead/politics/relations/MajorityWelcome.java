package com.aetherianartificer.townstead.politics.relations;

import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.root.disposition.DispositionGroups;
import com.aetherianartificer.townstead.root.disposition.DispositionRelations;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A faction welcomes a group that more than half its residents belong to, such as a town of
 * vampires. Only groups that can be welcomed count. Residents who are not loaded count toward the
 * whole but not toward any group, so a partly loaded town never tips on a few faces.
 */
public final class MajorityWelcome implements FactionRelations.WelcomeSource {
    private MajorityWelcome() {}

    public static void register() {
        FactionRelations.register(new MajorityWelcome());
    }

    @Override
    public Set<String> welcomes(MinecraftServer server, Faction faction) {
        Set<String> welcomable = DispositionRelations.welcomable().keySet();
        if (welcomable.isEmpty()) return Set.of();
        Map<String, Integer> counts = new HashMap<>();
        int population = 0;
        for (SettlementRef settlement : faction.settlements()) {
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
            Village village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
            if (village == null) continue;
            population += village.getPopulation();
            for (UUID id : village.getResidentsUUIDs().toList()) {
                Entity entity = id == null ? null : level.getEntity(id);
                if (!(entity instanceof LivingEntity living) || !living.isAlive()) continue;
                String group = DispositionGroups.of(living);
                if (welcomable.contains(group)) counts.merge(group, 1, Integer::sum);
            }
        }
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> count : counts.entrySet()) {
            if (count.getValue() * 2 > population) out.add(count.getKey());
        }
        return out;
    }
}
