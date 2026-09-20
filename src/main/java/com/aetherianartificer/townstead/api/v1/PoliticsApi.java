package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.AffiliationSnapshot;
import com.aetherianartificer.townstead.api.v1.model.AuthorityDecision;
import com.aetherianartificer.townstead.api.v1.model.MembershipSnapshot;
import com.aetherianartificer.townstead.api.v1.model.FoundingProfileSnapshot;
import com.aetherianartificer.townstead.api.v1.model.OrganizationSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PoliticalActorRef;
import com.aetherianartificer.townstead.api.v1.model.PolitySnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only political identities, affiliations, memberships, and scoped authority. */
public interface PoliticsApi {
    PoliticsApi EMPTY = new PoliticsApi() {};

    default Optional<OrganizationSnapshot> organization(MinecraftServer server, ResourceLocation id) {
        return Optional.empty();
    }

    default List<OrganizationSnapshot> organizations(MinecraftServer server) {
        return List.of();
    }

    default Optional<PolitySnapshot> polity(MinecraftServer server, ResourceLocation id) {
        return Optional.empty();
    }

    /** The polity currently containing this recognized MCA settlement. */
    default Optional<PolitySnapshot> polity(MinecraftServer server, VillageId settlement) {
        return Optional.empty();
    }

    default List<PolitySnapshot> polities(MinecraftServer server) {
        return List.of();
    }

    /** Every coherent data-pack founding profile currently loaded by the server. */
    default List<FoundingProfileSnapshot> foundingProfiles() {
        return List.of();
    }

    default Optional<FoundingProfileSnapshot> foundingProfile(ResourceLocation id) {
        return Optional.empty();
    }

    /** The persisted profile result for one recognized settlement, if it was explicitly founded. */
    default Optional<SettlementFoundingSnapshot> founding(MinecraftServer server, VillageId settlement) {
        return Optional.empty();
    }

    /** Includes membership affiliations as well as residence, citizenship, service, and contracts. */
    default List<AffiliationSnapshot> affiliations(MinecraftServer server, UUID person) {
        return List.of();
    }

    default Optional<MembershipSnapshot> membership(MinecraftServer server, ResourceLocation id) {
        return Optional.empty();
    }

    default List<MembershipSnapshot> memberships(MinecraftServer server, UUID person) {
        return List.of();
    }

    /**
     * Tests one capability in one explicitly selected acting capacity. Membership or roles in
     * every other organization are ignored. A polity delegates through its bound government.
     */
    default AuthorityDecision mayAct(MinecraftServer server, UUID person, PoliticalActorRef actor,
                                     ResourceLocation capability) {
        return new AuthorityDecision(false, ResourceLocation.tryParse("townstead:api_unavailable"), Optional.empty());
    }
}
