package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import java.util.*;

/** Durable requests. Memberships themselves remain in PoliticalSavedData. */
public final class CharterRequests extends SavedData {
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private long revision;
    public record Entry(UUID id, String organization, UUID person, String personName, UUID initiator,
                        String kind, String state, String policy, String membership, long due, Set<UUID> approvals) {
        public Entry { approvals = Set.copyOf(approvals); }
        public Entry state(String value) { return new Entry(id, organization, person, personName, initiator, kind, value, policy, membership, due, approvals); }
        public Entry approve(UUID voter) {
            Set<UUID> votes = new LinkedHashSet<>(approvals); votes.add(voter);
            return new Entry(id, organization, person, personName, initiator, kind, state, policy, membership, due, votes);
        }
        public boolean open() { return state.equals("pending") || state.equals("offered") || state.equals("notice"); }
    }
    public long revision() { return revision; }
    public List<Entry> entries() { return List.copyOf(entries.values()); }
    public Entry get(UUID id) { return entries.get(id); }
    public void put(Entry entry) { entries.put(entry.id(), entry); revision++; setDirty(); }
    public static CharterRequests get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(CharterRequests::new, CharterRequests::load), "townstead_charter_requests");
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(CharterRequests::load, CharterRequests::new, "townstead_charter_requests");
        *///?}
    }
    //? if >=1.21 {
    public static CharterRequests load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static CharterRequests load(CompoundTag tag) {
    *///?}
        CharterRequests data = new CharterRequests();
        data.revision = tag.getLong("revision");
        ListTag list = tag.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Set<UUID> votes = new LinkedHashSet<>();
            ListTag voteTags = t.getList("approvals", Tag.TAG_COMPOUND);
            for (int j = 0; j < voteTags.size(); j++) votes.add(voteTags.getCompound(j).getUUID("person"));
            Entry e = new Entry(t.getUUID("id"), t.getString("organization"), t.getUUID("person"), t.getString("name"),
                    t.getUUID("initiator"), t.getString("kind"), t.getString("state"), t.getString("policy"), t.getString("membership"), t.getLong("due"), votes);
            data.entries.put(e.id(), e);
        }
        return data;
    }
    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putLong("revision", revision);
        ListTag list = new ListTag();
        for (Entry e : entries.values()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("id", e.id()); t.putString("organization", e.organization()); t.putUUID("person", e.person());
            t.putString("name", e.personName()); t.putUUID("initiator", e.initiator()); t.putString("kind", e.kind());
            t.putString("state", e.state()); t.putString("policy", e.policy()); t.putLong("due", e.due()); t.putString("membership", e.membership());
            ListTag votes = new ListTag();
            for (UUID person : e.approvals()) { CompoundTag v = new CompoundTag(); v.putUUID("person", person); votes.add(v); }
            t.put("approvals", votes); list.add(t);
        }
        tag.put("requests", list); return tag;
    }
}
