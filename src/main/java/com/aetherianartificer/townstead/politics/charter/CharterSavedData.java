package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.culture.FactionNaming;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-owned Charter Bell bindings, founding intents, and each faction's draft amendment. */
public final class CharterSavedData extends SavedData {
    public static final String FILE_ID = "townstead_charters";
    private static final int SCHEMA = 5;
    private final Map<String, String> externalGovernments = new LinkedHashMap<>();
    private final List<Binding> archivedBindings = new ArrayList<>();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Map<String, Binding> bindings = new LinkedHashMap<>();
    private final Map<String, Proposal> proposals = new LinkedHashMap<>();

    public static CharterSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(CharterSavedData::new, CharterSavedData::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                CharterSavedData::load, CharterSavedData::new, FILE_ID);
        *///?}
    }

    public @Nullable Binding binding(ResourceLocation dimension, BlockPos lectern) {
        return bindings.get(key(dimension, lectern));
    }

    public @Nullable Proposal proposal(ResourceLocation dimension, BlockPos lectern) {
        return proposals.get(key(dimension, lectern));
    }

    public @Nullable Proposal proposalAtBell(ResourceLocation dimension, BlockPos bell) {
        for (Proposal value : proposals.values()) {
            if (value.dimension().equals(dimension) && value.bell().equals(bell)) return value;
        }
        return null;
    }

    public void prepare(Proposal proposal) {
        proposals.put(key(proposal.dimension(), proposal.lectern()), proposal);
        setDirty();
    }

    public boolean cancel(ResourceLocation dimension, BlockPos lectern, UUID actor) {
        String key = key(dimension, lectern);
        Proposal proposal = proposals.get(key);
        if (proposal != null && proposal.initiator().equals(actor)) {
            proposals.remove(key);
            setDirty();
            return true;
        }
        return false;
    }

    /** Removes the proposal before publishing the binding, making repeat ring delivery harmless. */
    public boolean commit(Proposal proposal, SettlementRef settlement, ResourceLocation faction, long now) {
        String key = key(proposal.dimension(), proposal.lectern());
        Proposal current = proposals.get(key);
        if (current == null || !current.token().equals(proposal.token())) return false;
        proposals.remove(key);
        bindings.put(key, new Binding(proposal.dimension(), proposal.lectern(), proposal.bell(), settlement,
                faction, proposal.initiator(), now));
        setDirty();
        return true;
    }

    /** Explicitly binds an already-known settlement without creating or changing political records. */
    public boolean bindExisting(ResourceLocation dimension, BlockPos lectern, BlockPos bell,
                                SettlementRef settlement, ResourceLocation faction, UUID actor, long now) {
        String key = key(dimension, lectern);
        if (bindings.containsKey(key) || proposals.containsKey(key)) return false;
        bindings.put(key, new Binding(dimension, lectern.immutable(), bell.immutable(), settlement, faction, actor, now));
        setDirty();
        return true;
    }

    public List<Binding> bindings() { return List.copyOf(bindings.values()); }

    public List<Binding> unbind(ResourceLocation faction) {
        var removed = bindings.values().stream().filter(b -> b.faction().equals(faction)).toList();
        archivedBindings.addAll(removed);
        bindings.values().removeIf(b -> b.faction().equals(faction));
        drafts.remove(faction.toString());
        setDirty();
        return removed;
    }

    public List<Binding> archivedBindings() { return List.copyOf(archivedBindings); }

    public @Nullable Draft draft(ResourceLocation faction) { return drafts.get(faction.toString()); }

    public void putDraft(Draft draft) {
        drafts.put(draft.faction().toString(), draft);
        setDirty();
    }

    public boolean removeDraft(ResourceLocation faction, UUID token) {
        Draft current = drafts.get(faction.toString());
        if (current == null || !current.token().equals(token)) return false;
        drafts.remove(faction.toString());
        setDirty();
        return true;
    }

    /** The prepared draft waiting at this bell, if any. */
    public @Nullable Draft draftAtBell(ResourceLocation dimension, BlockPos bell) {
        for (Draft value : drafts.values()) {
            if (value.prepared() && value.dimension().equals(dimension) && value.bell().equals(bell)) return value;
        }
        return null;
    }

    /** A prepared draft that lapses returns to an unsigned draft rather than vanishing. */
    public void expire(long now) {
        for (Draft value : List.copyOf(drafts.values())) {
            if (value.prepared() && value.expiresAt() <= now) putDraft(value.unsigned());
        }
        if (proposals.values().removeIf(value -> value.expiresAt() <= now)) setDirty();
    }

    public String externalGovernment(SettlementRef settlement) {
        return externalGovernments.getOrDefault(settlement.dimension() + "|" + settlement.villageId(), "");
    }

    public void observeGovernment(SettlementRef settlement, String actor) {
        String key = settlement.dimension() + "|" + settlement.villageId();
        if (!actor.equals(externalGovernments.put(key, actor))) setDirty();
    }

    private static String key(ResourceLocation dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    //? if >=1.21 {
    public static CharterSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static CharterSavedData load(CompoundTag tag) {
    *///?}
        CharterSavedData data = new CharterSavedData();
        CompoundTag external = tag.getCompound("external_governments");
        for (String key : external.getAllKeys()) data.externalGovernments.put(key, external.getString(key));
        ListTag bindings = tag.getList("bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < bindings.size(); i++) {
            Binding value = readBinding(bindings.getCompound(i));
            if (value != null) data.bindings.put(key(value.dimension(), value.lectern()), value);
        }
        ListTag archived = tag.getList("archived_bindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < archived.size(); i++) {
            Binding value = readBinding(archived.getCompound(i));
            if (value != null) data.archivedBindings.add(value);
        }
        ListTag proposals = tag.getList("proposals", Tag.TAG_COMPOUND);
        for (int i = 0; i < proposals.size(); i++) {
            Proposal value = readProposal(proposals.getCompound(i));
            if (value != null) data.proposals.put(key(value.dimension(), value.lectern()), value);
        }
        ListTag drafts = tag.getList("drafts", Tag.TAG_COMPOUND);
        for (int i = 0; i < drafts.size(); i++) {
            Draft value = Draft.read(drafts.getCompound(i));
            if (value != null) data.drafts.put(value.faction().toString(), value);
        }
        if (tag.getInt("schema") < SCHEMA) data.setDirty();
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putInt("schema", SCHEMA);
        CompoundTag external = new CompoundTag();
        externalGovernments.forEach(external::putString);
        tag.put("external_governments", external);
        ListTag savedBindings = new ListTag();
        bindings.values().forEach(value -> savedBindings.add(save(value)));
        tag.put("bindings", savedBindings);
        ListTag archived = new ListTag();
        archivedBindings.forEach(value -> archived.add(save(value)));
        tag.put("archived_bindings", archived);
        ListTag savedProposals = new ListTag();
        proposals.values().forEach(value -> savedProposals.add(save(value)));
        tag.put("proposals", savedProposals);
        ListTag savedDrafts = new ListTag();
        drafts.values().forEach(value -> savedDrafts.add(value.save()));
        tag.put("drafts", savedDrafts);
        return tag;
    }

    private static CompoundTag save(Binding value) {
        CompoundTag tag = base(value.dimension(), value.lectern(), value.bell());
        tag.putString("settlement_dimension", value.settlement().dimension().toString());
        tag.putInt("settlement_village", value.settlement().villageId());
        tag.putString("faction", value.faction().toString());
        tag.putUUID("founder", value.founder());
        tag.putLong("founded_at", value.foundedAt());
        return tag;
    }

    private static CompoundTag save(Proposal value) {
        CompoundTag tag = base(value.dimension(), value.lectern(), value.bell());
        tag.putUUID("token", value.token());
        tag.putUUID("initiator", value.initiator());
        tag.putString("name", value.name());
        tag.put("faction_name", value.factionName().save());
        tag.putString("profile", value.profile().toString());
        if (value.culture() != null) tag.putString("culture", value.culture().toString());
        tag.putLong("prepared_at", value.preparedAt());
        tag.putLong("expires_at", value.expiresAt());
        return tag;
    }

    private static CompoundTag base(ResourceLocation dimension, BlockPos lectern, BlockPos bell) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", dimension.toString());
        tag.putLong("lectern", lectern.asLong());
        tag.putLong("bell", bell.asLong());
        return tag;
    }

    private static @Nullable Binding readBinding(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ResourceLocation settlementDimension = ResourceLocation.tryParse(tag.getString("settlement_dimension"));
        ResourceLocation faction = ResourceLocation.tryParse(tag.getString(tag.contains("faction") ? "faction" : "polity"));
        if (dimension == null || settlementDimension == null || faction == null || !tag.hasUUID("founder")) return null;
        return new Binding(dimension, BlockPos.of(tag.getLong("lectern")), BlockPos.of(tag.getLong("bell")),
                new SettlementRef(settlementDimension, tag.getInt("settlement_village")), faction,
                tag.getUUID("founder"), tag.getLong("founded_at"));
    }

    private static @Nullable Proposal readProposal(CompoundTag tag) {
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        ResourceLocation profile = ResourceLocation.tryParse(tag.getString("profile"));
        ResourceLocation culture = tag.contains("culture", Tag.TAG_STRING)
                ? ResourceLocation.tryParse(tag.getString("culture")) : null;
        if (dimension == null || profile == null || !tag.hasUUID("token") || !tag.hasUUID("initiator")) return null;
        try {
            return new Proposal(tag.getUUID("token"), tag.getUUID("initiator"), dimension,
                    BlockPos.of(tag.getLong("lectern")), BlockPos.of(tag.getLong("bell")), tag.getString("name"),
                    profile, culture, tag.getLong("prepared_at"), tag.getLong("expires_at"),
                    tag.contains("faction_name") ? FactionNaming.Name.load(tag.getCompound("faction_name"))
                            : FactionNaming.Name.custom(tag.getString("name")));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record Binding(ResourceLocation dimension, BlockPos lectern, BlockPos bell,
                          SettlementRef settlement, ResourceLocation faction, UUID founder, long foundedAt) {}

    public record Proposal(UUID token, UUID initiator, ResourceLocation dimension, BlockPos lectern,
                           BlockPos bell, String name, ResourceLocation profile,
                           @Nullable ResourceLocation culture, long preparedAt, long expiresAt,
                           FactionNaming.Name factionName) {
        public Proposal(UUID token, UUID initiator, ResourceLocation dimension, BlockPos lectern,
                        BlockPos bell, String name, ResourceLocation profile, ResourceLocation culture, long preparedAt, long expiresAt) {
            this(token, initiator, dimension, lectern, bell, name, profile, culture, preparedAt, expiresAt,
                    FactionNaming.Name.custom(name));
        }

        public Proposal {
            name = name == null ? "" : name.trim();
            if (name.isEmpty() || name.length() > 48) throw new IllegalArgumentException("Invalid settlement name");
        }
    }

    /**
     * One proposed change. {@code type} is {@code rename}, {@code heraldry}, {@code seat},
     * {@code transfer_leadership} or {@code dissolve}; {@code target} and {@code argument} are that
     * change's own values; {@code expected} is what the target read when drafted, rechecked at the bell.
     */
    public record Clause(String type, String target, String argument, String expected) {
        public Clause {
            target = target == null ? "" : target;
            argument = argument == null ? "" : argument;
            expected = expected == null ? "" : expected;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("type", type);
            tag.putString("target", target);
            tag.putString("argument", argument);
            tag.putString("expected", expected);
            return tag;
        }

        static Clause read(CompoundTag tag) {
            return new Clause(tag.getString("type"), tag.getString("target"), tag.getString("argument"), tag.getString("expected"));
        }
    }

    /**
     * A faction's amendment in the making. Clauses gather at the lectern; the people governance names
     * sign it; once signed it is prepared for its bell until {@code expiresAt}. Changing a clause
     * clears the signatures. {@code signedOn} holds each signer's calendar day, for the mark.
     */
    public record Draft(UUID token, ResourceLocation faction, UUID author, ResourceLocation dimension,
                        BlockPos lectern, BlockPos bell, List<Clause> clauses, Set<UUID> signatures,
                        long expiresAt, Map<UUID, Long> signedOn) {
        public static final long UNSIGNED = 0L;

        public Draft {
            clauses = List.copyOf(clauses);
            signatures = Set.copyOf(new LinkedHashSet<>(signatures));
            Map<UUID, Long> days = new LinkedHashMap<>(signedOn);
            days.keySet().retainAll(signatures);
            signedOn = Map.copyOf(days);
        }

        public Draft(UUID token, ResourceLocation faction, UUID author, ResourceLocation dimension,
                     BlockPos lectern, BlockPos bell, List<Clause> clauses, Set<UUID> signatures, long expiresAt) {
            this(token, faction, author, dimension, lectern, bell, clauses, signatures, expiresAt, Map.of());
        }

        public boolean prepared() {
            return expiresAt != UNSIGNED;
        }

        public Draft withClauses(List<Clause> value) {
            return new Draft(UUID.randomUUID(), faction, author, dimension, lectern, bell, value, Set.of(), UNSIGNED);
        }

        public Draft signed(UUID person) {
            return signed(person, -1L);
        }

        /** {@code day} is the calendar day of the signature, or -1 when unknown. */
        public Draft signed(UUID person, long day) {
            Set<UUID> next = new LinkedHashSet<>(signatures);
            next.add(person);
            Map<UUID, Long> days = new LinkedHashMap<>(signedOn);
            if (day >= 0) days.put(person, day);
            return new Draft(token, faction, author, dimension, lectern, bell, clauses, next, expiresAt, days);
        }

        public Draft preparedUntil(long time) {
            return new Draft(token, faction, author, dimension, lectern, bell, clauses, signatures, time, signedOn);
        }

        public Draft unsigned() {
            return new Draft(token, faction, author, dimension, lectern, bell, clauses, Set.of(), UNSIGNED);
        }

        CompoundTag save() {
            CompoundTag tag = base(dimension, lectern, bell);
            tag.putUUID("token", token);
            tag.putString("faction", faction.toString());
            tag.putUUID("author", author);
            ListTag list = new ListTag();
            clauses.forEach(clause -> list.add(clause.save()));
            tag.put("clauses", list);
            ListTag signed = new ListTag();
            for (UUID person : signatures) {
                CompoundTag entry = new CompoundTag();
                entry.putUUID("person", person);
                Long day = signedOn.get(person);
                if (day != null) entry.putLong("day", day);
                signed.add(entry);
            }
            tag.put("signatures", signed);
            tag.putLong("expires_at", expiresAt);
            return tag;
        }

        static @Nullable Draft read(CompoundTag tag) {
            ResourceLocation faction = ResourceLocation.tryParse(tag.getString("faction"));
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
            if (faction == null || dimension == null || !tag.hasUUID("token") || !tag.hasUUID("author")) return null;
            List<Clause> clauses = new ArrayList<>();
            ListTag list = tag.getList("clauses", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) clauses.add(Clause.read(list.getCompound(i)));
            Set<UUID> signatures = new LinkedHashSet<>();
            Map<UUID, Long> days = new LinkedHashMap<>();
            ListTag signed = tag.getList("signatures", Tag.TAG_COMPOUND);
            for (int i = 0; i < signed.size(); i++) {
                CompoundTag entry = signed.getCompound(i);
                UUID person = entry.getUUID("person");
                signatures.add(person);
                if (entry.contains("day")) days.put(person, entry.getLong("day"));
            }
            return new Draft(tag.getUUID("token"), faction, tag.getUUID("author"), dimension,
                    BlockPos.of(tag.getLong("lectern")), BlockPos.of(tag.getLong("bell")), clauses, signatures,
                    tag.getLong("expires_at"), days);
        }
    }
}
