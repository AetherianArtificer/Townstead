package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.definition.MembershipPolicyDefinition;
import com.aetherianartificer.townstead.politics.definition.OrganizationKindDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Creates or recovers the first civic identity attached to an MCA village. */
public final class PoliticalVillageBootstrap {
    private static final ResourceLocation VILLAGE_COUNCIL = id("townstead:village_council");
    private static final ResourceLocation APPOINTED_COUNCIL = id("townstead:appointed_council");
    private static final ResourceLocation COUNCILOR = id("townstead:councilor");
    private static final ResourceLocation PRESIDING_COUNCILOR = id("townstead:presiding_councilor");
    private static final ResourceLocation MEMBERSHIP = id("townstead:membership");
    private static final ResourceLocation RESIDENCE = id("townstead:residence");
    private static final ResourceLocation GENERATION = id("townstead:village_generation");
    private static final int INITIAL_COUNCIL_SIZE = 3;
    private static boolean warnedMissingDefinitions;

    private PoliticalVillageBootstrap() {}

    public static Result ensure(ServerLevel level, Village village) {
        if (level == null || village == null) return Result.EMPTY;
        var data = PoliticalSavedData.get(level.getServer());
        var result = ensure(data, new SettlementRef(level.dimension().location(), village.getId()), village.getName(),
                village.getResidentsUUIDs().toList(), level.getGameTime());
        if (result.createdPolity()) {
            String culture = com.aetherianartificer.townstead.naming.NamingRegisterSavedData.get(level.getServer())
                    .villageCulture(level.dimension().location(), village.getId());
            com.aetherianartificer.townstead.culture.FactionNaming.initialize(data, result.polity(),
                    com.aetherianartificer.townstead.culture.FactionNaming.generate(ResourceLocation.tryParse(culture),
                            VILLAGE_COUNCIL, village.getName()));
        }
        return result;
    }

    /** Pure of MCA/server access so migrations and tests use exactly the live founding routine. */
    public static Result ensure(PoliticalSavedData data, SettlementRef settlement, String villageName,
                                Collection<UUID> residentIds, long now) {
        return ensure(data, settlement, villageName, residentIds, now, true);
    }

    /** Establishes the settlement polity and residences, optionally without inventing a government. */
    public static Result ensure(PoliticalSavedData data, SettlementRef settlement, String villageName,
                                Collection<UUID> residentIds, long now, boolean createDefaultGovernment) {
        return ensure(data, settlement, villageName, residentIds, now, createDefaultGovernment, false);
    }

    public static Result ensure(PoliticalSavedData data, SettlementRef settlement, String villageName,
                                Collection<UUID> residentIds, long now, boolean createDefaultGovernment, boolean explicitRefounding) {
        if (data == null || settlement == null || (createDefaultGovernment && !definitionsReady())) {
            return Result.EMPTY;
        }
        String name = villageName == null || villageName.isBlank()
                ? "Village " + settlement.villageId() : villageName.trim();
        ResourceLocation polityId = PoliticalIds.villagePolity(settlement);
        ResourceLocation councilId = PoliticalIds.villageCouncil(settlement);
        boolean createdPolity = false, createdCouncil = false;

        PolityInstance polity = data.polity(settlement);
        // A dissolved faction suppresses automatic recreation. Only explicit player founding
        // may establish a fresh identity at this settlement.
        if (polity != null && polity.status() == PoliticalStatus.Polity.DISSOLVED) {
            if (!explicitRefounding) return Result.EMPTY;
            polity = null;
            polityId = id("townstead:polity/" + UUID.randomUUID());
        }
        ResourceLocation governmentId = null;
        if (polity == null) {
            if (createDefaultGovernment) {
                OrganizationInstance council = data.organization(councilId);
                if (council == null) {
                    council = council(councilId, name, settlement, now);
                    data.putOrganization(council);
                    createdCouncil = true;
                }
                governmentId = council.id();
            }
            polity = new PolityInstance(polityId, name, color(settlement), null, now, GENERATION,
                    PoliticalStatus.Polity.ACTIVE, List.of(settlement), governmentId);
            data.putPolity(polity);
            createdPolity = true;
        } else if (createDefaultGovernment && polity.governmentOrganization() == null) {
            OrganizationInstance council = data.organization(councilId);
            if (council == null) {
                council = council(councilId, name, settlement, now);
                data.putOrganization(council);
                createdCouncil = true;
            }
            governmentId = council.id();
            polity = new PolityInstance(polity.id(), polity.name(), polity.color(), polity.emblem(),
                    polity.createdAt(), polity.provenance(), polity.status(), polity.settlements(), governmentId);
            data.putPolity(polity);
        } else {
            governmentId = polity.governmentOrganization();
        }

        List<UUID> residents = residentIds == null ? List.of() : residentIds.stream()
                .filter(java.util.Objects::nonNull).distinct()
                .sorted(Comparator.comparing(UUID::toString)).toList();
        int residencesCreated = ensureResidences(data, polity.actor(), residents, now);
        int councilorsCreated = 0;
        OrganizationInstance government = governmentId == null ? null : data.organization(governmentId);
        if (government != null && government.kind().equals(VILLAGE_COUNCIL)
                && data.memberships(government.actor()).isEmpty()) {
            councilorsCreated = foundCouncil(data, government, residents, now);
        }
        return new Result(polity.id(), governmentId, createdPolity, createdCouncil,
                residencesCreated, councilorsCreated);
    }

