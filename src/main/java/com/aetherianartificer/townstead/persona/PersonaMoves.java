package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.journey.Journeys;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

/**
 * Personas who leave for another village when theirs no longer holds them: when the order they
 * belonged to has dissolved, or when their town has fallen. They go on the road (see
 * {@link Journeys}) as themselves and arrive later wherever their arrival goals are next met.
 */
public final class PersonaMoves {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Personas");
    public static final String ORDER_DISSOLVED = "order_dissolved";
    public static final String TOWN_FALLEN = "town_fallen";
    public static final ResourceLocation PURPOSE = ResourceLocation.tryParse("townstead:persona_move");
    private static final int ROAD_DAYS = 2;

    private PersonaMoves() {}

    /** Checks each loaded Persona that can move on, and sends it on the road when it must. */
    static void tick(MinecraftServer server) {
        PersonaInstances instances = PersonaInstances.get(server);
        for (PersonaInstances.Instance instance : List.copyOf(instances.all())) {
            if (instance.village() == PersonaInstances.TRAVELLING) continue;
            PersonaDefinition persona = Personas.byId(instance.persona());
            if (persona == null || persona.movesOn().isEmpty()) continue;
            VillagerEntityMCA villager = PersonaService.find(server, instance.villager());
            if (villager == null || villager.isSleeping()) continue;
            String reason = reason(server, persona, instance, villager);
            if (reason != null) send(server, persona, instance, villager, reason, ROAD_DAYS);
        }
    }

    /** Puts a Persona on the road, ready to arrive elsewhere after {@code days}. Returns whether they left. */
    static boolean send(MinecraftServer server, PersonaDefinition persona, PersonaInstances.Instance instance,
                        VillagerEntityMCA villager, String reason, int days) {
        CompoundTag data = new CompoundTag();
        data.putString("persona", persona.id().toString());
        data.putString("from_dimension", instance.dimension().toString());
        data.putInt("from_village", instance.village());
        data.putString("reason", reason);
        if (Journeys.depart(villager, PURPOSE, days, data) == null) return false;
        PersonaInstances.get(server).move(instance.villager(), instance.dimension(), PersonaInstances.TRAVELLING);
        LOGGER.info("Persona {} left village {} ({})", persona.id(), instance.village(), reason);
        return true;
    }

    private static String reason(MinecraftServer server, PersonaDefinition persona, PersonaInstances.Instance instance,
                                 VillagerEntityMCA villager) {
        if (persona.movesOn().contains(TOWN_FALLEN) && townFallen(server, instance)) return TOWN_FALLEN;
        if (persona.movesOn().contains(ORDER_DISSOLVED) && orderDissolved(server, villager.getUUID())) return ORDER_DISSOLVED;
        return null;
    }

    /** Their home village is gone, has no one else left in it, or is held by vampires. */
    private static boolean townFallen(MinecraftServer server, PersonaInstances.Instance instance) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimension()));
        if (level == null) return false;
        Village village = VillageManager.get(level).getOrEmpty(instance.village()).orElse(null);
        if (village == null) return true;
        PersonaInstances instances = PersonaInstances.get(server);
        boolean anyoneElse = village.getResidentsUUIDs().anyMatch(id -> id != null && !instances.isPersona(id));
        if (!anyoneElse) return true;
        return ModCompat.isLoaded("vampirism")
                && com.aetherianartificer.townstead.compat.vampirism.VampireTotemWatch.heldByVampires(server, instance.dimension(), instance.village());
    }

    /**
     * They once belonged to an order (a faction that holds no land) and no longer belong to any: it
     * dissolved, or they were the last to leave it.
     */
    private static boolean orderDissolved(MinecraftServer server, UUID person) {
        PoliticalSavedData data = PoliticalSavedData.get(server);
        boolean belonged = false;
        for (BondInstance bond : data.bonds(Party.person(person))) {
            Party other = bond.other(Party.person(person));
            Faction faction = other == null || other.faction() == null ? null : data.faction(other.faction());
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind == null || kind.holdsLand() || !bond.kind().equals(kind.membership().bond())) continue;
            if (bond.active() && faction.active()) return false;
            belonged = true;
        }
        return belonged;
    }
}
