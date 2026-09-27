package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.SettlementNaming;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.FactionUpkeep;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicitly applies a loaded profile to one MCA village and persists the resolved identity. */
public final class FoundingProfileApplier {
    private static final ResourceLocation VILLAGE_GENERATION = id("townstead:village_generation");
    private static final ResourceLocation DEFAULT_VILLAGE = id("townstead:default_village");
    private static final ResourceLocation CHARTER = id("townstead:charter");

    private FoundingProfileApplier() {}

    public static Result apply(ServerLevel level, Village village, FoundingProfileDefinition profile,
                               BlockPos sampleAt) {
        return apply(level, village, profile, sampleAt, null, profile == null ? null : profile.culture(), true, null);
    }

    /** Applies independently chosen Charter values without mutating the source data-pack definition. */
    public static Result apply(ServerLevel level, Village village, FoundingProfileDefinition profile,
                               BlockPos sampleAt, String requestedName, ResourceLocation foundingCulture) {
        return apply(level, village, profile, sampleAt, requestedName, foundingCulture, false, null);
    }

    public static Result foundByPlayer(ServerLevel level, Village village, FoundingProfileDefinition profile,
                                       BlockPos at, String name, ResourceLocation culture, UUID founder) {
        if (founder == null) return Result.failed("founder_missing");
        return apply(level, village, profile, at, name, culture, false, founder);
    }

    private static Result apply(ServerLevel level, Village village, FoundingProfileDefinition profile,
                                BlockPos sampleAt, String requestedName, ResourceLocation foundingCulture,
                                boolean assignVillageCulture, UUID founder) {
        if (level == null || village == null || profile == null) return Result.failed("invalid_request");
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        String settlementName = requestedName == null || requestedName.isBlank()
                ? SettlementNaming.resolve(foundingCulture, village.getName()) : requestedName.trim();
        if (!settlementName.isBlank() && !settlementName.equals(village.getName())) village.setName(settlementName);
        List<UUID> residents = village.getResidentsUUIDs().toList().stream()
                .filter(java.util.Objects::nonNull).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        long now = level.getGameTime();
        PoliticalVillageBootstrap.Result base = PoliticalVillageBootstrap.ensure(data, settlement, village.getName(),
                residents, now, profile.faction() != null && founder == null, founder != null);
        if (!base.available()) return Result.failed("politics_unavailable");
        Faction faction = data.faction(base.faction());
        if (faction == null) return Result.failed("faction_missing");
        if (base.createdFaction()) {
            com.aetherianartificer.townstead.culture.FactionNaming.initialize(data, faction.id(),
                    com.aetherianartificer.townstead.culture.FactionNaming.generate(foundingCulture,
                            profile.faction() == null ? null : profile.faction().kind(), village.getName()));
            faction = data.faction(faction.id());
        }
        Environment environment = environment(level, sampleAt == null ? BlockPos.ZERO : sampleAt, profile);
        if (profile.faction() == null) {
            retire(data, faction, now);
            if (!faction.kind().equals(PoliticalVillageBootstrap.FREE_SETTLEMENT)) {
                data.putFaction(faction.withKind(PoliticalVillageBootstrap.FREE_SETTLEMENT));
            }
            if (assignVillageCulture && foundingCulture != null) {
                CultureAssignment.assignVillage(level, village.getId(), foundingCulture.toString());
            }
            data.putFounding(new SettlementFoundingRecord(settlement, profile.id(), foundingCulture, null,
                    environment.biome(), environment.naturalWeight(), now));
            return new Result(true, "applied", faction.id(), base.createdFaction(), 0, environment.naturalWeight());
        }
        FactionKind kind = PoliticalDefinitions.snapshot().kind(profile.faction().kind());
        if (kind == null) return Result.failed("faction_kind_missing");
        SettlementFoundingRecord previous = data.founding(settlement);
        boolean sameProfile = previous != null && previous.profile().equals(profile.id());
        boolean adoptGeneratedDefault = previous == null && faction.provenance().equals(VILLAGE_GENERATION)
                && profile.id().equals(DEFAULT_VILLAGE) && faction.kind().equals(kind.id());
        // Only the bundled default profile adopts the council a village was generated with. Any other
        // profile, even one using the same kind, is a real refounding with its own offices.
        boolean replace = !faction.kind().equals(kind.id()) || (!sameProfile && !adoptGeneratedDefault);
        if (replace) {
            retire(data, faction, now);
            data.clearLegitimacy(faction.id());
            faction = faction.withKind(kind.id());
            data.putFaction(faction);
        }
        int filled;
        if (founder != null) {
            filled = seatFounder(data, faction, kind, founder, now);
        } else {
            FactionUpkeep.run(data, faction, kind, residents, List.of(), now);
            filled = FactionUpkeep.fill(data, faction, kind, residents, profile.faction().bundles(), now);
        }
        if (assignVillageCulture && foundingCulture != null) {
            CultureAssignment.assignVillage(level, village.getId(), foundingCulture.toString());
        }
        data.putFounding(new SettlementFoundingRecord(settlement, profile.id(), foundingCulture, kind.id(),
                environment.biome(), environment.naturalWeight(), now));
        return new Result(true, "applied", faction.id(), base.createdFaction(), filled, environment.naturalWeight());
    }

    /** The founder joins, then takes every office the kind marks as the founder's. */
    static int seatFounder(PoliticalSavedData data, Faction faction, FactionKind kind, UUID founder, long now) {
        ResourceLocation membership = kind.membership().bond();
        if (!FactionBonds.member(data, founder, faction.id(), membership)) {
            FactionBonds.form(data, membership, FactionBonds.sides(membership, faction.id(), founder), CHARTER, now);
        }
        int filled = 0;
        for (FactionKind.Office office : kind.offices()) {
            if (!office.founder()) continue;
            if (FactionBonds.form(data, office.bond(), FactionBonds.sides(office.bond(), faction.id(), founder), CHARTER, now).formed()) filled++;
        }
        return filled;
    }

    /** Ends every office in the faction, as when its government is replaced. */
    private static void retire(PoliticalSavedData data, Faction faction, long now) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        for (BondInstance bond : data.activeBonds(Party.faction(faction.id()))) {
            boolean office = kind != null ? kind.office(bond.kind()) != null : !bond.kind().equals(FactionBonds.CITIZENSHIP);
            if (office) FactionBonds.end(data, bond, now, "refounded");
        }
    }

    public static Environment environment(ServerLevel level, BlockPos pos, FoundingProfileDefinition profile) {
        Holder<Biome> biome = level.getBiome(pos);
        ResourceLocation biomeId = biome.unwrapKey().map(ResourceKey::location).orElse(null);
        Set<ResourceLocation> tags = new HashSet<>();
        biome.tags().forEach(tag -> tags.add(tag.location()));
        float bias = profile.spawnBias().weight(biomeId, tags, level.dimension().location());
        return new Environment(biomeId, Math.max(0.0F, profile.weight() * bias));
    }

    public record Environment(ResourceLocation biome, float naturalWeight) {}

    public record Result(boolean applied, String reason, ResourceLocation faction, boolean createdFaction,
                         int officesFilled, float naturalWeight) {
        static Result failed(String reason) {
            return new Result(false, reason, id("townstead:none"), false, 0, 0.0F);
        }
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = DataPackLang.parseId(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