    private static boolean definitionsReady() {
        PoliticalDefinitions.Snapshot definitions = PoliticalDefinitions.snapshot();
        OrganizationKindDefinition kind = definitions.organizationKind(VILLAGE_COUNCIL);
        MembershipPolicyDefinition policy = definitions.membershipPolicy(APPOINTED_COUNCIL);
        boolean ready = kind != null && kind.membershipPolicy().equals(APPOINTED_COUNCIL)
                && policy != null && definitions.role(COUNCILOR) != null
                && definitions.role(PRESIDING_COUNCILOR) != null;
        if (!ready && !warnedMissingDefinitions) {
            warnedMissingDefinitions = true;
            Townstead.LOGGER.warn("Village politics bootstrap skipped: starter council definitions are unavailable");
        }
        if (ready) warnedMissingDefinitions = false;
        return ready;
    }

    private static OrganizationInstance council(ResourceLocation id, String villageName,
                                                 SettlementRef settlement, long now) {
        OrganizationKindDefinition kind = PoliticalDefinitions.snapshot().organizationKind(VILLAGE_COUNCIL);
        return new OrganizationInstance(id, VILLAGE_COUNCIL, kind.membershipPolicy(),
                villageName + " Council", villageName + " Council", color(settlement), null, now,
                GENERATION, PoliticalStatus.Organization.ACTIVE, settlement);
    }

    private static int ensureResidences(PoliticalSavedData data, PoliticalActorRef polity,
                                        List<UUID> residents, long now) {
        int created = 0;
        for (UUID resident : residents) {
            boolean present = false;
            for (AffiliationInstance affiliation : data.affiliations(resident)) {
                if (!affiliation.kind().equals(RESIDENCE) || !affiliation.active()) continue;
                if (affiliation.actor().equals(polity)) {
                    present = true;
                } else {
                    data.putAffiliation(new AffiliationInstance(affiliation.id(), affiliation.person(),
                            affiliation.actor(), affiliation.kind(), PoliticalStatus.Affiliation.FORMER,
                            affiliation.startedAt(), now, affiliation.provenance(), affiliation.visibility()));
                }
            }
            if (!present) {
                data.putAffiliation(new AffiliationInstance(PoliticalIds.affiliation("townstead"), resident,
                        polity, RESIDENCE, PoliticalStatus.Affiliation.ACTIVE, now,
                        AffiliationInstance.NOT_ENDED, GENERATION, PoliticalStatus.Visibility.PUBLIC));
                created++;
            }
        }
        return created;
    }

    private static int foundCouncil(PoliticalSavedData data, OrganizationInstance council,
                                    List<UUID> residents, long now) {
        MembershipPolicyDefinition policy = PoliticalDefinitions.snapshot().membershipPolicy(APPOINTED_COUNCIL);
        int seats = Math.min(INITIAL_COUNCIL_SIZE, residents.size());
        for (int index = 0; index < seats; index++) {
            Set<ResourceLocation> roles = new LinkedHashSet<>();
            roles.add(COUNCILOR);
            if (index == 0) roles.add(PRESIDING_COUNCILOR);
            AffiliationInstance affiliation = new AffiliationInstance(PoliticalIds.affiliation("townstead"),
                    residents.get(index), council.actor(), MEMBERSHIP, PoliticalStatus.Affiliation.ACTIVE,
                    now, AffiliationInstance.NOT_ENDED, GENERATION, PoliticalStatus.Visibility.PUBLIC);
            data.putMembership(new MembershipInstance(affiliation, APPOINTED_COUNCIL,
                    policy.admission().procedure(), policy.departure().procedure(), roles));
        }
        return seats;
    }

    private static int color(SettlementRef settlement) {
        int hash = 31 * settlement.dimension().hashCode() + settlement.villageId();
        return 0x404040 | (hash & 0xBFBFBF);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = DataPackLang.parseId(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }

    public record Result(ResourceLocation polity, @org.jetbrains.annotations.Nullable ResourceLocation government,
                         boolean createdPolity, boolean createdGovernment,
                         int residencesCreated, int councilorsCreated) {
        private static final Result EMPTY = new Result(id("townstead:none"), id("townstead:none"),
                false, false, 0, 0);

        public boolean available() {
            return !polity.equals(EMPTY.polity);
        }
    }
}
