package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.GovernanceDefinition;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.heraldry.EmblemItems;
import com.aetherianartificer.townstead.politics.heraldry.EmblemRecipe;
import com.aetherianartificer.townstead.politics.heraldry.HeraldryService;
import com.aetherianartificer.townstead.politics.heraldry.HeraldrySavedData;
import com.aetherianartificer.townstead.politics.seat.SeatService;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Draft, sign, proclaim. Changes to a faction gather as clauses on its draft at a Charter lectern;
 * the people its governance names sign; a signer rings the bell to proclaim. Every clause is
 * checked again at the bell and nothing applies unless all of them still can.
 */
public final class CharterDrafts {
    public static final ResourceLocation GOVERN = id("townstead:govern_faction");
    public static final String RENAME = "rename", HERALDRY = "heraldry", SEAT = "seat",
            TRANSFER_LEADERSHIP = "transfer_leadership", DISSOLVE = "dissolve";
    /** How long a signed draft waits for its bell: half a Minecraft day. */
    static final long PREPARED_LIFETIME = 12000L;

    private CharterDrafts() {}

    /** Whether this player may write the faction's draft: they govern it, or they are an operator. */
    public static boolean mayDraft(ServerPlayer player, Faction faction) {
        if (faction == null || !faction.active()) return false;
        PoliticalSavedData data = PoliticalSavedData.get(player.server);
        if (data.externalGovernment(faction.id())) return false;
        return PoliticalAuthority.allowed(data, player.getUUID(), faction.id(), GOVERN) || player.hasPermissions(2);
    }

    /**
     * Who must sign: the players holding the head office. When no player holds it, as in a village
     * run by its council, the draft's author signs alone.
     */
    public static List<UUID> signers(ServerPlayer viewer, Faction faction, UUID author) {
        List<UUID> out = new ArrayList<>();
        ResourceLocation head = headOffice(faction);
        if (head != null) {
            for (UUID holder : FactionBonds.holders(PoliticalSavedData.get(viewer.server), faction.id(), head)) {
                if (isPlayer(viewer, holder)) out.add(holder);
            }
        }
        if (out.isEmpty()) out.add(author);
        return List.copyOf(out);
    }

