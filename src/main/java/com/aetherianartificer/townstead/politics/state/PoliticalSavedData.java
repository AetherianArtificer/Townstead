package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.api.impl.v1.PoliticalEvents;
import com.aetherianartificer.townstead.culture.FactionNaming;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-wide factions, the bonds between parties, and what hangs off a faction: Seats, names, legitimacy. */
public final class PoliticalSavedData extends SavedData {
    public static final String FILE_ID = "townstead_politics";
    static final int SCHEMA_VERSION = 6;

    private final Map<ResourceLocation, Faction> factions = new LinkedHashMap<>();
    private final Map<ResourceLocation, BondInstance> bonds = new LinkedHashMap<>();
    private final Map<Party, Set<ResourceLocation>> bondsByParty = new HashMap<>();
    private final Map<SettlementRef, SettlementFoundingRecord> foundingRecords = new LinkedHashMap<>();
    private final Map<ResourceLocation, SeatInstance> seats = new LinkedHashMap<>();
    private final Map<ResourceLocation, Double> legitimacy = new LinkedHashMap<>();
    private final Map<ResourceLocation, FactionNaming.Name> factionNames = new LinkedHashMap<>();
    private final Set<ResourceLocation> externalGovernments = new HashSet<>();
    /** Disposition groups a faction has declared its people welcome, such as {@code vampire}. */
    private final Map<ResourceLocation, Set<String>> welcomes = new LinkedHashMap<>();
    /** Pre-faction organization and polity ids, mapped to the faction that replaced them. */
    private final Map<ResourceLocation, ResourceLocation> legacyIds = new LinkedHashMap<>();
    private MinecraftServer server;

    public PoliticalSavedData() {}

