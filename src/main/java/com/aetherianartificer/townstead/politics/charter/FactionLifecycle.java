package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** What proclaimed amendments do to a faction's head and its existence. */
public final class FactionLifecycle {
    private FactionLifecycle() {}

    /** The head office passes from its current holders to {@code recipient}. */
    public static boolean transferHead(PoliticalSavedData data, Faction faction, UUID recipient, long now) {
        ResourceLocation head = CharterDrafts.headOffice(faction);
        if (head == null || recipient == null || !FactionBonds.eligible(data, head, faction.id(), recipient)
                || FactionBonds.holders(data, faction.id(), head).contains(recipient)) return false;
        for (BondInstance bond : data.activeBonds(Party.faction(faction.id()))) {
            if (bond.kind().equals(head)) FactionBonds.end(data, bond, now, "transferred");
        }
        return FactionBonds.form(data, head, FactionBonds.sides(head, faction.id(), recipient),
                ResourceLocation.tryParse("townstead:charter"), now).formed();
    }

    /** Dissolves the faction, writes it into the chronicle, and unbinds its Charters. */
    public static void dissolve(ServerPlayer player, Faction faction, CharterSavedData.Draft draft) {
        var history = FactionChronicles.dissolution(player, faction, draft.dimension(), draft.bell());
        dissolve(PoliticalSavedData.get(player.server), CharterRequests.get(player.server), faction.id(),
                player.serverLevel().getGameTime());
        FactionChronicles.record(player, history);
        CharterSavedData saved = CharterSavedData.get(player.server);
        for (var binding : saved.unbind(faction.id())) {
            var level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, binding.dimension()));
            if (level != null && level.isLoaded(binding.lectern())) CharterBellService.setLecternState(level, binding.lectern(), CharterLecternAccess.NONE);
        }
        player.server.getPlayerList().broadcastSystemMessage(
                Component.translatable("charter.townstead.lifecycle.dissolved_announcement", faction.name()), false);
    }

    /** Ends every bond the faction is part of, withdraws its open requests, and marks it dissolved. */
    public static boolean dissolve(PoliticalSavedData data, CharterRequests requests, ResourceLocation id, long now) {
        Faction faction = data.faction(id);
        if (faction == null || faction.status() == Faction.Status.DISSOLVED) return false;
        FactionBonds.endAll(data, Party.faction(faction.id()), now, "dissolved");
        for (var request : requests.entries()) {
            if (request.open() && request.faction().equals(faction.id().toString())) requests.put(request.state("withdrawn"));
        }
        data.putFaction(faction.withStatus(Faction.Status.DISSOLVED));
        return true;
    }
}
