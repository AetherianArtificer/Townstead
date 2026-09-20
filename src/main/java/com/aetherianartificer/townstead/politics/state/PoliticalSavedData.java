package com.aetherianartificer.townstead.politics.state;

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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-wide persistent political identities and person-to-actor relationships. */
public final class PoliticalSavedData extends SavedData {
    public static final String FILE_ID = "townstead_politics";
    private static final int SCHEMA_VERSION = 4;
    private final Map<ResourceLocation, com.aetherianartificer.townstead.culture.FactionNaming.Name> factionNames = new LinkedHashMap<>();
    public com.aetherianartificer.townstead.culture.FactionNaming.Name factionName(ResourceLocation id) { return factionNames.get(id); }
    public void putFactionName(ResourceLocation id, com.aetherianartificer.townstead.culture.FactionNaming.Name name) {
        factionNames.put(id, name); setDirty();
    }
    private final java.util.Set<ResourceLocation> externalGovernments = new java.util.HashSet<>();
    public void markExternalGovernment(ResourceLocation polity) { if (externalGovernments.add(polity)) setDirty(); }
    private MinecraftServer server;
    public boolean externalGovernment(ResourceLocation polity) {
        if (externalGovernments.contains(polity)) return true;
        PolityInstance value = polities.get(polity);
        if (server != null && value != null && value.settlements().stream().anyMatch(settlement ->
                com.aetherianartificer.townstead.politics.charter.CivicProviders.ownsGovernment(server, settlement))) {
            markExternalGovernment(polity);
            return true;
        }
        return false;
    }
    public boolean supersededGovernment(ResourceLocation organization) {
        return polities.values().stream().anyMatch(p -> organization.equals(p.governmentOrganization()) && externalGovernment(p.id()));
    }

    private final Map<ResourceLocation, OrganizationInstance> organizations = new LinkedHashMap<>();
    private final Map<ResourceLocation, PolityInstance> polities = new LinkedHashMap<>();
    private final Map<ResourceLocation, AffiliationInstance> affiliations = new LinkedHashMap<>();
    private final Map<ResourceLocation, MembershipInstance> memberships = new LinkedHashMap<>();
    private final Map<SettlementRef, SettlementFoundingRecord> foundingRecords = new LinkedHashMap<>();

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

    public @Nullable OrganizationInstance organization(ResourceLocation id) { return organizations.get(id); }
    public @Nullable PolityInstance polity(ResourceLocation id) { return polities.get(id); }

    public @Nullable PolityInstance polity(SettlementRef settlement) {
        PolityInstance archived = null;
        for (PolityInstance polity : polities.values()) {
            if (!polity.settlements().contains(settlement)) continue;
            if (polity.status() != PoliticalStatus.Polity.DISSOLVED) return polity;
            archived = polity;
        }
        return archived;
    }
    public @Nullable AffiliationInstance affiliation(ResourceLocation id) { return affiliations.get(id); }
    public @Nullable MembershipInstance membership(ResourceLocation id) { return memberships.get(id); }
    public @Nullable SettlementFoundingRecord founding(SettlementRef settlement) { return foundingRecords.get(settlement); }

    public Collection<OrganizationInstance> organizations() { return List.copyOf(organizations.values()); }
    public Collection<PolityInstance> polities() { return List.copyOf(polities.values()); }
    public Collection<AffiliationInstance> directAffiliations() { return List.copyOf(affiliations.values()); }
    public Collection<MembershipInstance> memberships() { return List.copyOf(memberships.values()); }
    public Collection<SettlementFoundingRecord> foundingRecords() { return List.copyOf(foundingRecords.values()); }

    /** Includes ordinary affiliations and the affiliation carried by every membership. */
    public List<AffiliationInstance> affiliations(UUID person) {
        List<AffiliationInstance> out = new ArrayList<>();
        for (AffiliationInstance value : affiliations.values()) if (value.person().equals(person)) out.add(value);
        for (MembershipInstance value : memberships.values()) {
            if (value.affiliation().person().equals(person)) out.add(value.affiliation());
        }
        return List.copyOf(out);
    }

    public List<MembershipInstance> memberships(UUID person) {
        List<MembershipInstance> out = new ArrayList<>();
        for (MembershipInstance value : memberships.values()) {
            if (value.affiliation().person().equals(person)) out.add(value);
        }
        return List.copyOf(out);
    }

    public List<MembershipInstance> memberships(PoliticalActorRef actor) {
        List<MembershipInstance> out = new ArrayList<>();
        for (MembershipInstance value : memberships.values()) {
            if (value.affiliation().actor().equals(actor)) out.add(value);
        }
        return List.copyOf(out);
    }

    public @Nullable MembershipInstance membership(UUID person, ResourceLocation organizationId) {
        PoliticalActorRef actor = new PoliticalActorRef(PoliticalActorRef.Kind.ORGANIZATION, organizationId);
        MembershipInstance newest = null;
        for (MembershipInstance value : memberships.values()) {
            if (value.affiliation().person().equals(person) && value.affiliation().actor().equals(actor)
                    && (newest == null || value.affiliation().startedAt() >= newest.affiliation().startedAt())) {
                newest = value;
            }
        }
        return newest;
    }