    public static PoliticalSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        PoliticalSavedData data = overworld.getDataStorage().computeIfAbsent(
                new Factory<>(PoliticalSavedData::new, PoliticalSavedData::load), FILE_ID);
        //?} else {
        /*PoliticalSavedData data = overworld.getDataStorage().computeIfAbsent(
                PoliticalSavedData::load, PoliticalSavedData::new, FILE_ID);
        *///?}
        data.server = server;
        return data;
    }

    public @Nullable Faction faction(@Nullable ResourceLocation id) {
        if (id == null) return null;
        Faction direct = factions.get(id);
        if (direct != null) return direct;
        ResourceLocation mapped = legacyIds.get(id);
        return mapped == null ? null : factions.get(mapped);
    }

    /** The faction holding this settlement: an active one if any, else the last dissolved one. */
    public @Nullable Faction faction(SettlementRef settlement) {
        Faction archived = null;
        for (Faction faction : factions.values()) {
            if (!faction.settlements().contains(settlement)) continue;
            if (faction.status() != Faction.Status.DISSOLVED) return faction;
            archived = faction;
        }
        return archived;
    }

    public Collection<Faction> factions() { return List.copyOf(factions.values()); }

    /** The current id for an id that may predate factions. */
    public ResourceLocation canonical(ResourceLocation id) {
        if (id == null || factions.containsKey(id)) return id;
        return legacyIds.getOrDefault(id, id);
    }

    public @Nullable BondInstance bond(ResourceLocation id) { return bonds.get(id); }

    public Collection<BondInstance> bonds() { return List.copyOf(bonds.values()); }

    public List<BondInstance> bonds(Party party) {
        Set<ResourceLocation> ids = bondsByParty.get(party);
        if (ids == null) return List.of();
        List<BondInstance> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            BondInstance bond = bonds.get(id);
            if (bond != null) out.add(bond);
        }
        return out;
    }

    public List<BondInstance> activeBonds(Party party) {
        List<BondInstance> out = new ArrayList<>();
        for (BondInstance bond : bonds(party)) if (bond.active()) out.add(bond);
        return out;
    }

    /** Active bonds of one kind between these two parties. */
    public List<BondInstance> activeBetween(Party a, Party b, ResourceLocation kind) {
        List<BondInstance> out = new ArrayList<>();
        for (BondInstance bond : bonds(a)) if (bond.active() && bond.kind().equals(kind) && bond.involves(b)) out.add(bond);
        return out;
    }

    public @Nullable SettlementFoundingRecord founding(SettlementRef settlement) { return foundingRecords.get(settlement); }

    public Collection<SettlementFoundingRecord> foundingRecords() { return List.copyOf(foundingRecords.values()); }

    public @Nullable SeatInstance seat(ResourceLocation faction) { return seats.get(canonical(faction)); }

    public Collection<SeatInstance> seats() { return List.copyOf(seats.values()); }

    /** A faction's stored legitimacy (0 to 100), or null before it was first computed. */
    public @Nullable Double legitimacy(ResourceLocation faction) { return legitimacy.get(canonical(faction)); }

    public void setLegitimacy(ResourceLocation faction, double value) {
        legitimacy.put(faction, Math.max(0.0, Math.min(100.0, value)));
        setDirty();
    }

    public void clearLegitimacy(ResourceLocation faction) {
        if (legitimacy.remove(faction) != null) setDirty();
    }

    public @Nullable FactionNaming.Name factionName(ResourceLocation id) { return factionNames.get(id); }

    public void putFactionName(ResourceLocation id, FactionNaming.Name name) {
        factionNames.put(id, name);
        setDirty();
    }

    public Set<String> welcomes(ResourceLocation faction) {
        Set<String> groups = welcomes.get(canonical(faction));
        return groups == null ? Set.of() : Set.copyOf(groups);
    }

    public boolean anyWelcomes() {
        return !welcomes.isEmpty();
    }

    public void setWelcome(ResourceLocation faction, String group, boolean welcome) {
        ResourceLocation id = canonical(faction);
        boolean changed = welcome ? welcomes.computeIfAbsent(id, key -> new LinkedHashSet<>()).add(group)
                : welcomes.containsKey(id) && welcomes.get(id).remove(group);
        if (!welcome && welcomes.containsKey(id) && welcomes.get(id).isEmpty()) welcomes.remove(id);
        if (!changed) return;
        setDirty();
        com.aetherianartificer.townstead.politics.relations.FactionRelations.invalidate();
    }

    public void markExternalGovernment(ResourceLocation faction) {
        if (externalGovernments.add(faction)) setDirty();
    }

    /** True when another mod, such as MCA Capitals, governs this faction's settlement instead. */
    public boolean externalGovernment(ResourceLocation id) {
        if (externalGovernments.contains(id)) return true;
        Faction value = factions.get(id);
        if (server != null && value != null && value.settlements().stream().anyMatch(settlement ->
                com.aetherianartificer.townstead.politics.charter.CivicProviders.ownsGovernment(server, settlement))) {
            markExternalGovernment(id);
            return true;
        }
        return false;
    }

    public void putFaction(Faction value) {
        PoliticalEvents.beforeFactionWrite(this);
        Faction before = factions.put(value.id(), value);
        PoliticalEvents.faction(this, before, value);
        setDirty();
    }

    /** Stores a bond as given. Rules for forming and ending bonds live in {@link FactionBonds}. */
    public void putBond(BondInstance value) {
        for (BondInstance.Side side : value.sides()) {
            if (side.party().isFaction() && !factions.containsKey(side.party().faction())) {
                throw new IllegalArgumentException("Unknown faction " + side.party().id());
            }
        }
        BondInstance before = bonds.put(value.id(), value);
        index(value);
        PoliticalEvents.bond(before, value);
        setDirty();
    }

    public void putFounding(SettlementFoundingRecord value) {
        if (faction(value.settlement()) == null) {
            throw new IllegalArgumentException("Founding record has no faction for " + value.settlement());
        }
        SettlementFoundingRecord before = foundingRecords.put(value.settlement(), value);
        PoliticalEvents.founding(before, value);
        setDirty();
    }

    /** A faction has at most one Seat; writing a Seat for a faction that has one moves it. */
    public void putSeat(SeatInstance value) {
        if (!factions.containsKey(value.faction())) throw new IllegalArgumentException("Unknown faction " + value.faction());
        SeatInstance before = seats.put(value.faction(), value);
        PoliticalEvents.seat(before, value);
        com.aetherianartificer.townstead.politics.seat.SeatNotices.changed(before, value);
        setDirty();
    }

    public void removeSeat(ResourceLocation faction, String reason) {
        SeatInstance before = seats.remove(faction);
        if (before == null) return;
        PoliticalEvents.seatLost(before, reason);
        com.aetherianartificer.townstead.politics.seat.SeatNotices.lost(before, reason);
        setDirty();
    }

    private void index(BondInstance bond) {
        for (BondInstance.Side side : bond.sides()) {
            bondsByParty.computeIfAbsent(side.party(), key -> new LinkedHashSet<>()).add(bond.id());
        }
    }

    //? if >=1.21 {
    public static PoliticalSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static PoliticalSavedData load(CompoundTag tag) {
    *///?}
        PoliticalSavedData data = new PoliticalSavedData();
        if (tag.getInt("schema_version") < SCHEMA_VERSION && !tag.contains("factions", Tag.TAG_LIST)) {
            LegacyPolitics.migrate(tag, data);
            data.setDirty();
            return data;
        }
        read(tag, "factions", PoliticalNbt::faction, value -> data.factions.put(value.id(), value));
        read(tag, "bonds", PoliticalNbt::bond, value -> {
            data.bonds.put(value.id(), value);
            data.index(value);
        });
        read(tag, "founding_records", PoliticalNbt::founding, value -> data.foundingRecords.put(value.settlement(), value));
        read(tag, "seats", PoliticalNbt::seat, value -> data.seats.put(value.faction(), value));
        CompoundTag legitimacy = tag.getCompound("legitimacy");
        for (String key : legitimacy.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) data.legitimacy.put(id, legitimacy.getDouble(key));
        }
        ListTag external = tag.getList("external_governments", Tag.TAG_STRING);
        for (int i = 0; i < external.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(external.getString(i));
            if (id != null) data.externalGovernments.add(id);
        }
        CompoundTag names = tag.getCompound("faction_names");
        for (String key : names.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && data.factions.containsKey(id)) data.factionNames.put(id, FactionNaming.Name.load(names.getCompound(key)));
        }
        CompoundTag welcomeTag = tag.getCompound("welcomes");
        for (String key : welcomeTag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            ListTag groups = welcomeTag.getList(key, Tag.TAG_STRING);
            if (id == null || groups.isEmpty()) continue;
            Set<String> set = new LinkedHashSet<>();
            for (int i = 0; i < groups.size(); i++) set.add(groups.getString(i));
            data.welcomes.put(id, set);
        }
        CompoundTag legacy = tag.getCompound("legacy_ids");
        for (String key : legacy.getAllKeys()) {
            ResourceLocation from = ResourceLocation.tryParse(key), to = ResourceLocation.tryParse(legacy.getString(key));
            if (from != null && to != null) data.legacyIds.put(from, to);
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putInt("schema_version", SCHEMA_VERSION);
        tag.put("factions", write(factions.values(), PoliticalNbt::save));
        tag.put("bonds", write(bonds.values(), PoliticalNbt::save));
        tag.put("founding_records", write(foundingRecords.values(), PoliticalNbt::save));
        tag.put("seats", write(seats.values(), PoliticalNbt::save));
        CompoundTag legitimacyTag = new CompoundTag();
        legitimacy.forEach((id, value) -> legitimacyTag.putDouble(id.toString(), value));
        tag.put("legitimacy", legitimacyTag);
        ListTag external = new ListTag();
        externalGovernments.forEach(id -> external.add(StringTag.valueOf(id.toString())));
        tag.put("external_governments", external);
        CompoundTag names = new CompoundTag();
        factionNames.forEach((id, name) -> names.put(id.toString(), name.save()));
        tag.put("faction_names", names);
        CompoundTag welcomeTag = new CompoundTag();
        welcomes.forEach((id, groups) -> {
            ListTag list = new ListTag();
            groups.forEach(group -> list.add(StringTag.valueOf(group)));
            welcomeTag.put(id.toString(), list);
        });
        tag.put("welcomes", welcomeTag);
        CompoundTag legacy = new CompoundTag();
        legacyIds.forEach((from, to) -> legacy.putString(from.toString(), to.toString()));
        tag.put("legacy_ids", legacy);
        return tag;
    }

    /** Raw writes for the one-time conversion of an older save; no events. */
    void restore(Faction faction) { factions.put(faction.id(), faction); }

    void restore(BondInstance bond) { bonds.put(bond.id(), bond); index(bond); }

    void restore(SettlementFoundingRecord record) { foundingRecords.put(record.settlement(), record); }

    void restore(SeatInstance seat) { seats.put(seat.faction(), seat); }

    void restoreLegitimacy(ResourceLocation faction, double value) { legitimacy.put(faction, value); }

    void restoreName(ResourceLocation faction, FactionNaming.Name name) { factionNames.put(faction, name); }

    void restoreExternal(ResourceLocation faction) { externalGovernments.add(faction); }

    void restoreLegacy(ResourceLocation from, ResourceLocation to) { if (!from.equals(to)) legacyIds.put(from, to); }

    boolean hasFaction(ResourceLocation id) { return factions.containsKey(id); }

    private static <T> void read(CompoundTag root, String key, Decoder<T> decoder, Sink<T> sink) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            T decoded = decoder.read(list.getCompound(i));
            if (decoded != null) sink.accept(decoded);
        }
    }

    private static <T> ListTag write(Collection<T> values, Encoder<T> encoder) {
        ListTag list = new ListTag();
        for (T value : values) list.add(encoder.write(value));
        return list;
    }

    @FunctionalInterface interface Decoder<T> { @Nullable T read(CompoundTag tag); }
    @FunctionalInterface private interface Encoder<T> { CompoundTag write(T value); }
    @FunctionalInterface interface Sink<T> { void accept(T value); }
}
