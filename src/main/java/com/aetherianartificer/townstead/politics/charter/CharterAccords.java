package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.item.AccordLetterItem;
import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.relations.FactionRelations;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Accords between factions. A Charter proclaims an offer as a letter; an envoy carries it to the
 * other faction, whose representative (any office that gives {@code represent_faction}) answers.
 * A villager answers at once, by {@link AccordAcceptance}; a player answers from the requests in
 * their Charter.
 */
public final class CharterAccords {
    public static final ResourceLocation REPRESENT = ResourceLocation.tryParse("townstead:represent_faction");
    static final String REQUEST_KIND = "accord";
    private static final ResourceLocation PROVENANCE = ResourceLocation.tryParse("townstead:accord_letter");

    private CharterAccords() {}

    /** Why an accord between these two cannot be offered now, as a message key; null when it can. */
    static @Nullable String problem(PoliticalSavedData data, Faction proposer, @Nullable Faction recipient) {
        if (recipient == null || !recipient.active() || !proposer.active()) return "accord_unknown";
        if (recipient.id().equals(proposer.id())) return "accord_self";
        if (data.externalGovernment(recipient.id())) return "accord_external";
        if (!data.activeBetween(Party.faction(proposer.id()), Party.faction(recipient.id()), FactionRelations.ACCORD).isEmpty()) {
            return "accord_exists";
        }
        return null;
    }

    /** Every office of this faction that may answer for it, with who holds each. */
    static List<Holder> representatives(PoliticalSavedData data, Faction faction) {
        FactionKind kind = PoliticalDefinitions.snapshot().kind(faction.kind());
        List<Holder> out = new ArrayList<>();
        if (kind == null) return out;
        for (FactionKind.Office office : kind.offices()) {
            BondKind bond = BondKinds.all().get(office.bond());
            BondKind.Role role = bond == null ? null : bond.roleFor(BondKind.Party.FACTION);
            if (role == null || !role.gives().contains(REPRESENT)) continue;
            for (UUID holder : FactionBonds.holders(data, faction.id(), office.bond())) out.add(new Holder(holder, bond));
        }
        return out;
    }

    record Holder(UUID person, BondKind office) {}

    /** The letter a proclaimed offer produces, naming whom to find as things stand now. */
    static ItemStack letter(ServerPlayer ringer, PoliticalSavedData data, Faction proposer, Faction recipient) {
        List<Holder> holders = representatives(data, recipient);
        Holder first = holders.isEmpty() ? null : holders.get(0);
        String addressee = first == null ? "" : CharterPeople.name(ringer, first.person()).getString();
        String office = first == null ? "" : first.office().displayLangKey();
        String officeText = first == null ? "" : first.office().displayName().getString();
        return AccordLetterItem.create(Townstead.ACCORD_LETTER.get(), new AccordLetterItem.Letter(proposer.id(), proposer.name(),
                recipient.id(), recipient.name(), addressee, office, officeText, seat(ringer.server, data, recipient)));
    }

    private static String seat(MinecraftServer server, PoliticalSavedData data, Faction faction) {
        SeatInstance seat = data.seat(faction.id());
        SettlementRef settlement = seat != null ? seat.settlement() : faction.seatSettlement();
        if (settlement == null) return "";
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
        String village = level == null ? "" : VillageManager.get(level).getOrEmpty(settlement.villageId())
                .map(v -> v.getName()).orElse("");
        if (seat == null) return village;
        var at = seat.lectern();
        return (village.isEmpty() ? "" : village + " ") + "(" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")";
    }

    /**
     * An envoy hands the letter to someone. Only a representative of the faction it is for can
     * answer; a refusal leaves the letter with the envoy, to try again once trusted more.
     */
    public static boolean give(ServerPlayer envoy, LivingEntity receiver, ItemStack stack) {
        AccordLetterItem.Letter letter = AccordLetterItem.read(stack);
        if (letter == null) return false;
        PoliticalSavedData data = PoliticalSavedData.get(envoy.server);
        Faction recipient = data.faction(letter.recipient());
        if (recipient == null || !PoliticalAuthority.allowed(data, receiver.getUUID(), recipient.id(), REPRESENT)) {
            envoy.displayClientMessage(Component.translatable("townstead.accord.not_for_them", letter.recipientName()), true);
            return true;
        }
        answer(envoy, data, letter, receiver.getUUID(), receiver, stack);
        return true;
    }

