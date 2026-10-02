package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Personal membership actions at the Charter: joining, applying, inviting, leaving, and deciding
 * requests. How a faction admits people comes from its kind's membership block; anything the kind
 * does not allow is never offered.
 */
public final class CharterMemberships {
    public static final ResourceLocation INVITE = id("townstead:invite_member");
    private static final ResourceLocation CHARTER = id("townstead:charter");

    private CharterMemberships() {}

    private static ResourceLocation id(String value) { return Objects.requireNonNull(ResourceLocation.tryParse(value)); }

    private static CharterSnapshotS2CPayload.Text text(Component value) { return CharterSnapshotS2CPayload.Text.of(value); }

    static CharterSnapshotS2CPayload.Action action(String operation) {
        return new CharterSnapshotS2CPayload.Action(operation, text(Component.translatable("charter.townstead.action." + operation)),
                text(Component.translatable("charter.townstead.action." + operation + ".description")), operation.equals("invite"));
    }

    private static FactionKind kind(Faction faction) {
        return faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
    }

    static boolean deciding(PoliticalSavedData data, UUID person, Faction faction, FactionKind kind) {
        return PoliticalAuthority.allowed(data, person, faction.id(), kind.membership().approval());
    }

    public static boolean member(PoliticalSavedData data, UUID person, Faction faction) {
        FactionKind kind = kind(faction);
        return kind != null && FactionBonds.member(data, person, faction.id(), kind.membership().bond());
    }

    private static boolean pending(CharterRequests requests, UUID person, Faction faction) {
        return requests.entries().stream().anyMatch(r -> r.open() && r.person().equals(person) && r.faction().equals(faction.id().toString()));
    }

    /** True while this person alone fills an office the faction must keep filled, such as its leader. */
    public static boolean irreplaceable(PoliticalSavedData data, UUID person, Faction faction) {
        FactionKind kind = kind(faction);
        if (kind == null) return false;
        for (FactionKind.Office office : kind.offices()) {
            if (office.minimum() < 1) continue;
            List<UUID> holders = FactionBonds.holders(data, faction.id(), office.bond());
            if (holders.contains(person) && holders.size() <= office.minimum()) return true;
        }
        return false;
    }

    public static List<CharterSnapshotS2CPayload.Action> actions(ServerPlayer player, PoliticalSavedData data, Faction faction) {
        FactionKind kind = kind(faction);
        if (kind == null || !faction.active() || data.externalGovernment(faction.id())) return List.of();
        var requests = CharterRequests.get(player.server);
        List<CharterSnapshotS2CPayload.Action> actions = new ArrayList<>();
        UUID self = player.getUUID();
        boolean member = member(data, self, faction);
        if (!pending(requests, self, faction)) {
            ResourceLocation admission = kind.membership().admission();
            if (!member && kind.membership().eligibility().test(new ConditionContext(player))) {
                if (admission.equals(FactionKind.OPEN)) actions.add(action("join"));
                if (admission.equals(FactionKind.APPLICATION)) actions.add(action("apply"));
            }
            if (member && !irreplaceable(data, self, faction)) {
                actions.add(action(kind.membership().noticeDays() > 0 ? "notice" : "resign"));
            }
        }
        if (!kind.membership().admission().equals(FactionKind.RESIDENCE)
                && PoliticalAuthority.allowed(data, self, faction.id(), INVITE)) actions.add(action("invite"));
        return List.copyOf(actions);
    }

