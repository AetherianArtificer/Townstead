package com.aetherianartificer.townstead.politics.charter;

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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-owned Charter Bell bindings and prepared, exactly-once founding intents. */
public final class CharterSavedData extends SavedData {
    public static final String FILE_ID = "townstead_charters";
    private static final int SCHEMA = 4;
    private final Map<String, String> externalGovernments = new LinkedHashMap<>();
    private final java.util.List<Binding> archivedBindings = new java.util.ArrayList<>();
    private final Map<String, Amendment> amendments = new LinkedHashMap<>();
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
    public boolean commit(Proposal proposal, SettlementRef settlement, ResourceLocation polity, long now) {
        String key = key(proposal.dimension(), proposal.lectern());
        Proposal current = proposals.get(key);
        if (current == null || !current.token().equals(proposal.token())) return false;
        proposals.remove(key);
        bindings.put(key, new Binding(proposal.dimension(), proposal.lectern(), proposal.bell(), settlement,
                polity, proposal.initiator(), now));
        setDirty();
        return true;
    }

    /** Explicitly binds an already-known settlement without creating or changing political records. */
    public boolean bindExisting(ResourceLocation dimension, BlockPos lectern, BlockPos bell,
                                SettlementRef settlement, ResourceLocation polity, UUID actor, long now) {
        String key = key(dimension, lectern);
        if (bindings.containsKey(key) || proposals.containsKey(key)) return false;
        bindings.put(key, new Binding(dimension, lectern.immutable(), bell.immutable(), settlement, polity, actor, now));
        setDirty();
        return true;
    }

    public java.util.List<Binding> bindings() { return java.util.List.copyOf(bindings.values()); }

    public record Amendment(UUID token, UUID initiator, ResourceLocation polity, ResourceLocation dimension,
                            BlockPos lectern, BlockPos bell, String operation, String expectedName, String argument, long expiresAt) {}
    public void prepareAmendment(Amendment value) { amendments.put(value.polity().toString(), value); setDirty(); }
    public Amendment amendment(ResourceLocation polity) { return amendments.get(polity.toString()); }
    public Amendment amendmentAtBell(ResourceLocation dimension, BlockPos bell) {
        return amendments.values().stream().filter(a -> a.dimension().equals(dimension) && a.bell().equals(bell)).findFirst().orElse(null);
    }
    public boolean removeAmendment(Amendment value) {
        boolean removed = amendments.remove(value.polity().toString(), value);
        if (removed) setDirty(); return removed;
    }
    public java.util.List<Binding> unbind(ResourceLocation polity) {
        var removed = bindings.values().stream().filter(b -> b.polity().equals(polity)).toList();
        archivedBindings.addAll(removed);
        bindings.values().removeIf(b -> b.polity().equals(polity));
        amendments.remove(polity.toString()); setDirty(); return removed;
    }

    public java.util.List<Binding> archivedBindings() { return java.util.List.copyOf(archivedBindings); }

    public void expire(long now) {
        if (amendments.values().removeIf(value -> value.expiresAt() <= now)) setDirty();
        if (proposals.values().removeIf(value -> value.expiresAt() <= now)) setDirty();
    }

    public String externalGovernment(SettlementRef settlement) { return externalGovernments.getOrDefault(settlement.dimension() + "|" + settlement.villageId(), ""); }
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
        ListTag amendments = tag.getList("amendments", Tag.TAG_COMPOUND);
        for (int i = 0; i < amendments.size(); i++) {
            CompoundTag t = amendments.getCompound(i);
            try {
                var polity = ResourceLocation.tryParse(t.getString("polity"));
                var dimension = ResourceLocation.tryParse(t.getString("dimension"));
                if (polity == null || dimension == null || !t.hasUUID("token") || !t.hasUUID("initiator")) continue;
                var a = new Amendment(t.getUUID("token"), t.getUUID("initiator"), polity, dimension,
                        BlockPos.of(t.getLong("lectern")), BlockPos.of(t.getLong("bell")), t.getString("operation"),
                        t.getString("expected_name"), t.getString("argument"), t.getLong("expires_at"));
                data.amendments.put(polity.toString(), a);
            } catch (RuntimeException ignored) { }
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
        ListTag savedAmendments = new ListTag();
        for (var a : amendments.values()) {
            CompoundTag t = base(a.dimension(), a.lectern(), a.bell());
            t.putUUID("token", a.token()); t.putUUID("initiator", a.initiator());
            t.putString("polity", a.polity().toString()); t.putString("operation", a.operation());
            t.putString("expected_name", a.expectedName()); t.putString("argument", a.argument());
            t.putLong("expires_at", a.expiresAt()); savedAmendments.add(t);
        }
        tag.put("amendments", savedAmendments);
        return tag;
    }

    private static CompoundTag save(Binding value) {
        CompoundTag tag = base(value.dimension(), value.lectern(), value.bell());
        tag.putString("settlement_dimension", value.settlement().dimension().toString());
        tag.putInt("settlement_village", value.settlement().villageId());
        tag.putString("polity", value.polity().toString());
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
        ResourceLocation polity = ResourceLocation.tryParse(tag.getString("polity"));
        if (dimension == null || settlementDimension == null || polity == null || !tag.hasUUID("founder")) return null;
        return new Binding(dimension, BlockPos.of(tag.getLong("lectern")), BlockPos.of(tag.getLong("bell")),
                new SettlementRef(settlementDimension, tag.getInt("settlement_village")), polity,
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
                    tag.contains("faction_name") ? com.aetherianartificer.townstead.culture.FactionNaming.Name.load(tag.getCompound("faction_name"))
                            : com.aetherianartificer.townstead.culture.FactionNaming.Name.custom(tag.getString("name")));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record Binding(ResourceLocation dimension, BlockPos lectern, BlockPos bell,
                          SettlementRef settlement, ResourceLocation polity, UUID founder, long foundedAt) {}

    public record Proposal(UUID token, UUID initiator, ResourceLocation dimension, BlockPos lectern,
                           BlockPos bell, String name, ResourceLocation profile,
                           @Nullable ResourceLocation culture, long preparedAt, long expiresAt,
                           com.aetherianartificer.townstead.culture.FactionNaming.Name factionName) {
        public Proposal(UUID token, UUID initiator, ResourceLocation dimension, BlockPos lectern,
                        BlockPos bell, String name, ResourceLocation profile, ResourceLocation culture, long preparedAt, long expiresAt) {
            this(token, initiator, dimension, lectern, bell, name, profile, culture, preparedAt, expiresAt,
                    com.aetherianartificer.townstead.culture.FactionNaming.Name.custom(name));
        }
        public Proposal {
            name = name == null ? "" : name.trim();
            if (name.isEmpty() || name.length() > 48) throw new IllegalArgumentException("Invalid settlement name");
        }
    }
}
