package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Built-in membership procedures. Unknown procedures fail closed, never become open admission. */
public final class CharterMemberships {
    private CharterMemberships() {}
    private static ResourceLocation id(String value) { return Objects.requireNonNull(ResourceLocation.tryParse(value)); }
    private static boolean procedure(ResourceLocation value, String name) { return value.equals(id("townstead:" + name)); }
    private static CharterSnapshotS2CPayload.Text text(Component value) { return CharterSnapshotS2CPayload.Text.of(value); }
    private static CharterSnapshotS2CPayload.Action action(String operation) {
        return new CharterSnapshotS2CPayload.Action(operation, text(Component.translatable("charter.townstead.action." + operation)),
                text(Component.translatable("charter.townstead.action." + operation + ".description")), operation.equals("invite") || operation.equals("transfer_leadership"));
    }
    public static boolean inScope(OrganizationInstance org, PolityInstance polity) {
        return org != null && polity != null && org.status() == PoliticalStatus.Organization.ACTIVE
                && (org.id().equals(polity.governmentOrganization()) || org.home() != null && polity.settlements().contains(org.home()));
    }
    static boolean deciding(PoliticalSavedData data, UUID person, OrganizationInstance org, MembershipPolicyDefinition policy) {
        if (data.supersededGovernment(org.id())) return false;
        var decision = policy.admission().decision();
        var member = data.membership(person, org.id());
        return decision != null && procedure(decision.procedure(), "role_approval") && decision.role() != null
                && member != null && member.affiliation().active() && member.roles().contains(decision.role());
    }
    private static boolean hasCurrent(PoliticalSavedData data, UUID person, OrganizationInstance org) {
        var member = data.membership(person, org.id());
        return member != null && member.affiliation().status() != PoliticalStatus.Affiliation.FORMER
                && member.affiliation().status() != PoliticalStatus.Affiliation.REJECTED;
    }
    private static boolean pending(CharterRequests requests, UUID person, OrganizationInstance org) {
        return requests.entries().stream().anyMatch(r -> r.open() && r.person().equals(person) && r.organization().equals(org.id().toString()));
    }
    public static List<CharterSnapshotS2CPayload.Action> actions(ServerPlayer player, PoliticalSavedData data, OrganizationInstance org) {
        var policy = PoliticalDefinitions.snapshot().membershipPolicy(org.membershipPolicy());
        if (data.supersededGovernment(org.id()) || policy == null || org.status() != PoliticalStatus.Organization.ACTIVE) return List.of();
        var requests = CharterRequests.get(player.server);
        List<CharterSnapshotS2CPayload.Action> actions = new ArrayList<>();
        var member = data.membership(player.getUUID(), org.id());
        if (!pending(requests, player.getUUID(), org)) {
            if (!hasCurrent(data, player.getUUID(), org) && policy.admission().eligibility().test(new ConditionContext(player))) {
                if (procedure(policy.admission().procedure(), "open_admission")) actions.add(action("join"));
                if (procedure(policy.admission().procedure(), "application") && supportedDecision(policy)) actions.add(action("apply"));
            }
            if (member != null && member.affiliation().active() && !isFactionLeader(org, member)) {
                if (procedure(policy.departure().procedure(), "free_resignation")) actions.add(action("resign"));
                if (procedure(policy.departure().procedure(), "notice")) actions.add(action("notice"));
            }
        }
        if (invitationSupported(org, policy) && deciding(data, player.getUUID(), org, policy)) actions.add(action("invite"));
        if (isFactionLeader(org, member)) {
            var polity = data.polities().stream().filter(p -> org.id().equals(p.governmentOrganization()) && p.status() == PoliticalStatus.Polity.ACTIVE).findFirst().orElse(null);
            if (polity != null && CharterSavedData.get(player.server).amendment(polity.id()) != null) actions.add(action("cancel_amendment"));
            else { actions.add(action("transfer_leadership")); actions.add(action("dissolve_faction")); }
        }
        return List.copyOf(actions);
    }
    private static boolean invitationSupported(OrganizationInstance org, MembershipPolicyDefinition policy) {
        return procedure(policy.admission().procedure(), "appointment")
                || org.kind().equals(id("townstead:player_faction")) && procedure(policy.admission().procedure(), "application");
    }
    public static boolean isFactionLeader(OrganizationInstance org, MembershipInstance member) {
        return org != null && org.kind().equals(id("townstead:player_faction")) && member != null
                && member.affiliation().active() && member.roles().contains(id("townstead:faction_leader"));
    }
    /** Native faction leadership only; external and modded governments own their succession rules. */
    public static boolean transferLeadership(PoliticalSavedData data, OrganizationInstance org, UUID from, UUID to) {
        if (org == null || org.status() != PoliticalStatus.Organization.ACTIVE || data.supersededGovernment(org.id()) || from.equals(to)) return false;
        var outgoing = data.membership(from, org.id()); var incoming = data.membership(to, org.id());
        if (!isFactionLeader(org, outgoing) || incoming == null || !incoming.affiliation().active()) return false;
        if (data.memberships(org.actor()).stream().filter(m -> isFactionLeader(org, m)).count() != 1) return false;
        var oldRoles = new HashSet<>(outgoing.roles()); oldRoles.remove(id("townstead:faction_leader"));
        var newRoles = new HashSet<>(incoming.roles()); newRoles.add(id("townstead:faction_leader"));
        data.putMembership(new MembershipInstance(outgoing.affiliation(), outgoing.membershipPolicy(), outgoing.admissionProcedure(), outgoing.departureProcedure(), oldRoles));
        data.putMembership(new MembershipInstance(incoming.affiliation(), incoming.membershipPolicy(), incoming.admissionProcedure(), incoming.departureProcedure(), newRoles));
        return true;
    }
    private static boolean supportedDecision(MembershipPolicyDefinition policy) {
        return policy.admission().decision() != null && procedure(policy.admission().decision().procedure(), "role_approval")
                && policy.admission().decision().role() != null;
    }
    private static long votes(CharterRequests.Entry entry, PoliticalSavedData data, OrganizationInstance org, MembershipPolicyDefinition policy) {
        return entry.approvals().stream().filter(v -> deciding(data, v, org, policy)).count();
    }
    private static List<CharterSnapshotS2CPayload.Action> requestActions(ServerPlayer player, PoliticalSavedData data,
            OrganizationInstance org, MembershipPolicyDefinition policy, CharterRequests.Entry entry) {
        List<CharterSnapshotS2CPayload.Action> actions = new ArrayList<>();
        if (!entry.open()) return actions;
        boolean self = player.getUUID().equals(entry.person());
        if (self && entry.kind().equals("application")) actions.add(action("withdraw"));
        if (self && entry.kind().equals("invitation")) actions.add(action("decline"));
        if (!entry.kind().equals("notice") && entry.initiator().equals(player.getUUID()) && !self) actions.add(action("withdraw"));
        if (policy == null || !entry.policy().equals(policy.id().toString()) || org.status() != PoliticalStatus.Organization.ACTIVE) return actions;
        if (self && entry.kind().equals("invitation") && invitationSupported(org, policy) && supportedDecision(policy) && votes(entry, data, org, policy) >= policy.admission().decision().approvals()) actions.add(action("accept"));
        if (!entry.kind().equals("notice") && ((entry.kind().equals("application") && procedure(policy.admission().procedure(), "application"))
                || (entry.kind().equals("invitation") && invitationSupported(org, policy))) && deciding(data, player.getUUID(), org, policy)) {
            if (!entry.approvals().contains(player.getUUID())) actions.add(action("approve"));
            actions.add(action("reject"));
        }
        return List.copyOf(actions);
    }
    public static List<CharterSnapshotS2CPayload.Request> requests(ServerPlayer player, PoliticalSavedData data, PolityInstance polity) {
        List<CharterSnapshotS2CPayload.Request> out = new ArrayList<>();
        for (var entry : CharterRequests.get(player.server).entries()) {
            var org = data.organization(ResourceLocation.tryParse(entry.organization()));
            if (!inScope(org, polity) || data.supersededGovernment(org.id())) continue;
            var policy = PoliticalDefinitions.snapshot().membershipPolicy(org.membershipPolicy());
            if (!entry.person().equals(player.getUUID()) && !entry.initiator().equals(player.getUUID())
                    && (policy == null || !deciding(data, player.getUUID(), org, policy))) continue;
            int required = policy == null || policy.admission().decision() == null ? 0 : policy.admission().decision().approvals();
            int approvalCount = policy == null ? 0 : (int) votes(entry, data, org, policy);
            String state = entry.state().equals("offered") && approvalCount < required ? "pending" : entry.state();
            out.add(new CharterSnapshotS2CPayload.Request(entry.id().toString(), text(Component.literal(org.name())),
                    text(Component.literal(entry.personName())), entry.kind(), state,
                    approvalCount, required, requestActions(player, data, org, policy, entry)));
        }
        Collections.reverse(out);
        return List.copyOf(out);
    }
    /** Revalidates the bound actor, current procedure, live eligibility and exact request revision on the server thread. */
    public static Component handle(ServerPlayer player, PolityInstance polity, CharterActionC2SPayload intent) {
        var store = CharterRequests.get(player.server);
        var data = PoliticalSavedData.get(player.server);
        if (intent.revision() != revision(player.server)) return message("stale");
        String operation = intent.operation();
        ResourceLocation target = ResourceLocation.tryParse(intent.target());
        var org = target == null ? null : data.organization(target);
        if (org != null) {
            if (!inScope(org, polity) || data.supersededGovernment(org.id()) || actions(player, data, org).stream().noneMatch(a -> a.id().equals(operation))) return message("denied");
            var policy = PoliticalDefinitions.snapshot().membershipPolicy(org.membershipPolicy());
            ServerPlayer subject = (operation.equals("invite") || operation.equals("transfer_leadership")) ? player.server.getPlayerList().getPlayerByName(intent.argument()) : player;
            if (subject == null) return message("player_unavailable");
            // Lifecycle actions must pass through the bound lectern and bell preparation path.
            if (operation.equals("transfer_leadership") || operation.equals("dissolve_faction") || operation.equals("cancel_amendment")) return message("denied");
            if (operation.equals("join") || operation.equals("apply") || operation.equals("invite")) {
                if (hasCurrent(data, subject.getUUID(), org) || pending(store, subject.getUUID(), org)) return message("already_pending");
                if (!policy.admission().eligibility().test(new ConditionContext(subject))) return message("ineligible");
                if (!capacity(data, org, policy)) return message("capacity");
            }
            String kind = switch (operation) { case "apply" -> "application"; case "invite" -> "invitation";
                case "resign", "notice" -> "notice"; default -> "membership"; };
            long now = player.server.overworld().getGameTime();
            String state = operation.equals("join") || operation.equals("resign") ? "completed"
                    : operation.equals("notice") ? "notice" : "pending";
            Set<UUID> approvals = operation.equals("invite") ? Set.of(player.getUUID()) : Set.of();
            var entry = new CharterRequests.Entry(UUID.randomUUID(), org.id().toString(), subject.getUUID(), subject.getGameProfile().getName(),
                    player.getUUID(), kind, state, policy.id().toString(),
                    data.membership(subject.getUUID(), org.id()) == null ? "" : data.membership(subject.getUUID(), org.id()).id().toString(), now + policy.departure().noticeDays() * 24000L, approvals);
            if (operation.equals("join")) admit(data, subject, org, policy, now);
            if (operation.equals("resign")) end(data, data.membership(player.getUUID(), org.id()), now);
            if (operation.equals("invite") && policy.admission().decision().approvals() == 1) entry = entry.state("offered");
            store.put(entry);
            return message(state.equals("completed") ? "completed" : "recorded");
        }
        UUID requestId;
        try { requestId = UUID.fromString(intent.target()); } catch (IllegalArgumentException e) { return message("denied"); }
        var entry = store.get(requestId);
        if (entry == null) return message("stale");
        org = data.organization(ResourceLocation.tryParse(entry.organization()));
        if (!inScope(org, polity) || data.supersededGovernment(org.id())) return message("denied");
        var policy = PoliticalDefinitions.snapshot().membershipPolicy(org.membershipPolicy());
        if (requestActions(player, data, org, policy, entry).stream().noneMatch(a -> a.id().equals(operation))) return message("denied");
        if (operation.equals("withdraw") || operation.equals("decline") || operation.equals("reject")) {
            store.put(entry.state(operation.equals("withdraw") ? "withdrawn" : "rejected")); return message("completed");
        }
        var updated = operation.equals("approve") ? entry.approve(player.getUUID()) : entry;
        boolean quorum = votes(updated, data, org, policy) >= policy.admission().decision().approvals();
        if (quorum && (entry.kind().equals("application") || operation.equals("accept"))) {
            ServerPlayer subject = player.server.getPlayerList().getPlayer(entry.person());
            if (subject == null) return message("player_unavailable");
            if (hasCurrent(data, entry.person(), org)) return message("already_pending");
            if (!policy.admission().eligibility().test(new ConditionContext(subject))) return message("ineligible");
            if (!capacity(data, org, policy)) return message("capacity");
            admit(data, subject, org, policy, player.server.overworld().getGameTime());
            updated = updated.state("completed");
        } else if (quorum) updated = updated.state("offered");
        store.put(updated);
        return message(updated.state().equals("completed") ? "completed" : "recorded");
    }
    static boolean capacity(PoliticalSavedData data, OrganizationInstance org, MembershipPolicyDefinition policy) {
        var kind = PoliticalDefinitions.snapshot().organizationKind(org.kind());
        if (kind == null) return false;
        for (var role : policy.admission().initialRoles()) {
            var binding = kind.roles().stream().filter(r -> r.role().equals(role)).findFirst().orElse(null);
            if (binding == null || PoliticalDefinitions.snapshot().role(role) == null) return false;
            if (binding.maximum() >= 0 && data.memberships(org.actor()).stream()
                    .filter(m -> m.affiliation().active() && m.roles().contains(role)).count() >= binding.maximum()) return false;
        }
        return true;
    }
    private static void admit(PoliticalSavedData data, ServerPlayer subject, OrganizationInstance org, MembershipPolicyDefinition policy, long now) {
        var affiliation = new AffiliationInstance(id("townstead:membership/" + UUID.randomUUID()), subject.getUUID(), org.actor(),
                id("townstead:membership"), PoliticalStatus.Affiliation.ACTIVE, now, AffiliationInstance.NOT_ENDED,
                id("townstead:charter"), PoliticalStatus.Visibility.MEMBERS);
        data.putMembership(new MembershipInstance(affiliation, policy.id(), policy.admission().procedure(),
                policy.departure().procedure(), Set.copyOf(policy.admission().initialRoles())));
    }
    /** Ends every active membership a person holds, as when they start a new life. */
    public static void endAll(MinecraftServer server, UUID person) {
        var data = PoliticalSavedData.get(server);
        long now = server.overworld().getGameTime();
        for (MembershipInstance member : data.memberships(person)) {
            if (member.affiliation().active()) end(data, member, now);
        }
    }

