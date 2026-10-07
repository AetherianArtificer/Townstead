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
    /** A town must stay empty this many days before a Persona gives up on it. */
    private static final long EMPTY_DAYS = 3;
    /** When each Persona's town was first seen empty, by villager. */
    private static final java.util.Map<UUID, Long> EMPTY_SINCE = new java.util.HashMap<>();
    private static boolean listening;

    private PersonaMoves() {}

    /** Checks each loaded Persona that can move on, and sends it on the road when it must. */
    static void tick(MinecraftServer server) {
        listen();
        PersonaInstances instances = PersonaInstances.get(server);
        for (PersonaInstances.Instance instance : List.copyOf(instances.all())) {
            if (instance.village() == PersonaInstances.TRAVELLING) continue;
            PersonaDefinition persona = Personas.byId(instance.persona());
            if (persona == null || persona.movesOn().isEmpty()) continue;
            VillagerEntityMCA villager = PersonaService.find(server, instance.villager());
            if (villager == null || villager.isSleeping() || Journeys.isLeaving(villager.getUUID())) continue;
            instance = followVillage(server, instance, villager);
            String reason = reason(server, persona, instance, villager);
            if (reason == null) continue;
            // Like an errand, they walk out of town and go once nobody is watching, never in front of you.
            Journeys.departUnseen(villager, PURPOSE, ROAD_DAYS, moveData(persona, instance, reason));
            LOGGER.info("Persona {} is leaving village {} ({})", persona.id(), instance.village(), reason);
            tellNearby(villager, "message.townstead.persona.leaving");
        }
    }

    /** Marks a moving Persona as on the road the moment they actually leave. */
    private static void listen() {
        if (listening) return;
        listening = true;
        Journeys.onDepart(journey -> {
            if (!PURPOSE.equals(journey.purpose())) return;
            //? if neoforge {
            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            //?} else {
            /*MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            *///?}
            if (server == null) return;
            PersonaInstances instances = PersonaInstances.get(server);
            PersonaInstances.Instance instance = instances.of(journey.traveller());
            if (instance != null) instances.move(journey.traveller(), instance.dimension(), PersonaInstances.TRAVELLING);
            EMPTY_SINCE.remove(journey.traveller());
        });
    }

    /**
     * The village a Persona really lives in now. MCA can merge or renumber a village, and a Persona
     * whose stored id no longer exists is still standing in a town.
     */
    private static PersonaInstances.Instance followVillage(MinecraftServer server, PersonaInstances.Instance instance,
                                                           VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return instance;
        VillageManager villages = VillageManager.get(level);
        if (villages.getOrEmpty(instance.village()).isPresent()) return instance;
        Village here = villages.findNearestVillage(villager.blockPosition(), Village.MERGE_MARGIN).orElse(null);
        if (here == null) return instance;
        PersonaInstances instances = PersonaInstances.get(server);
        instances.move(instance.villager(), level.dimension().location(), here.getId());
        PersonaInstances.Instance moved = instances.of(instance.villager());
        return moved == null ? instance : moved;
    }

    private static CompoundTag moveData(PersonaDefinition persona, PersonaInstances.Instance instance, String reason) {
        CompoundTag data = new CompoundTag();
        data.putString("persona", persona.id().toString());
        data.putString("from_dimension", instance.dimension().toString());
        data.putInt("from_village", instance.village());
        data.putString("reason", reason);
        return data;
    }

    /** Tells the players who are near enough to have noticed them. */
    private static void tellNearby(VillagerEntityMCA villager, String key) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            if (player.distanceToSqr(villager) > 128 * 128) continue;
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(key, villager.getName()));
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

    /**
     * Their home village is gone, has stood empty of anyone but Personas for {@link #EMPTY_DAYS} days,
     * or is held by vampires. Empty means no other villager lives there and none is in town: a young
     * town whose people have no homes yet is not empty.
     */
    private static boolean townFallen(MinecraftServer server, PersonaInstances.Instance instance) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, instance.dimension()));
        if (level == null) return false;
        Village village = VillageManager.get(level).getOrEmpty(instance.village()).orElse(null);
        if (village != null && ModCompat.isLoaded("vampirism")
                && com.aetherianartificer.townstead.compat.vampirism.VampireTotemWatch.heldByVampires(server, instance.dimension(), instance.village())) {
            return true;
        }
        PersonaInstances instances = PersonaInstances.get(server);
        boolean empty = village == null;
        if (!empty) {
            boolean resident = village.getResidentsUUIDs().anyMatch(id -> id != null && !instances.isPersona(id));
            VillagerEntityMCA persona = PersonaService.find(server, instance.villager());
            boolean present = persona != null && !level.getEntitiesOfClass(VillagerEntityMCA.class,
                    persona.getBoundingBox().inflate(96), v -> v.isAlive() && !instances.isPersona(v.getUUID())).isEmpty();
            empty = !resident && !present;
        }
        long today = com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(server);
        if (!empty) {
            EMPTY_SINCE.remove(instance.villager());
            return false;
        }
        long since = EMPTY_SINCE.computeIfAbsent(instance.villager(), k -> today);
        return today - since >= EMPTY_DAYS;
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
