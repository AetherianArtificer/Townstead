package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.PoliticsApi;
import com.aetherianartificer.townstead.api.v1.model.AffiliationSnapshot;
import com.aetherianartificer.townstead.api.v1.model.AuthorityDecision;
import com.aetherianartificer.townstead.api.v1.model.FoundingProfileSnapshot;
import com.aetherianartificer.townstead.api.v1.model.MembershipSnapshot;
import com.aetherianartificer.townstead.api.v1.model.OrganizationSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PoliticalActorRef;
import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.politics.state.AffiliationInstance;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

final class PoliticsImpl implements PoliticsApi {
    @Override
    public Optional<OrganizationSnapshot> organization(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            OrganizationInstance value = PoliticalSavedData.get(server).organization(id);
            return value == null ? Optional.empty() : Optional.of(organization(PoliticalSavedData.get(server), value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.organization", error);
            return Optional.empty();
        }
    }

    @Override
    public List<OrganizationSnapshot> organizations(MinecraftServer server) {
        List<OrganizationSnapshot> out = new ArrayList<>();
        try {
            if (server == null) return out;
            PoliticalSavedData data = PoliticalSavedData.get(server);
            for (OrganizationInstance value : data.organizations()) out.add(organization(data, value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.organizations", error);
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<PolitySnapshot> polity(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            PolityInstance value = PoliticalSavedData.get(server).polity(id);
            return value == null ? Optional.empty() : Optional.of(polity(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.polity", error);
            return Optional.empty();
        }
    }

    @Override
    public Optional<PolitySnapshot> polity(MinecraftServer server, VillageId settlement) {
        try {
            if (server == null || settlement == null) return Optional.empty();
            PolityInstance value = PoliticalSavedData.get(server).polity(
                    new SettlementRef(settlement.dimension(), settlement.villageId()));
            return value == null ? Optional.empty() : Optional.of(polity(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.polityForSettlement", error);
            return Optional.empty();
        }
    }

    @Override
    public List<PolitySnapshot> polities(MinecraftServer server) {
        List<PolitySnapshot> out = new ArrayList<>();
        try {
            if (server == null) return out;
            for (PolityInstance value : PoliticalSavedData.get(server).polities()) out.add(polity(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.polities", error);
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
            return value == null ? Optional.empty() : Optional.of(new SettlementFoundingSnapshot(
                    village(value.settlement()), value.profile(), Optional.ofNullable(value.culture()),
                    Optional.ofNullable(value.government()), Optional.ofNullable(value.foundingBiome()), value.naturalWeight(),
                    value.foundedAt()));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.founding", error);
            return Optional.empty();
        }
    }

    @Override
    public List<AffiliationSnapshot> affiliations(MinecraftServer server, UUID person) {
        List<AffiliationSnapshot> out = new ArrayList<>();
        try {
            if (server == null || person == null) return out;
            PoliticalSavedData data = PoliticalSavedData.get(server);
            for (AffiliationInstance value : data.affiliations(person)) {
                out.add(affiliation(value, data.membership(value.id()) != null));
            }
        } catch (Throwable error) {
            ApiSupport.swallow("politics.affiliations", error);
        }
        return List.copyOf(out);
    }

    @Override
    public Optional<MembershipSnapshot> membership(MinecraftServer server, ResourceLocation id) {
        try {
            if (server == null || id == null) return Optional.empty();
            MembershipInstance value = PoliticalSavedData.get(server).membership(id);
            return value == null ? Optional.empty() : Optional.of(membership(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.membership", error);
            return Optional.empty();
        }
    }

    @Override
    public List<MembershipSnapshot> memberships(MinecraftServer server, UUID person) {
        List<MembershipSnapshot> out = new ArrayList<>();
        try {
            if (server == null || person == null) return out;
            for (MembershipInstance value : PoliticalSavedData.get(server).memberships(person)) out.add(membership(value));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.memberships", error);
        }
        return List.copyOf(out);
    }

    @Override
    public AuthorityDecision mayAct(MinecraftServer server, UUID person, PoliticalActorRef actor,
                                    ResourceLocation capability) {
        try {
            com.aetherianartificer.townstead.politics.state.PoliticalActorRef internal = actor(actor);
            if (server == null || person == null || internal == null || capability == null) {
                return denied("invalid_request");
            }
            PoliticalAuthority.Decision result = PoliticalAuthority.mayAct(
                    PoliticalSavedData.get(server), person, internal, capability);
            return new AuthorityDecision(result.allowed(), result.reason(), Optional.ofNullable(result.grantingRole()));
        } catch (Throwable error) {
            ApiSupport.swallow("politics.mayAct", error);
            return denied("internal_error");
        }
    }

    private static OrganizationSnapshot organization(PoliticalSavedData data, OrganizationInstance value) {
        int members = (int) data.memberships(value.actor()).stream()
                .filter(membership -> membership.affiliation().active()).count();
        return new OrganizationSnapshot(value.id(), value.kind(), value.membershipPolicy(), value.name(),
                value.shortName(), value.color(), Optional.ofNullable(value.emblem()), value.createdAt(),
                value.provenance(), value.status().id(), Optional.ofNullable(village(value.home())), members);
    }

    private static PolitySnapshot polity(PolityInstance value) {
        List<VillageId> settlements = value.settlements().stream().map(PoliticsImpl::village).toList();
        return new PolitySnapshot(value.id(), value.name(), value.color(), Optional.ofNullable(value.emblem()),
                value.createdAt(), value.provenance(), value.status().id(), settlements,
                Optional.ofNullable(value.governmentOrganization()));
    }

    private static FoundingProfileSnapshot foundingProfile(FoundingProfileDefinition value) {
        FoundingProfileDefinition.Government government = value.government();
        List<FoundingProfileSnapshot.GovernmentSeat> seats = government == null ? List.of()
                : government.seats().stream()
                .map(seat -> new FoundingProfileSnapshot.GovernmentSeat(seat.roles(), seat.count())).toList();
        return new FoundingProfileSnapshot(value.id(), value.displayName().getString(),
                Optional.ofNullable(value.culture()), value.weight(),
                Optional.ofNullable(value.spawnBias().defaultWeight()), value.spawnBias().biomes(),
                value.spawnBias().biomeTags(), value.spawnBias().dimensions(), value.population().strategy(),
                value.population().outsiderBaseline(), value.population().adjustments(),
                Optional.ofNullable(government == null ? null : government.organizationKind()),
                Optional.ofNullable(government == null ? null : government.namePattern()), seats);
    }

    private static AffiliationSnapshot affiliation(AffiliationInstance value, boolean membership) {
        OptionalLong ended = value.endedAt() == AffiliationInstance.NOT_ENDED
                ? OptionalLong.empty() : OptionalLong.of(value.endedAt());
        return new AffiliationSnapshot(value.id(), value.person(), actor(value.actor()), value.kind(),
                value.status().id(), value.startedAt(), ended, value.provenance(), value.visibility().id(), membership);
    }

    private static MembershipSnapshot membership(MembershipInstance value) {
        return new MembershipSnapshot(affiliation(value.affiliation(), true), value.membershipPolicy(),
                value.admissionProcedure(), value.departureProcedure(), value.roles());
    }

    private static PoliticalActorRef actor(
            com.aetherianartificer.townstead.politics.state.PoliticalActorRef value) {
        return new PoliticalActorRef(value.kind().id(), value.id());
    }

    private static com.aetherianartificer.townstead.politics.state.PoliticalActorRef actor(PoliticalActorRef value) {
        if (value == null) return null;
        com.aetherianartificer.townstead.politics.state.PoliticalActorRef.Kind kind;
        if (PoliticalActorRef.ORGANIZATION.equals(value.kind())) {
            kind = com.aetherianartificer.townstead.politics.state.PoliticalActorRef.Kind.ORGANIZATION;
        } else if (PoliticalActorRef.POLITY.equals(value.kind())) {
            kind = com.aetherianartificer.townstead.politics.state.PoliticalActorRef.Kind.POLITY;
        } else return null;
        return new com.aetherianartificer.townstead.politics.state.PoliticalActorRef(kind, value.id());
    }

    private static VillageId village(SettlementRef value) {
        return value == null ? null : new VillageId(value.dimension(), value.villageId());
    }

    private static AuthorityDecision denied(String path) {
        return new AuthorityDecision(false, ResourceLocation.tryParse("townstead:" + path), Optional.empty());
    }
}