    static void end(PoliticalSavedData data, MembershipInstance member, long now) {
        var a = member.affiliation();
        data.putMembership(new MembershipInstance(new AffiliationInstance(a.id(), a.person(), a.actor(), a.kind(),
                PoliticalStatus.Affiliation.FORMER, a.startedAt(), now, a.provenance(), a.visibility()),
                member.membershipPolicy(), member.admissionProcedure(), member.departureProcedure(), member.roles()));
    }
    public static void tick(MinecraftServer server) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.POLITICS)) return;
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        var store = CharterRequests.get(server);
        for (var entry : store.entries()) if (entry.state().equals("notice") && now >= entry.due()) {
            var data = PoliticalSavedData.get(server);
            var org = data.organization(ResourceLocation.tryParse(entry.organization()));
            var member = org == null ? null : data.membership(entry.person(), org.id());
            if (member != null && member.id().toString().equals(entry.membership()) && member.affiliation().active() && !isFactionLeader(org, member)) end(data, member, now);
            store.put(entry.state("completed"));
        }
    }
    public static long revision(MinecraftServer server) {
        return CharterRequests.get(server).revision() ^ ((long) System.identityHashCode(PoliticalDefinitions.snapshot()) << 32);
    }
    private static Component message(String key) { return Component.translatable("charter.townstead.membership." + key); }
}