    private static List<CharterSnapshotS2CPayload.Action> requestActions(ServerPlayer player, PoliticalSavedData data,
            Faction faction, FactionKind kind, CharterRequests.Entry entry) {
        List<CharterSnapshotS2CPayload.Action> actions = new ArrayList<>();
        if (!entry.open()) return actions;
        boolean self = player.getUUID().equals(entry.person());
        if (self && entry.kind().equals("application")) actions.add(action("withdraw"));
        if (self && entry.kind().equals("invitation")) actions.add(action("decline"));
        if (!entry.kind().equals("notice") && entry.initiator().equals(player.getUUID()) && !self) actions.add(action("withdraw"));
        if (kind == null || !faction.active()) return actions;
        if (entry.kind().equals(CharterAccords.REQUEST_KIND)) {
            if (CharterAccords.answers(data, player.getUUID(), faction)) {
                actions.add(action("approve"));
                actions.add(action("reject"));
            }
            return List.copyOf(actions);
        }
        if (self && entry.kind().equals("invitation") && votes(entry, data, faction, kind) >= kind.membership().approvals()) {
            actions.add(action("accept"));
        }
        if (!entry.kind().equals("notice") && deciding(data, player.getUUID(), faction, kind)) {
            if (!entry.approvals().contains(player.getUUID())) actions.add(action("approve"));
            actions.add(action("reject"));
        }
        return List.copyOf(actions);
    }

    private static long votes(CharterRequests.Entry entry, PoliticalSavedData data, Faction faction, FactionKind kind) {
        return entry.approvals().stream().filter(v -> deciding(data, v, faction, kind)).count();
    }

    /** Requests the viewer may see: their own, ones they started, and all of them for those who decide. */
    public static List<CharterSnapshotS2CPayload.Request> requests(ServerPlayer player, PoliticalSavedData data, Faction faction) {
        List<CharterSnapshotS2CPayload.Request> out = new ArrayList<>();
        FactionKind kind = kind(faction);
        for (var entry : CharterRequests.get(player.server).entries()) {
            ResourceLocation target = ResourceLocation.tryParse(entry.faction());
            if (target == null || !data.canonical(target).equals(faction.id())) continue;
            boolean accord = entry.kind().equals(CharterAccords.REQUEST_KIND);
            boolean decides = accord ? CharterAccords.answers(data, player.getUUID(), faction)
                    : kind != null && deciding(data, player.getUUID(), faction, kind);
            if (!entry.person().equals(player.getUUID()) && !entry.initiator().equals(player.getUUID()) && !decides) continue;
            int required = accord ? 1 : kind == null ? 0 : kind.membership().approvals();
            int approvalCount = accord ? 0 : kind == null ? 0 : (int) votes(entry, data, faction, kind);
            String state = entry.state().equals("offered") && approvalCount < required ? "pending" : entry.state();
            out.add(new CharterSnapshotS2CPayload.Request(entry.id().toString(), text(Component.literal(faction.name())),
                    text(Component.literal(entry.personName())), entry.kind(), state, approvalCount, required,
                    requestActions(player, data, faction, kind, entry)));
        }
        Collections.reverse(out);
        return List.copyOf(out);
    }

