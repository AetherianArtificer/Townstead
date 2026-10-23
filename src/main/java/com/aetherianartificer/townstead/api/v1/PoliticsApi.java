package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.AuthorityDecision;
import com.aetherianartificer.townstead.api.v1.model.BondSnapshot;
import com.aetherianartificer.townstead.api.v1.model.FactionSnapshot;
import com.aetherianartificer.townstead.api.v1.model.FoundingProfileSnapshot;
import com.aetherianartificer.townstead.api.v1.model.GovernanceSnapshot;
import com.aetherianartificer.townstead.api.v1.model.PartyRef;
import com.aetherianartificer.townstead.api.v1.model.SeatSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SettlementFoundingSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only factions, the bonds between parties, Seats, land, and faction-scoped authority. */
public interface PoliticsApi {
    PoliticsApi EMPTY = new PoliticsApi() {};

    default Optional<FactionSnapshot> faction(MinecraftServer server, ResourceLocation id) {
        return Optional.empty();
    }

    /** The faction holding this recognized MCA settlement. */
    default Optional<FactionSnapshot> faction(MinecraftServer server, VillageId settlement) {
        return Optional.empty();
    }

    default List<FactionSnapshot> factions(MinecraftServer server) {
        return List.of();
    }

    /**
     * The faction that holds this position: the deepest holder, such as a guild whose Headquarters
     * stands there before the village around it. Empty outside every faction's land.
     */
    default Optional<ResourceLocation> landHolder(MinecraftServer server, ResourceLocation dimension, BlockPos pos) {
        return Optional.empty();
    }

    /** The top of a faction's chain of parents; the faction itself when it is sovereign. */
    default Optional<ResourceLocation> sovereign(MinecraftServer server, ResourceLocation faction) {
        return Optional.empty();
    }

    default Optional<BondSnapshot> bond(MinecraftServer server, ResourceLocation id) {
        return Optional.empty();
    }

    /** Every bond, active or ended, that this party is one side of. */
    default List<BondSnapshot> bonds(MinecraftServer server, PartyRef party) {
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

    /**
     * Who governs this faction and how firmly: the form, the head and every office with its
     * holders, legitimacy, and the outside mod that governs it instead, if any.
     */
    default Optional<GovernanceSnapshot> governance(MinecraftServer server, ResourceLocation faction) {
        return Optional.empty();
    }

    /** The Seat this faction governs from. */
    default Optional<SeatSnapshot> seat(MinecraftServer server, ResourceLocation faction) {
        return Optional.empty();
    }

    /** Every Seat inside one settlement. A building can hold the Seats of several factions. */
    default List<SeatSnapshot> seats(MinecraftServer server, VillageId settlement) {
        return List.of();
    }

    /**
     * Whether this person may use one capability for one faction: they hold an active bond with it,
     * such as an office, whose faction role gives the capability. Bonds with every other faction
     * are ignored.
     */
    default AuthorityDecision mayAct(MinecraftServer server, UUID person, ResourceLocation faction,
                                     ResourceLocation capability) {
        return new AuthorityDecision(false, ResourceLocation.tryParse("townstead:api_unavailable"), Optional.empty());
    }
}