    public static @Nullable ResourceLocation headOffice(Faction faction) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        GovernanceDefinition governance = kind == null ? null : kind.governance();
        return governance == null ? null : governance.head();
    }

    private static boolean isPlayer(ServerPlayer viewer, UUID person) {
        if (viewer.server.getPlayerList().getPlayer(person) != null) return true;
        var cache = viewer.server.getProfileCache();
        return cache != null && cache.get(person).isPresent();
    }

    /** Handles every draft operation from the lectern. Returns the message to show. */
    public static Component handle(ServerPlayer player, CharterSavedData.Binding binding, Faction faction, CharterActionC2SPayload request) {
        if (!mayDraft(player, faction)) return message("denied");
        CharterSavedData saved = CharterSavedData.get(player.server);
        long now = player.serverLevel().getGameTime();
        saved.expire(now);
        CharterSavedData.Draft draft = saved.draft(faction.id());
        String operation = request.operation();
        switch (operation) {
            case "discard" -> {
                if (draft == null || !draft.token().toString().equals(request.target())) return message("stale");
                saved.removeDraft(faction.id(), draft.token());
                return message("discarded");
            }
            case "remove" -> {
                if (draft == null || !draft.token().toString().equals(request.target())) return message("stale");
                int index;
                try { index = Integer.parseInt(request.argument()); } catch (NumberFormatException e) { return message("stale"); }
                if (index < 0 || index >= draft.clauses().size()) return message("stale");
                List<CharterSavedData.Clause> clauses = new ArrayList<>(draft.clauses());
                clauses.remove(index);
                if (clauses.isEmpty()) saved.removeDraft(faction.id(), draft.token());
                else saved.putDraft(draft.withClauses(clauses));
                return message("removed");
            }
            case "sign" -> {
                if (draft == null || !draft.token().toString().equals(request.target())) return message("stale");
                List<UUID> signers = signers(player, faction, draft.author());
                if (!signers.contains(player.getUUID())) return message("not_signer");
                String problem = check(player, faction, draft);
                if (problem != null) return message(problem);
                CharterSavedData.Draft signed = draft.signed(player.getUUID(),
                        com.aetherianartificer.townstead.calendar.TownsteadCalendar.worldDay(player.server));
                if (signed.signatures().containsAll(signers)) signed = signed.preparedUntil(now + PREPARED_LIFETIME);
                saved.putDraft(signed);
                return message(signed.prepared() ? "prepared" : "signed");
            }
            default -> {
                CharterSavedData.Clause clause = clause(player, binding, faction, request);
                if (clause == null) return message("invalid");
                String problem = check(player, faction, clause);
                if (problem != null) return message(problem);
                List<CharterSavedData.Clause> clauses = new ArrayList<>(draft == null ? List.of() : draft.clauses());
                if (clause.type().equals(DISSOLVE)) clauses.clear();
                else clauses.removeIf(existing -> existing.type().equals(DISSOLVE));
                clauses.removeIf(existing -> existing.type().equals(clause.type()) && existing.target().equals(clause.target()));
                clauses.add(clause);
                saved.putDraft(draft == null
                        ? new CharterSavedData.Draft(UUID.randomUUID(), faction.id(), player.getUUID(), binding.dimension(),
                                binding.lectern(), binding.bell(), clauses, java.util.Set.of(), CharterSavedData.Draft.UNSIGNED)
                        : draft.withClauses(clauses));
                return message("added");
            }
        }
    }

    /** Builds the clause a lectern request asks for, capturing what its target reads now. */
    private static @Nullable CharterSavedData.Clause clause(ServerPlayer player, CharterSavedData.Binding binding, Faction faction,
                                                           CharterActionC2SPayload request) {
        return switch (request.operation()) {
            case RENAME -> new CharterSavedData.Clause(RENAME, faction.id().toString(), request.argument(), faction.name());
            case HERALDRY -> HeraldryService.shows(binding, request.target())
                    ? new CharterSavedData.Clause(HERALDRY, request.target(), request.argument(),
                            Long.toString(HeraldrySavedData.get(player.server).get(request.target()).revision()))
                    : null;
            case SEAT -> new CharterSavedData.Clause(SEAT, binding.dimension() + "|" + binding.lectern().asLong(), "", "");
            case TRANSFER_LEADERSHIP -> new CharterSavedData.Clause(TRANSFER_LEADERSHIP, faction.id().toString(), request.argument(), "");
            case DISSOLVE -> new CharterSavedData.Clause(DISSOLVE, faction.id().toString(), "", faction.name());
            default -> null;
        };
    }

    /** The first clause that can no longer apply, as a message key; null when all can. */
    static @Nullable String check(ServerPlayer player, Faction faction, CharterSavedData.Draft draft) {
        if (draft.clauses().isEmpty()) return "empty";
        for (CharterSavedData.Clause clause : draft.clauses()) {
            String problem = check(player, faction, clause);
            if (problem != null) return problem;
        }
        return null;
    }

    static @Nullable String check(ServerPlayer player, Faction faction, CharterSavedData.Clause clause) {
        PoliticalSavedData data = PoliticalSavedData.get(player.server);
        return switch (clause.type()) {
            case RENAME -> !faction.name().equals(clause.expected()) ? "stale"
                    : CharterIdentityService.normalize(clause.argument()) == null ? "invalid_name" : null;
            case HERALDRY -> {
                EmblemRecipe recipe;
                try { recipe = EmblemRecipe.decode(clause.argument()); } catch (RuntimeException error) { yield "invalid"; }
                if (!EmblemItems.valid(player.serverLevel().registryAccess(), recipe)) yield "invalid";
                yield Long.toString(HeraldrySavedData.get(player.server).get(clause.target()).revision()).equals(clause.expected()) ? null : "stale";
            }
            case SEAT -> {
                CharterSavedData.Binding binding = seatBinding(player, clause);
                if (binding == null || !binding.faction().equals(faction.id())) yield "stale";
                ServerLevel level = level(player, binding.dimension());
                yield level == null || SeatService.host(level, binding) == null ? "seat_no_building" : null;
            }
            case TRANSFER_LEADERSHIP -> {
                ResourceLocation head = headOffice(faction);
                UUID recipient = uuid(clause.argument());
                FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
                if (head == null || recipient == null || kind == null) yield "invalid";
                if (!FactionBonds.member(data, recipient, faction.id(), kind.membership().bond())) yield "recipient";
                yield FactionBonds.holders(data, faction.id(), head).contains(recipient) ? "recipient" : null;
            }
            case DISSOLVE -> faction.name().equals(clause.expected()) ? null : "stale";
            default -> "invalid";
        };
    }

    /**
     * Proclaims a prepared draft from its bell. The ringer must be one of its signers. Every clause is
     * checked before any applies.
     */
    /** What ringing the bell did: the message for the ringer, and whether the amendment took effect. */
    public record Proclamation(Component message, boolean done) {}

    public static Proclamation proclaim(ServerPlayer player, CharterSavedData.Draft draft) {
        CharterSavedData saved = CharterSavedData.get(player.server);
        PoliticalSavedData data = PoliticalSavedData.get(player.server);
        Faction faction = data.faction(draft.faction());
        if (faction == null || !faction.active() || !draft.signatures().contains(player.getUUID())) return new Proclamation(message("denied"), false);
        String problem = check(player, faction, draft);
        if (problem != null) {
            saved.putDraft(draft.unsigned());
            return new Proclamation(message(problem), false);
        }
        if (!saved.removeDraft(faction.id(), draft.token())) return new Proclamation(message("stale"), false);
        long now = player.serverLevel().getGameTime();
        for (CharterSavedData.Clause clause : draft.clauses()) {
            switch (clause.type()) {
                case RENAME -> CharterIdentityService.rename(data, faction.id(), clause.expected(), clause.argument());
                case HERALDRY -> HeraldryService.publish(player, clause.target(), EmblemRecipe.decode(clause.argument()),
                        Long.parseLong(clause.expected()));
                case SEAT -> {
                    CharterSavedData.Binding binding = seatBinding(player, clause);
                    ServerLevel level = binding == null ? null : level(player, binding.dimension());
                    if (level != null) SeatService.designate(level, binding, true);
                }
                case TRANSFER_LEADERSHIP -> FactionLifecycle.transferHead(data, faction, uuid(clause.argument()), now);
                case DISSOLVE -> {
                    FactionLifecycle.dissolve(player, faction, draft);
                    return new Proclamation(message("dissolved"), true);
                }
                default -> { }
            }
        }
        return new Proclamation(message("proclaimed"), true);
    }

    private static @Nullable CharterSavedData.Binding seatBinding(ServerPlayer player, CharterSavedData.Clause clause) {
        String[] parts = clause.target().split("\\|", 2);
        if (parts.length != 2) return null;
        ResourceLocation dimension = ResourceLocation.tryParse(parts[0]);
        try {
            return dimension == null ? null : CharterSavedData.get(player.server).binding(dimension, BlockPos.of(Long.parseLong(parts[1])));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    private static @Nullable ServerLevel level(ServerPlayer player, ResourceLocation dimension) {
        return player.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
    }

    static @Nullable UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    static Component message(String key) {
        return Component.translatable("charter.townstead.draft." + key);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