    /** Revalidates the faction, its admission rules, live eligibility and the request revision on the server thread. */
    public static Component handle(ServerPlayer player, Faction faction, CharterActionC2SPayload intent) {
        var store = CharterRequests.get(player.server);
        var data = PoliticalSavedData.get(player.server);
        if (intent.revision() != revision(player.server)) return message("stale");
        FactionKind kind = kind(faction);
        if (kind == null) return message("denied");
        String operation = intent.operation();
        long now = player.server.overworld().getGameTime();
        if (intent.target().equals(faction.id().toString())) {
            if (actions(player, data, faction).stream().noneMatch(a -> a.id().equals(operation))) return message("denied");
            ServerPlayer subject = operation.equals("invite") ? player.server.getPlayerList().getPlayerByName(intent.argument()) : player;
            if (subject == null) return message("player_unavailable");
            if (operation.equals("join") || operation.equals("apply") || operation.equals("invite")) {
                if (member(data, subject.getUUID(), faction) || pending(store, subject.getUUID(), faction)) return message("already_pending");
                if (!kind.membership().eligibility().test(new ConditionContext(subject))) return message("ineligible");
            }
            ResourceLocation bond = kind.membership().bond();
            BondInstance current = FactionBonds.membership(data, subject.getUUID(), faction.id(), bond);
            if (operation.equals("join")) {
                return message(admit(data, subject, faction, bond, now) ? "completed" : "capacity");
            }
            if (operation.equals("resign")) {
                FactionBonds.end(data, current, now, "resigned");
                return message("completed");
            }
            String kindOfRequest = switch (operation) { case "apply" -> "application"; case "invite" -> "invitation"; default -> "notice"; };
            String state = operation.equals("notice") ? "notice" : "pending";
            Set<UUID> approvals = operation.equals("invite") ? Set.of(player.getUUID()) : Set.of();
            var entry = new CharterRequests.Entry(UUID.randomUUID(), faction.id().toString(), subject.getUUID(),
                    subject.getGameProfile().getName(), player.getUUID(), kindOfRequest, state, bond.toString(),
                    current == null ? "" : current.id().toString(), now + kind.membership().noticeDays() * 24000L, approvals);
            if (operation.equals("invite") && kind.membership().approvals() <= 1) entry = entry.state("offered");
            store.put(entry);
            return message("recorded");
        }
        UUID requestId;
        try { requestId = UUID.fromString(intent.target()); } catch (IllegalArgumentException e) { return message("denied"); }
        var entry = store.get(requestId);
        if (entry == null) return message("stale");
        ResourceLocation target = ResourceLocation.tryParse(entry.faction());
        if (target == null || !data.canonical(target).equals(faction.id())) return message("denied");
        if (requestActions(player, data, faction, kind, entry).stream().noneMatch(a -> a.id().equals(operation))) return message("denied");
        if (operation.equals("withdraw") || operation.equals("decline") || operation.equals("reject")) {
            store.put(entry.state(operation.equals("withdraw") ? "withdrawn" : "rejected"));
            return message("completed");
        }
        if (entry.kind().equals(CharterAccords.REQUEST_KIND)) {
            String problem = CharterAccords.accept(player, faction, entry);
            if (problem != null) return CharterDrafts.message(problem);
            store.put(entry.state("completed"));
            return message("completed");
        }
        var updated = operation.equals("approve") ? entry.approve(player.getUUID()) : entry;
        boolean quorum = votes(updated, data, faction, kind) >= kind.membership().approvals();
        if (quorum && (entry.kind().equals("application") || operation.equals("accept"))) {
            ServerPlayer subject = player.server.getPlayerList().getPlayer(entry.person());
            if (subject == null) return message("player_unavailable");
            if (member(data, entry.person(), faction)) return message("already_pending");
            if (!kind.membership().eligibility().test(new ConditionContext(subject))) return message("ineligible");
            if (!admit(data, subject, faction, kind.membership().bond(), now)) return message("capacity");
            updated = updated.state("completed");
        } else if (quorum) updated = updated.state("offered");
        store.put(updated);
        return message(updated.state().equals("completed") ? "completed" : "recorded");
    }

    private static boolean admit(PoliticalSavedData data, ServerPlayer subject, Faction faction, ResourceLocation bond, long now) {
        return FactionBonds.form(data, bond, FactionBonds.sides(bond, faction.id(), subject.getUUID()), CHARTER, now).formed();
    }

    /** Ends every bond a person holds, as when they start a new life. */
    public static void endAll(MinecraftServer server, UUID person) {
        FactionBonds.endAll(PoliticalSavedData.get(server), Party.person(person), server.overworld().getGameTime(), "reborn");
    }

    public static void tick(MinecraftServer server) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.POLITICS)) return;
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        var store = CharterRequests.get(server);
        for (var entry : store.entries()) if (entry.state().equals("notice") && now >= entry.due()) {
            var data = PoliticalSavedData.get(server);
            ResourceLocation target = ResourceLocation.tryParse(entry.faction());
            Faction faction = target == null ? null : data.faction(target);
            ResourceLocation bond = ResourceLocation.tryParse(entry.bond());
            BondInstance current = faction == null || bond == null ? null : FactionBonds.membership(data, entry.person(), faction.id(), bond);
            if (current != null && current.id().toString().equals(entry.membership()) && !irreplaceable(data, entry.person(), faction)) {
                FactionBonds.end(data, current, now, "resigned");
            }
            store.put(entry.state("completed"));
        }
    }

    public static long revision(MinecraftServer server) {
        return CharterRequests.get(server).revision() ^ ((long) System.identityHashCode(PoliticalDefinitions.snapshot()) << 32);
    }

    private static Component message(String key) { return Component.translatable("charter.townstead.membership." + key); }
}
