package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** Native faction amendments are prepared at the lectern and proclaimed by their author at its bell. */
public final class FactionLifecycle {
    private FactionLifecycle() {}
    public static boolean leader(PoliticalSavedData data, PolityInstance polity, UUID person) {
        if (polity == null || polity.status() != PoliticalStatus.Polity.ACTIVE || data.externalGovernment(polity.id())) return false;
        var org = data.organization(polity.governmentOrganization());
        return org != null && org.status() == PoliticalStatus.Organization.ACTIVE && !data.supersededGovernment(org.id())
                && CharterMemberships.isFactionLeader(org, data.membership(person, org.id()));
    }
    public static Component prepare(ServerPlayer player, CharterSavedData.Binding binding, String operation, String expected, String argument) {
        var data = PoliticalSavedData.get(player.server); var polity = data.polity(binding.polity());
        if (!leader(data, polity, player.getUUID()) || polity.settlements().stream().anyMatch(s -> CivicProviders.ownsGovernment(player.server, s))) return message("denied");
        var saved = CharterSavedData.get(player.server);
        if (operation.equals("cancel_amendment")) {
            var pending = saved.amendment(polity.id());
            if (pending != null) saved.removeAmendment(pending);
            return message("cancelled");
        }
        if (!polity.name().equals(expected)) return message("stale");
        if (operation.equals("rename_polity")) {
            argument = CharterIdentityService.normalize(argument);
            if (argument == null) return message("invalid");
        } else if (operation.equals("transfer_leadership")) {
            var recipient = player.server.getPlayerList().getPlayerByName(argument);
            if (recipient == null || recipient.getUUID().equals(player.getUUID())) return message("recipient");
            var member = data.membership(recipient.getUUID(), polity.governmentOrganization());
            if (member == null || !member.affiliation().active()) return message("recipient");
            argument = recipient.getUUID().toString();
        } else if (!operation.equals("dissolve_faction")) return message("denied");
        saved.expire(player.serverLevel().getGameTime());
        if (saved.amendment(polity.id()) != null) return message("pending");
        saved.prepareAmendment(new CharterSavedData.Amendment(UUID.randomUUID(), player.getUUID(), polity.id(), binding.dimension(),
                binding.lectern(), binding.bell(), operation, expected, argument, player.serverLevel().getGameTime() + 12000));
        return message("prepared");
    }
    public static Component commit(ServerPlayer player, CharterSavedData.Amendment amendment) {
        var saved = CharterSavedData.get(player.server); var data = PoliticalSavedData.get(player.server);
        var polity = data.polity(amendment.polity());
        if (!amendment.initiator().equals(player.getUUID()) || amendment.expiresAt() <= player.serverLevel().getGameTime()
                || !leader(data, polity, player.getUUID()) || !polity.name().equals(amendment.expectedName())
                || polity.settlements().stream().anyMatch(s -> CivicProviders.ownsGovernment(player.server, s))) {
            saved.removeAmendment(amendment); return message("stale");
        }
        if (!saved.removeAmendment(amendment)) return message("stale");
        if (amendment.operation().equals("rename_polity"))
            return Component.translatable("charter.townstead.identity." + CharterIdentityService.rename(data, polity.id(), amendment.expectedName(), amendment.argument()));
        if (amendment.operation().equals("transfer_leadership")) {
            UUID recipient;
            try { recipient = UUID.fromString(amendment.argument()); } catch (IllegalArgumentException e) { return message("recipient"); }
            return message(CharterMemberships.transferLeadership(data, data.organization(polity.governmentOrganization()), player.getUUID(), recipient) ? "transferred" : "recipient");
        }
        if (!amendment.operation().equals("dissolve_faction")) return message("denied");
        var history = FactionChronicles.dissolution(player, polity, amendment);
        if (!dissolve(data, CharterRequests.get(player.server), polity.id(), player.getUUID(), player.serverLevel().getGameTime())) return message("denied");
        FactionChronicles.record(player, history);
        for (var binding : saved.unbind(polity.id())) {
            var level = player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, binding.dimension()));
            if (level != null && level.isLoaded(binding.lectern())) CharterBellService.setLecternState(level, binding.lectern(), CharterLecternAccess.NONE);
        }
        player.server.getPlayerList().broadcastSystemMessage(Component.translatable("charter.townstead.lifecycle.dissolved_announcement", polity.name()), false);
        return message("dissolved");
    }
    public static boolean dissolve(PoliticalSavedData data, CharterRequests requests, ResourceLocation id, UUID actor, long now) {
        var polity = data.polity(id);
        if (!leader(data, polity, actor)) return false;
        var org = data.organization(polity.governmentOrganization());
        for (var member : data.memberships(org.actor())) {
            var status = member.affiliation().status();
            if (status != PoliticalStatus.Affiliation.FORMER && status != PoliticalStatus.Affiliation.REJECTED) CharterMemberships.end(data, member, now);
        }
        for (var a : data.directAffiliations()) if (a.actor().equals(polity.actor())
                && a.status() != PoliticalStatus.Affiliation.FORMER && a.status() != PoliticalStatus.Affiliation.REJECTED)
            data.putAffiliation(new AffiliationInstance(a.id(), a.person(), a.actor(), a.kind(), PoliticalStatus.Affiliation.FORMER, a.startedAt(), now, a.provenance(), a.visibility()));
        for (var request : requests.entries()) if (request.open() && request.organization().equals(org.id().toString())) requests.put(request.state("withdrawn"));
        data.putOrganization(new OrganizationInstance(org.id(), org.kind(), org.membershipPolicy(), org.name(), org.shortName(), org.color(), org.emblem(), org.createdAt(), org.provenance(), PoliticalStatus.Organization.DISSOLVED, org.home()));
        data.putPolity(new PolityInstance(polity.id(), polity.name(), polity.color(), polity.emblem(), polity.createdAt(), polity.provenance(), PoliticalStatus.Polity.DISSOLVED, polity.settlements(), polity.governmentOrganization()));
        return true;
    }
    private static Component message(String key) { return Component.translatable("charter.townstead.lifecycle." + key); }
}
