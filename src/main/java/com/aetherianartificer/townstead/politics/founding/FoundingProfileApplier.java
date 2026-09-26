package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.SettlementNaming;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.definition.MembershipPolicyDefinition;
import com.aetherianartificer.townstead.politics.definition.OrganizationKindDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.AffiliationInstance;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalIds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalStatus;
import com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicitly applies a loaded profile to one MCA village and persists the resolved identity. */
public final class FoundingProfileApplier {
    private static final ResourceLocation MEMBERSHIP = id("townstead:membership");
    private static final ResourceLocation VILLAGE_GENERATION = id("townstead:village_generation");
    private static final ResourceLocation DEFAULT_VILLAGE = id("townstead:default_village");

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
                                BlockPos sampleAt, String requestedName, ResourceLocation foundingCulture, boolean assignVillageCulture, UUID founder) {
        if (level == null || village == null || profile == null) return Result.failed("invalid_request");
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        String settlementName = requestedName == null || requestedName.isBlank()
                ? SettlementNaming.resolve(foundingCulture, village.getName()) : requestedName.trim();
        if (!settlementName.isBlank() && !settlementName.equals(village.getName())) {
            village.setName(settlementName);
        }
        List<UUID> residents = village.getResidentsUUIDs().toList().stream()
                .filter(java.util.Objects::nonNull).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();

        PoliticalVillageBootstrap.Result base = PoliticalVillageBootstrap.ensure(
                data, settlement, village.getName(), residents, level.getGameTime(), profile.government() != null && founder == null, founder != null);
        if (!base.available()) return Result.failed("politics_unavailable");
        if (base.createdPolity()) {
            com.aetherianartificer.townstead.culture.FactionNaming.initialize(data, base.polity(),
                    com.aetherianartificer.townstead.culture.FactionNaming.generate(foundingCulture,
                            profile.government() == null ? null : profile.government().organizationKind(), village.getName()));
        }


        if (profile.government() == null) {
            PolityInstance polity = data.polity(base.polity());
            if (polity == null) return Result.failed("polity_missing");
            ResourceLocation formerGovernment = polity.governmentOrganization();
            if (formerGovernment != null) {
                OrganizationInstance former = data.organization(formerGovernment);
                if (former != null) retire(data, former, level.getGameTime());
            }
            polity = new PolityInstance(polity.id(), polity.name(), polity.color(), polity.emblem(),
                    polity.createdAt(), polity.provenance(), polity.status(), polity.settlements(), null);
            data.putPolity(polity);
            if (assignVillageCulture && foundingCulture != null) {
                CultureAssignment.assignVillage(level, village.getId(), foundingCulture.toString());
            }
            Environment environment = environment(level, sampleAt == null ? BlockPos.ZERO : sampleAt, profile);
            data.putFounding(new SettlementFoundingRecord(settlement, profile.id(), foundingCulture, null,
                    environment.biome(), environment.naturalWeight(), level.getGameTime()));
            return new Result(true, "applied", polity.id(), null, false, 0, environment.naturalWeight());
        }

        OrganizationKindDefinition kind = PoliticalDefinitions.snapshot()
                .organizationKind(profile.government().organizationKind());
        if (kind == null) return Result.failed("government_kind_missing");
        MembershipPolicyDefinition policy = PoliticalDefinitions.snapshot()
                .membershipPolicy(kind.membershipPolicy());
        if (policy == null) return Result.failed("membership_policy_missing");

        PolityInstance polity = data.polity(base.polity());
        if (polity == null) return Result.failed("polity_missing");
        OrganizationInstance current = data.organization(polity.governmentOrganization());
        SettlementFoundingRecord previous = data.founding(settlement);
        boolean sameProfile = previous != null && previous.profile().equals(profile.id());
        boolean adoptGeneratedDefault = previous == null && current != null
                && current.provenance().equals(VILLAGE_GENERATION)
                && profile.id().equals(DEFAULT_VILLAGE) && current.kind().equals(kind.id());
        // Only the bundled default profile adopts the pre-profile generated council. A culture
        // profile using that same organization kind still receives its authored name, seats,
        // provenance, and future instance choices. Changing profiles is always a real refounding.
        boolean replace = current == null || !current.kind().equals(kind.id())
                || (!sameProfile && !adoptGeneratedDefault);
        ResourceLocation governmentId = replace
                ? PoliticalIds.villageGovernment(settlement, profile.id()) : current.id();
        if (founder != null && base.createdPolity() && data.organization(governmentId) != null)
            governmentId = id("townstead:government/" + UUID.randomUUID());
        OrganizationInstance government = data.organization(governmentId);
        boolean createdGovernment = false;
        if (government == null) {
            String name = profile.government().name(polity.name());
            government = new OrganizationInstance(governmentId, kind.id(), kind.membershipPolicy(),
                    name, name, polity.color(), polity.emblem(), level.getGameTime(), profile.id(),
                    PoliticalStatus.Organization.ACTIVE, settlement);
            data.putOrganization(government);
            createdGovernment = true;
        } else if (government.status() != PoliticalStatus.Organization.ACTIVE
                || !government.name().equals(profile.government().name(polity.name()))) {
            String name = profile.government().name(polity.name());
            government = new OrganizationInstance(government.id(), government.kind(),
                    government.membershipPolicy(), name, name,
                    government.color(), government.emblem(), government.createdAt(), government.provenance(),
                    PoliticalStatus.Organization.ACTIVE, government.home());
            data.putOrganization(government);
        }

        if (replace) {
            if (current != null) retire(data, current, level.getGameTime());
            polity = new PolityInstance(polity.id(), polity.name(), polity.color(), polity.emblem(),
                    polity.createdAt(), polity.provenance(), polity.status(), polity.settlements(), governmentId);
            data.putPolity(polity);
        }

        int membersCreated = seedGovernment(data, government, kind, policy, profile, founder == null ? residents : List.of(founder),
                level.getGameTime());
        if (assignVillageCulture && foundingCulture != null) {
            CultureAssignment.assignVillage(level, village.getId(), foundingCulture.toString());
        }

        Environment environment = environment(level, sampleAt == null ? BlockPos.ZERO : sampleAt, profile);
        SettlementFoundingRecord record = new SettlementFoundingRecord(settlement, profile.id(),
                foundingCulture, governmentId, environment.biome(), environment.naturalWeight(),
                level.getGameTime());
        data.putFounding(record);
        return new Result(true, "applied", polity.id(), governmentId, createdGovernment,
                membersCreated, environment.naturalWeight());
    }

    static int seedGovernment(PoliticalSavedData data, OrganizationInstance government,
                                      OrganizationKindDefinition kind, MembershipPolicyDefinition policy,
                                      FoundingProfileDefinition profile, List<UUID> residents, long now) {
        if (residents.isEmpty() || data.memberships(government.actor()).stream()
                .anyMatch(membership -> membership.affiliation().active())) return 0;
        List<FoundingProfileDefinition.Seat> seats = profile.government().seats();
        if (seats.isEmpty()) seats = derivedSeats(kind, policy);
        List<Set<ResourceLocation>> assignments = new ArrayList<>();
        for (FoundingProfileDefinition.Seat seat : seats) {
            for (int count = 0; count < seat.count(); count++) assignments.add(seat.roles());
        }
        int limit = Math.min(residents.size(), assignments.size());
        for (int index = 0; index < limit; index++) {
            AffiliationInstance affiliation = new AffiliationInstance(
                    PoliticalIds.affiliation("townstead"), residents.get(index), government.actor(), MEMBERSHIP,
                    PoliticalStatus.Affiliation.ACTIVE, now, AffiliationInstance.NOT_ENDED, profile.id(),
                    PoliticalStatus.Visibility.PUBLIC);
            data.putMembership(new MembershipInstance(affiliation, policy.id(),
                    policy.admission().procedure(), policy.departure().procedure(), assignments.get(index)));
        }
        return limit;
    }

    private static List<FoundingProfileDefinition.Seat> derivedSeats(
            OrganizationKindDefinition kind, MembershipPolicyDefinition policy) {
        List<FoundingProfileDefinition.Seat> seats = new ArrayList<>();
        for (OrganizationKindDefinition.RoleBinding binding : kind.roles()) {
            if (binding.minimum() > 0) seats.add(new FoundingProfileDefinition.Seat(
                    Set.of(binding.role()), binding.minimum()));
        }
        if (!seats.isEmpty()) return List.copyOf(seats);
        Set<ResourceLocation> initial = new LinkedHashSet<>(policy.admission().initialRoles());
        if (initial.isEmpty() && !kind.roles().isEmpty()) initial.add(kind.roles().get(0).role());
        return initial.isEmpty() ? List.of() : List.of(new FoundingProfileDefinition.Seat(initial, 1));
    }

    private static void retire(PoliticalSavedData data, OrganizationInstance organization, long now) {
        for (MembershipInstance membership : data.memberships(organization.actor())) {
            AffiliationInstance old = membership.affiliation();
            if (!old.active()) continue;
            AffiliationInstance former = new AffiliationInstance(old.id(), old.person(), old.actor(), old.kind(),
                    PoliticalStatus.Affiliation.FORMER, old.startedAt(), now, old.provenance(), old.visibility());
            data.putMembership(new MembershipInstance(former, membership.membershipPolicy(),
                    membership.admissionProcedure(), membership.departureProcedure(), membership.roles()));
        }
        data.putOrganization(new OrganizationInstance(organization.id(), organization.kind(),
                organization.membershipPolicy(), organization.name(), organization.shortName(),
                organization.color(), organization.emblem(), organization.createdAt(), organization.provenance(),
                PoliticalStatus.Organization.DISSOLVED, organization.home()));
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

    public record Result(boolean applied, String reason, ResourceLocation polity,
                         @org.jetbrains.annotations.Nullable ResourceLocation government, boolean createdGovernment,
                         int governmentMembersCreated, float naturalWeight) {
        static Result failed(String reason) {
            ResourceLocation none = id("townstead:none");
            return new Result(false, reason, none, none, false, 0, 0.0F);
        }
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = DataPackLang.parseId(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
