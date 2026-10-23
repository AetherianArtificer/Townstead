package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.PoliticsApi;
import com.aetherianartificer.townstead.api.v1.model.AuthorityDecision;
import com.aetherianartificer.townstead.api.v1.model.BondSnapshot;
import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import com.aetherianartificer.townstead.api.v1.model.FoundingProfileSnapshot;
import com.aetherianartificer.townstead.api.v1.model.GovernanceSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PartyRef;
import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import com.aetherianartificer.townstead.politics.land.FactionLand;
import com.aetherianartificer.townstead.politics.legitimacy.LegitimacyService;
import com.aetherianartificer.townstead.politics.seat.SeatBuildings;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

final class PoliticsImpl implements PoliticsApi {
    @Override
    public Optional<FactionSnapshot> faction(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            PoliticalSavedData data = PoliticalSavedData.get(server);
            Faction value = data.faction(id);
            return value == null ? Optional.empty() : Optional.of(faction(data, value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.faction", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<FactionSnapshot> faction(MinecraftServer server, VillageId settlement) {
        try {
            if (server == null || settlement == null) return Optional.empty();
            PoliticalSavedData data = PoliticalSavedData.get(server);
            Faction value = data.faction(new SettlementRef(settlement.dimension(), settlement.villageId()));
            return value == null ? Optional.empty() : Optional.of(faction(data, value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.factionForSettlement", error);
            return Optional.empty();
        }
    }

    @Override
    public List<FactionSnapshot> factions(MinecraftServer server) {
        List<FactionSnapshot> out = new ArrayList<>();
        try {
            if (server == null) return out;
            PoliticalSavedData data = PoliticalSavedData.get(server);
            for (Faction value : data.factions()) out.add(faction(data, value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.factions", error);
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<ResourceLocation> landHolder(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        try {
            return FactionLand.holder(server, dimension, pos);
        } catch (Throwable error) {
            ApiSupport.swallow("politics.landHolder", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<ResourceLocation> sovereign(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            PoliticalSavedData data = PoliticalSavedData.get(server);
            Faction value = data.faction(id);
            return value == null ? Optional.empty() : Optional.of(FactionBonds.sovereign(data, value.id()));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.sovereign", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<BondSnapshot> bond(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            BondInstance value = PoliticalSavedData.get(server).bond(id);
            return value == null ? Optional.empty() : Optional.of(bond(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.bond", error);
            return Optional.empty();
        }
    }

    @Override
    public List<BondSnapshot> bonds(MinecraftServer server, PartyRef party) {
        List<BondSnapshot> out = new ArrayList<>();
        try {
            Party internal = party(party);
            if (server == null || internal == null) return out;
            for (BondInstance value : PoliticalSavedData.get(server).bonds(internal)) out.add(bond(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.bonds", error);
        }
        return List.copyOf(out);
    }

    @Override
    public List<FoundingProfileSnapshot> foundingProfiles() {
        List<FoundingProfileSnapshot> out = new ArrayList<>();
        try {
            for (FoundingProfileDefinition value : FoundingProfiles.all()) out.add(foundingProfile(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.foundingProfiles", error);
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<FoundingProfileSnapshot> foundingProfile(ResourceLocation id) {
        try {
            FoundingProfileDefinition value = FoundingProfiles.get(id);
            return value == null ? Optional.empty() : Optional.of(foundingProfile(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.foundingProfile", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<SettlementFoundingSnapshot> founding(MinecraftServer server, VillageId settlement) {
        try {
            if (server == null || settlement == null) return Optional.empty();
            SettlementFoundingRecord value = PoliticalSavedData.get(server).founding(
                    new SettlementRef(settlement.dimension(), settlement.villageId()));
            return value == null ? Optional.empty() : Optional.of(founding(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.founding", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<GovernanceSnapshot> governance(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            PoliticalSavedData data = PoliticalSavedData.get(server);
            Faction faction = data.faction(id);
            if (faction == null) return Optional.empty();
            FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
            var governance = kind == null ? null : kind.governance();
            List<GovernanceSnapshot.Office> offices = new ArrayList<>();
            if (kind != null) {
                for (FactionKind.Office office : kind.offices()) {
                    offices.add(new GovernanceSnapshot.Office(office.bond(), office.minimum(), office.maximum(),
                            FactionBonds.holders(data, faction.id(), office.bond())));
                }
            }
            OptionalInt legitimacy = OptionalInt.empty();
            Optional<String> band = Optional.empty();
            Optional<UUID> head = Optional.empty();
            if (governance != null) {
                double value = LegitimacyService.current(data, faction);
                legitimacy = OptionalInt.of((int) Math.round(value));
                band = Optional.of(LegitimacyService.band(value));
                head = FactionBonds.holders(data, faction.id(), governance.head()).stream().findFirst();
            }
            Optional<String> provider = Optional.empty();
            for (SettlementRef settlement : faction.settlements()) {
                provider = com.aetherianartificer.townstead.politics.charter.CivicProviders.governingProvider(server, settlement);
                if (provider.isPresent()) break;
            }
            return Optional.of(new GovernanceSnapshot(faction.id(), faction.kind(),
                    Optional.ofNullable(governance == null ? null : governance.head()), head, offices, legitimacy, band,
                    Optional.ofNullable(governance == null ? null : governance.succession()), provider));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.governance", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<SeatSnapshot> seat(MinecraftServer server, ResourceLocation faction) {
        try {
            if (server == null || faction == null) return Optional.empty();
            SeatInstance value = PoliticalSavedData.get(server).seat(faction);
            return value == null ? Optional.empty() : Optional.of(seat(server, value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.seat", error);
            return Optional.empty();
        }
    }

    @Override
    public List<SeatSnapshot> seats(MinecraftServer server, VillageId settlement) {
        List<SeatSnapshot> out = new ArrayList<>();
        try {
            if (server == null || settlement == null) return out;
            SettlementRef ref = new SettlementRef(settlement.dimension(), settlement.villageId());
            for (SeatInstance value : PoliticalSavedData.get(server).seats()) {
                if (value.settlement().equals(ref)) out.add(seat(server, value));
            }
        } catch (Throwable error) {
            ApiSupport.swallow("politics.seats", error);
        }
        return List.copyOf(out);
    }

    @Override
    public AuthorityDecision mayAct(MinecraftServer server, UUID person, ResourceLocation faction,
                                    ResourceLocation capability) {
        try {
            if (server == null || person == null || faction == null || capability == null) return denied("invalid_request");
            PoliticalAuthority.Decision result = PoliticalAuthority.mayAct(PoliticalSavedData.get(server), person, faction, capability);
            return new AuthorityDecision(result.allowed(), result.reason(), Optional.ofNullable(result.grantingBond()));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.mayAct", error);
            return denied("internal_error");
        }
    }

    static FactionSnapshot faction(PoliticalSavedData data, Faction value) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(value.kind());
        ResourceLocation membership = kind == null ? FactionBonds.CITIZENSHIP : kind.membership().bond();
        int members = FactionBonds.holders(data, value.id(), membership).size();
        return new FactionSnapshot(value.id(), value.kind(), value.name(), value.color(), Optional.ofNullable(value.emblem()),
                value.createdAt(), value.provenance(), value.status().id(),
                value.settlements().stream().map(PoliticsImpl::village).toList(), Optional.ofNullable(village(value.home())),
                Optional.ofNullable(FactionBonds.parent(data, value.id())),
                kind == null ? !value.settlements().isEmpty() : kind.holdsLand(), members);
    }

    static BondSnapshot bond(BondInstance value) {
        List<BondSnapshot.Side> sides = value.sides().stream()
                .map(side -> new BondSnapshot.Side(side.role(), party(side.party()))).toList();
        return new BondSnapshot(value.id(), value.kind(), sides, value.startedAt(),
                value.active() ? OptionalLong.empty() : OptionalLong.of(value.endedAt()), value.endedBy(), value.provenance());
    }

    static SeatSnapshot seat(MinecraftServer server, SeatInstance value) {
        VillageId village = village(value.settlement());
        String type = ApiSupport.findVillage(server, village)
                .map(v -> McaBuildings.byId(v, value.buildingId()))
                .map(building -> building.getType())
                .orElse("");
        SeatBuildings.Spec spec = SeatBuildings.forType(type);
        return new SeatSnapshot(value.faction(), village, value.lectern(), value.buildingId(), type,
                spec.tier(), value.damaged() ? List.of() : spec.functions(), value.designatedAt(), value.damage());
    }

    static SettlementFoundingSnapshot founding(SettlementFoundingRecord value) {
        return new SettlementFoundingSnapshot(village(value.settlement()), value.profile(),
                Optional.ofNullable(value.culture()), Optional.ofNullable(value.factionKind()),
                Optional.ofNullable(value.foundingBiome()), value.naturalWeight(), value.foundedAt());
    }

    private static FoundingProfileSnapshot foundingProfile(FoundingProfileDefinition value) {
        FoundingProfileDefinition.FactionSpec spec = value.faction();
        List<FoundingProfileSnapshot.Seat> seats = spec == null ? List.of()
                : spec.seats().stream().map(seat -> new FoundingProfileSnapshot.Seat(seat.offices(), seat.count())).toList();
        return new FoundingProfileSnapshot(value.id(), value.displayName().getString(),
                Optional.ofNullable(value.culture()), value.weight(),
                Optional.ofNullable(value.spawnBias().defaultWeight()), value.spawnBias().biomes(),
                value.spawnBias().biomeTags(), value.spawnBias().dimensions(), value.population().strategy(),
                value.population().outsiderBaseline(), value.population().adjustments(),
                Optional.ofNullable(spec == null ? null : spec.kind()), seats);
    }

    static PartyRef party(Party value) {
        return value.isPerson() ? new PartyRef(PartyRef.PERSON, value.id()) : new PartyRef(PartyRef.FACTION, value.id());
    }

    private static Party party(PartyRef value) {
        if (value == null) return null;
        if (value.asPerson().isPresent()) return Party.person(value.asPerson().get());
        return value.asFaction().map(Party::faction).orElse(null);
    }

    static VillageId village(SettlementRef value) {
        return value == null ? null : new VillageId(value.dimension(), value.villageId());
    }

    private static AuthorityDecision denied(String path) {
        return new AuthorityDecision(false, ResourceLocation.tryParse("townstead:" + path), Optional.empty());
    }
}