    /**
     * An envoy leaves the letter at the faction's Charter lectern. A player representative answers
     * it from their Charter; a faction run by villagers answers at once.
     */
    static Component leave(ServerPlayer envoy, Faction lecternFaction, ItemStack stack) {
        AccordLetterItem.Letter letter = AccordLetterItem.read(stack);
        PoliticalSavedData data = PoliticalSavedData.get(envoy.server);
        if (letter == null || !data.canonical(letter.recipient()).equals(lecternFaction.id())) {
            return Component.translatable("townstead.accord.not_for_them", letter == null ? "" : letter.recipientName());
        }
        Faction proposer = data.faction(letter.proposer());
        String problem = proposer == null ? "accord_unknown" : problem(data, proposer, lecternFaction);
        if (problem != null) return CharterDrafts.message(problem);
        List<Holder> holders = representatives(data, lecternFaction);
        if (holders.isEmpty()) return Component.translatable("townstead.accord.nobody", lecternFaction.name());
        boolean playerAnswers = holders.stream().anyMatch(h -> isPlayer(envoy.server, h.person()));
        if (!playerAnswers) {
            UUID speaker = holders.get(0).person();
            answer(envoy, data, letter, speaker, AccordAcceptance.find(envoy.server, speaker), stack);
            return Component.empty();
        }
        long now = envoy.server.overworld().getGameTime();
        CharterRequests.get(envoy.server).put(new CharterRequests.Entry(UUID.randomUUID(), lecternFaction.id().toString(),
                envoy.getUUID(), proposer.name(), envoy.getUUID(), REQUEST_KIND, "pending", FactionRelations.ACCORD.toString(),
                "", now + 7 * 24000L, Set.of(), proposer.id().toString()));
        stack.shrink(1);
        return Component.translatable("townstead.accord.left", lecternFaction.name());
    }

    /** A player representative accepts an accord waiting in their Charter. */
    static String accept(ServerPlayer representative, Faction recipient, CharterRequests.Entry entry) {
        PoliticalSavedData data = PoliticalSavedData.get(representative.server);
        Faction proposer = data.faction(ResourceLocation.tryParse(entry.proposer()));
        String problem = proposer == null ? "accord_unknown" : problem(data, proposer, recipient);
        if (problem != null) return problem;
        return form(representative, data, proposer, recipient) ? null : "accord_unknown";
    }

    /** Whether this person may answer accords for the faction. */
    static boolean answers(PoliticalSavedData data, UUID person, Faction faction) {
        return PoliticalAuthority.allowed(data, person, faction.id(), REPRESENT);
    }

    private static void answer(ServerPlayer envoy, PoliticalSavedData data, AccordLetterItem.Letter letter,
                               UUID speaker, @Nullable LivingEntity speakerEntity, ItemStack stack) {
        Faction proposer = data.faction(letter.proposer());
        Faction recipient = data.faction(letter.recipient());
        String problem = proposer == null ? "accord_unknown" : problem(data, proposer, recipient);
        if (problem != null) {
            envoy.displayClientMessage(CharterDrafts.message(problem), true);
            return;
        }
        Component name = speakerEntity != null ? speakerEntity.getName() : CharterPeople.name(envoy, speaker);
        AccordAcceptance.Result result = AccordAcceptance.evaluate(envoy.server, envoy.getUUID(), envoy,
                proposer, recipient, speaker, speakerEntity);
        if (!result.accepted()) {
            envoy.displayClientMessage(Component.translatable("townstead.accord.declined", name, signed(result.total())), false);
            for (AccordAcceptance.Line line : result.lines()) envoy.displayClientMessage(describe(line), false);
            return;
        }
        if (!form(envoy, data, proposer, recipient)) return;
        stack.shrink(1);
        envoy.displayClientMessage(Component.translatable("townstead.accord.accepted", name, recipient.name(), proposer.name()), false);
    }

    /** One reason, as "  +30 Likes you". */
    public static Component describe(AccordAcceptance.Line line) {
        return Component.translatable("townstead.accord.line", signed(line.value()),
                Component.translatable("townstead.accord.line." + line.key()));
    }

    public static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private static boolean form(ServerPlayer witness, PoliticalSavedData data, Faction a, Faction b) {
        MinecraftServer server = witness.server;
        String role = "ally";
        List<BondInstance.Side> sides = List.of(new BondInstance.Side(role, Party.faction(a.id())),
                new BondInstance.Side(role, Party.faction(b.id())));
        boolean formed = FactionBonds.form(data, FactionRelations.ACCORD, sides, PROVENANCE, server.overworld().getGameTime()).formed();
        if (formed) {
            FactionRelations.invalidate();
            FactionChronicles.proclaimed(witness, b, "accord_formed", java.util.Map.of("ally", a.name()));
        }
        return formed;
    }

    /** Ends the accord between the two, from either side. */
    static void end(MinecraftServer server, PoliticalSavedData data, Faction self, ResourceLocation other) {
        long now = server.overworld().getGameTime();
        for (BondInstance bond : data.activeBetween(Party.faction(self.id()), Party.faction(other), FactionRelations.ACCORD)) {
            FactionBonds.end(data, bond, now, "ended");
        }
        FactionRelations.invalidate();
    }

    /** The factions this one holds an accord with. */
    static Set<ResourceLocation> allies(PoliticalSavedData data, Faction faction) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        Party self = Party.faction(faction.id());
        for (BondInstance bond : data.activeBonds(self)) {
            if (!bond.kind().equals(FactionRelations.ACCORD)) continue;
            Party other = bond.other(self);
            if (other != null && other.isFaction()) out.add(other.faction());
        }
        return out;
    }

    private static boolean isPlayer(MinecraftServer server, UUID person) {
        if (server.getPlayerList().getPlayer(person) != null) return true;
        var cache = server.getProfileCache();
        return cache != null && cache.get(person).isPresent();
    }
}