    public void putOrganization(OrganizationInstance value) {
        organizations.put(value.id(), value);
        setDirty();
    }

    public void putPolity(PolityInstance value) {
        if (value.governmentOrganization() != null && !organizations.containsKey(value.governmentOrganization())) {
            throw new IllegalArgumentException("Unknown government organization " + value.governmentOrganization());
        }
        polities.put(value.id(), value);
        setDirty();
    }

    public void putAffiliation(AffiliationInstance value) {
        requireActor(value.actor());
        if (memberships.containsKey(value.id())) throw new IllegalArgumentException("Affiliation id already belongs to a membership");
        affiliations.put(value.id(), value);
        setDirty();
    }

    public void putMembership(MembershipInstance value) {
        requireActor(value.affiliation().actor());
        if (affiliations.containsKey(value.id())) throw new IllegalArgumentException("Membership id already belongs to an affiliation");
        for (MembershipInstance existing : memberships.values()) {
            if (existing.id().equals(value.id())) continue;
            if (existing.affiliation().person().equals(value.affiliation().person())
                    && existing.affiliation().actor().equals(value.affiliation().actor())
                    && !terminal(existing.affiliation().status()) && !terminal(value.affiliation().status())) {
                throw new IllegalArgumentException("Person already has a current membership record for "
                        + value.affiliation().actor().id());
            }
        }
        memberships.put(value.id(), value);
        setDirty();
    }

    public void putFounding(SettlementFoundingRecord value) {
        if (polity(value.settlement()) == null) {
            throw new IllegalArgumentException("Founding record has no polity for " + value.settlement());
        }
        if (value.government() != null && !organizations.containsKey(value.government())) {
            throw new IllegalArgumentException("Founding record has unknown government " + value.government());
        }
        foundingRecords.put(value.settlement(), value);
        setDirty();
    }

    private void requireActor(PoliticalActorRef actor) {
        boolean exists = actor.kind() == PoliticalActorRef.Kind.ORGANIZATION
                ? organizations.containsKey(actor.id()) : polities.containsKey(actor.id());
        if (!exists) throw new IllegalArgumentException("Unknown political actor " + actor.kind().id() + ":" + actor.id());
    }

    private static boolean terminal(PoliticalStatus.Affiliation status) {
        return status == PoliticalStatus.Affiliation.FORMER || status == PoliticalStatus.Affiliation.REJECTED;
    }

    //? if >=1.21 {
    public static PoliticalSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static PoliticalSavedData load(CompoundTag tag) {
    *///?}
        PoliticalSavedData data = new PoliticalSavedData();
        load(tag, "organizations", PoliticalNbt::organization, value -> data.organizations.put(value.id(), value));
        load(tag, "polities", PoliticalNbt::polity, value -> data.polities.put(value.id(), value));
        load(tag, "affiliations", PoliticalNbt::affiliation, value -> data.affiliations.put(value.id(), value));
        load(tag, "memberships", PoliticalNbt::membership, value -> data.memberships.put(value.id(), value));
        load(tag, "founding_records", PoliticalNbt::founding,
                value -> data.foundingRecords.put(value.settlement(), value));
        ListTag external = tag.getList("external_governments", Tag.TAG_STRING);
        for (int i = 0; i < external.size(); i++) {
            ResourceLocation actor = ResourceLocation.tryParse(external.getString(i));
            if (actor != null) data.externalGovernments.add(actor);
        }
        CompoundTag names = tag.getCompound("faction_names");
        for (String key : names.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && data.polities.containsKey(id)) data.factionNames.put(id,
                    com.aetherianartificer.townstead.culture.FactionNaming.Name.load(names.getCompound(key)));
        }
        if (tag.getInt("schema_version") < SCHEMA_VERSION) data.setDirty();
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putInt("schema_version", SCHEMA_VERSION);
        CompoundTag names = new CompoundTag();
        factionNames.forEach((id, name) -> names.put(id.toString(), name.save()));
        tag.put("faction_names", names);
        ListTag external = new ListTag();
        externalGovernments.forEach(id -> external.add(net.minecraft.nbt.StringTag.valueOf(id.toString())));
        tag.put("external_governments", external);
        tag.put("organizations", save(organizations.values(), PoliticalNbt::save));
        tag.put("polities", save(polities.values(), PoliticalNbt::save));
        tag.put("affiliations", save(affiliations.values(), PoliticalNbt::save));
        tag.put("memberships", save(memberships.values(), PoliticalNbt::save));
        tag.put("founding_records", save(foundingRecords.values(), PoliticalNbt::save));
        return tag;
    }

    private static <T> void load(CompoundTag root, String key, Decoder<T> decoder, Sink<T> sink) {
        ListTag list = root.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            T decoded = decoder.read(list.getCompound(i));
            if (decoded != null) sink.accept(decoded);
        }
    }

    private static <T> ListTag save(Collection<T> values, Encoder<T> encoder) {
        ListTag list = new ListTag();
        for (T value : values) list.add(encoder.write(value));
        return list;
    }

    @FunctionalInterface private interface Decoder<T> { @Nullable T read(CompoundTag tag); }
    @FunctionalInterface private interface Encoder<T> { CompoundTag write(T value); }
    @FunctionalInterface private interface Sink<T> { void accept(T value); }
}
