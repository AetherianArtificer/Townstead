package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Gives an MCA village its faction when first recognized, then keeps that faction's bonds current. */
public final class PoliticalVillageBootstrap {
    public static final ResourceLocation VILLAGE_COUNCIL = id("townstead:village_council");
    public static final ResourceLocation FREE_SETTLEMENT = LegacyPolitics.FREE_SETTLEMENT;
    private static final ResourceLocation GENERATION = id("townstead:village_generation");
    private static final ResourceLocation DEFAULT_PROFILE = id("townstead:default_village");
    private static boolean warnedMissingDefinitions;
    private static boolean registered;

    private PoliticalVillageBootstrap() {}

    public static Result ensure(ServerLevel level, Village village) {
        if (level == null || village == null) return Result.EMPTY;
        var data = PoliticalSavedData.get(level.getServer());
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        Faction existing = data.faction(settlement);
        if (existing == null && data.founding(settlement) == null) {
            // A village seen for the first time takes a founding profile suited to where it stands.
            var chosen = com.aetherianartificer.townstead.politics.founding.FoundingSelection.choose(level, village);
            if (chosen != null && !chosen.id().equals(DEFAULT_PROFILE)) {
                com.aetherianartificer.townstead.politics.founding.FoundingProfileApplier.apply(level, village, chosen,
                        new net.minecraft.core.BlockPos(village.getCenter()));
                existing = data.faction(settlement);
            }
        }
        Collection<UUID> residents = existing == null || existing.status() == Faction.Status.DISSOLVED
                ? village.getResidentsUUIDs().toList() : residents(level, existing);
        var result = ensure(data, settlement, village.getName(), residents, level.getGameTime());
        if (result.createdFaction()) {
            String culture = com.aetherianartificer.townstead.naming.NamingRegisterSavedData.get(level.getServer())
                    .villageCulture(level.dimension().location(), village.getId());
            com.aetherianartificer.townstead.culture.FactionNaming.initialize(data, result.faction(),
                    com.aetherianartificer.townstead.culture.FactionNaming.generate(ResourceLocation.tryParse(culture),
                            VILLAGE_COUNCIL, village.getName()));
        }
        return result;
    }

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        com.aetherianartificer.townstead.api.v1.TownsteadApiV1.get().events().subscribe(
                com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent.class, e -> {
                    if (e.kind() == com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent.Kind.DAY) daily(e.server());
                });
    }

    /** Once a day every faction with land catches up with who lives there. */
    static void daily(net.minecraft.server.MinecraftServer server) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.POLITICS)) return;
        PoliticalSavedData data = PoliticalSavedData.get(server);
        for (Faction faction : data.factions()) {
            if (!faction.active() || faction.settlements().isEmpty()) continue;
            SettlementRef settlement = faction.settlements().get(0);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
            Village village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
            if (village != null) ensure(level, village);
        }
    }

    /** Everyone living in any of the faction's settlements that is loaded. */
    public static Set<UUID> residents(ServerLevel level, Faction faction) {
        Set<UUID> out = new LinkedHashSet<>();
        for (SettlementRef settlement : faction.settlements()) {
            ServerLevel source = level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
            Village village = source == null ? null : VillageManager.get(source).getOrEmpty(settlement.villageId()).orElse(null);
            if (village != null) village.getResidentsUUIDs().forEach(out::add);
        }
        return out;
    }

    /** Pure of MCA/server access so migrations and tests use exactly the live routine. */
    public static Result ensure(PoliticalSavedData data, SettlementRef settlement, String villageName,
                                Collection<UUID> residentIds, long now) {
        return ensure(data, settlement, villageName, residentIds, now, true, false);
    }

    /**
     * Establishes the settlement's faction and its citizens. With {@code createDefaultGovernment}
     * false a new faction starts with no offices; {@code explicitRefounding} lets a player found a
     * fresh faction where a dissolved one stood.
     */
    public static Result ensure(PoliticalSavedData data, SettlementRef settlement, String villageName,
                                Collection<UUID> residentIds, long now, boolean createDefaultGovernment,
                                boolean explicitRefounding) {
        if (data == null || settlement == null || (createDefaultGovernment && !definitionsReady())) return Result.EMPTY;
        String name = villageName == null || villageName.isBlank() ? "Village " + settlement.villageId() : villageName.trim();
        Faction faction = data.faction(settlement);
        boolean created = false;
        if (faction != null && faction.status() == Faction.Status.DISSOLVED) {
            // A dissolved faction suppresses automatic recreation; only a player founding replaces it.
            if (!explicitRefounding) return Result.EMPTY;
            faction = null;
        }
        if (faction == null) {
            ResourceLocation id = PoliticalIds.villageFaction(settlement);
            if (data.faction(id) != null) id = PoliticalIds.faction("townstead");
            faction = new Faction(id, createDefaultGovernment ? VILLAGE_COUNCIL : FREE_SETTLEMENT, name, color(settlement),
                    null, now, GENERATION, Faction.Status.ACTIVE, List.of(settlement), null);
            data.putFaction(faction);
            created = true;
        }
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        FactionUpkeep.Result upkeep = kind == null ? FactionUpkeep.Result.NONE
                : FactionUpkeep.run(data, faction, kind, residentIds, seats(data, settlement), now);
        return new Result(faction.id(), created, upkeep.citizensAdded(), upkeep.officesFilled());
    }

    /** The seats a generated faction starts with: its founding profile's, else the default village's. */
    static List<Set<ResourceLocation>> seats(PoliticalSavedData data, SettlementRef settlement) {
        SettlementFoundingRecord record = data.founding(settlement);
        FoundingProfileDefinition profile = FoundingProfiles.get(record == null ? DEFAULT_PROFILE : record.profile());
        if (profile == null || profile.faction() == null) profile = FoundingProfiles.get(DEFAULT_PROFILE);
        return profile == null || profile.faction() == null ? List.of() : profile.faction().bundles();
    }

    private static boolean definitionsReady() {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(VILLAGE_COUNCIL);
        boolean ready = kind != null;
        if (!ready && !warnedMissingDefinitions) {
            warnedMissingDefinitions = true;
            Townstead.LOGGER.warn("Village politics bootstrap skipped: the village council faction kind is unavailable");
        }
        if (ready) warnedMissingDefinitions = false;
        return ready;
    }

    static int color(SettlementRef settlement) {
        int hash = 31 * settlement.dimension().hashCode() + settlement.villageId();
        return 0x404040 | (hash & 0xBFBFBF);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = DataPackLang.parseId(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }

    public record Result(ResourceLocation faction, boolean createdFaction, int citizensAdded, int officesFilled) {
        private static final Result EMPTY = new Result(id("townstead:none"), false, 0, 0);

        public boolean available() {
            return !faction.equals(EMPTY.faction);
        }
    }
}
