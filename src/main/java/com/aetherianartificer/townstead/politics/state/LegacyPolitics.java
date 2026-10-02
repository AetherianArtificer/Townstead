package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.culture.FactionNaming;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Converts a save from before factions (schema 5 and older). Each polity and its government merge
 * into one faction that keeps the polity's id; other organizations become factions of their own;
 * affiliations and memberships become citizenship and office bonds. {@link #convert} holds the
 * rules; the rest only reads the old NBT.
 */
public final class LegacyPolitics {
    static final ResourceLocation FREE_SETTLEMENT = id("townstead:free_settlement");
    private static final ResourceLocation RESIDENCE_KIND = id("townstead:residence");
    private static final String MEMBER_ROLE = "member";

    private LegacyPolitics() {}

    public record Organization(ResourceLocation id, ResourceLocation kind, String name, int color,
                               @Nullable ResourceLocation emblem, long createdAt, ResourceLocation provenance,
                               String status, @Nullable SettlementRef home) {}

    public record Polity(ResourceLocation id, String name, int color, @Nullable ResourceLocation emblem, long createdAt,
                         ResourceLocation provenance, String status, List<SettlementRef> settlements,
                         @Nullable ResourceLocation government) {}

    /** An affiliation, or a membership when {@code roles} is not empty. {@code actor} is a polity or organization id. */
    public record Relation(ResourceLocation id, UUID person, ResourceLocation actor, ResourceLocation kind, String status,
                           long startedAt, long endedAt, ResourceLocation provenance, Set<ResourceLocation> roles) {}

    public record Seat(String actorKind, ResourceLocation actor, SettlementRef settlement, BlockPos lectern, int building,
                       long designatedAt, String damage, long damagedAt) {}

    public record Founding(SettlementRef settlement, ResourceLocation profile, @Nullable ResourceLocation culture,
                           @Nullable ResourceLocation government, @Nullable ResourceLocation biome, float weight, long foundedAt) {}

    public record Input(List<Organization> organizations, List<Polity> polities, List<Relation> affiliations,
                        List<Relation> memberships, List<Seat> seats, List<Founding> foundings,
                        Map<ResourceLocation, Double> legitimacy, Set<ResourceLocation> external) {}

    static void migrate(CompoundTag tag, PoliticalSavedData data) {
        convert(read(tag), data);
        CompoundTag names = tag.getCompound("faction_names");
        for (String key : names.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null && data.hasFaction(id)) data.restoreName(id, FactionNaming.Name.load(names.getCompound(key)));
        }
        Townstead.LOGGER.info("Converted political records to factions: {} factions, {} bonds",
                data.factions().size(), data.bonds().size());
    }

    /** Writes the converted records straight into {@code data}, without events. */
    public static void convert(Input input, PoliticalSavedData data) {
        Map<ResourceLocation, Organization> organizations = new LinkedHashMap<>();
        for (Organization organization : input.organizations()) organizations.put(organization.id(), organization);
        Map<ResourceLocation, ResourceLocation> owner = new HashMap<>();
        for (Polity polity : input.polities()) {
            Organization government = polity.government() == null ? null : organizations.get(polity.government());
            try {
                data.restore(new Faction(polity.id(), government == null ? FREE_SETTLEMENT : government.kind(), polity.name(),
                        polity.color(), polity.emblem(), polity.createdAt(), polity.provenance(),
                        Faction.Status.parse(polity.status()), polity.settlements(), null));
            } catch (RuntimeException error) {
                continue;
            }
            owner.put(polity.id(), polity.id());
            if (government != null) owner.put(government.id(), polity.id());
        }
        for (Organization organization : organizations.values()) {
            if (owner.containsKey(organization.id())) continue;
            try {
                data.restore(new Faction(organization.id(), organization.kind(), organization.name(), organization.color(),
                        organization.emblem(), organization.createdAt(), organization.provenance(),
                        Faction.Status.parse(organization.status()), List.of(), organization.home()));
            } catch (RuntimeException error) {
                continue;
            }
            owner.put(organization.id(), organization.id());
        }
        owner.forEach(data::restoreLegacy);

        Set<String> citizens = new HashSet<>();
        for (Relation relation : input.affiliations()) {
            ResourceLocation faction = owner.get(relation.actor());
            if (faction == null || relation.status().equals("pending")) continue;
            ResourceLocation provenance = RESIDENCE_KIND.equals(relation.kind()) ? FactionBonds.RESIDENCE : relation.provenance();
            citizenship(data, citizens, relation, faction, relation.id(), provenance);
        }
        for (Relation relation : input.memberships()) {
            ResourceLocation faction = owner.get(relation.actor());
            if (faction == null || relation.status().equals("pending")) continue;
            boolean member = relation.roles().stream().anyMatch(role -> role.getPath().equals(MEMBER_ROLE));
            citizenship(data, citizens, relation, faction, member ? relation.id() : child(relation.id(), "citizenship"), relation.provenance());
            for (ResourceLocation role : relation.roles()) {
                if (role.getPath().equals(MEMBER_ROLE)) continue;
                long ended = ended(relation);
                data.restore(new BondInstance(child(relation.id(), role.getPath()), role,
                        FactionBonds.sides(role, faction, relation.person()), relation.startedAt(), ended, relation.provenance(),
                        ended == BondInstance.ONGOING ? "" : "migrated"));
            }
        }

        for (Founding founding : input.foundings()) {
            Organization government = founding.government() == null ? null : organizations.get(founding.government());
            data.restore(new SettlementFoundingRecord(founding.settlement(), founding.profile(), founding.culture(),
                    government == null ? null : government.kind(), founding.biome(), founding.weight(), founding.foundedAt()));
        }
        // Polity Seats are written last so they win over a government's own Headquarters.
        for (String wanted : List.of("organization", "polity")) {
            for (Seat seat : input.seats()) {
                ResourceLocation faction = owner.get(seat.actor());
                if (!seat.actorKind().equals(wanted) || faction == null) continue;
                data.restore(new SeatInstance(faction, seat.settlement(), seat.lectern(), seat.building(), seat.designatedAt(),
                        seat.damage(), seat.damagedAt()));
            }
        }
        input.legitimacy().forEach((id, value) -> {
            ResourceLocation faction = owner.get(id);
            if (faction != null) data.restoreLegitimacy(faction, value);
        });
        for (ResourceLocation id : input.external()) if (owner.containsKey(id)) data.restoreExternal(owner.get(id));
    }

    private static long ended(Relation relation) {
        boolean ended = relation.status().equals("former") || relation.status().equals("rejected");
        return !ended ? BondInstance.ONGOING : relation.endedAt() == BondInstance.ONGOING ? relation.startedAt() : relation.endedAt();
    }

    /** One active citizenship per person and faction, however many old records said so. */
    private static void citizenship(PoliticalSavedData data, Set<String> citizens, Relation relation, ResourceLocation faction,
                                    ResourceLocation id, ResourceLocation provenance) {
        long ended = ended(relation);
        boolean active = ended == BondInstance.ONGOING;
        if (active && !citizens.add(relation.person() + "|" + faction)) return;
        data.restore(new BondInstance(id, FactionBonds.CITIZENSHIP, FactionBonds.sides(FactionBonds.CITIZENSHIP, faction, relation.person()),
                relation.startedAt(), ended, provenance, active ? "" : "migrated"));
    }

    private static Input read(CompoundTag tag) {
        List<Organization> organizations = new ArrayList<>();
        for (CompoundTag entry : list(tag, "organizations")) {
            ResourceLocation id = PoliticalNbt.id(entry, "id"), kind = PoliticalNbt.id(entry, "kind");
            ResourceLocation provenance = PoliticalNbt.id(entry, "provenance");
            if (id == null || kind == null || provenance == null || entry.getString("name").isBlank()) continue;
            organizations.add(new Organization(id, kind, entry.getString("name"), entry.getInt("color"), PoliticalNbt.id(entry, "emblem"),
                    entry.getLong("created_at"), provenance, entry.getString("status"),
                    entry.contains("home", Tag.TAG_COMPOUND) ? PoliticalNbt.settlement(entry.getCompound("home")) : null));
        }
        List<Polity> polities = new ArrayList<>();
        for (CompoundTag entry : list(tag, "polities")) {
            ResourceLocation id = PoliticalNbt.id(entry, "id"), provenance = PoliticalNbt.id(entry, "provenance");
            if (id == null || provenance == null || entry.getString("name").isBlank()) continue;
            polities.add(new Polity(id, entry.getString("name"), entry.getInt("color"), PoliticalNbt.id(entry, "emblem"),
                    entry.getLong("created_at"), provenance, entry.getString("status"), PoliticalNbt.settlements(entry, "settlements"),
                    PoliticalNbt.id(entry, "government")));
        }
        List<Relation> affiliations = relations(tag, "affiliations");
        List<Relation> memberships = relations(tag, "memberships");
        List<Seat> seats = new ArrayList<>();
        for (CompoundTag entry : list(tag, "seats")) {
            ResourceLocation actor = PoliticalNbt.id(entry, "actor_id");
            SettlementRef settlement = entry.contains("settlement", Tag.TAG_COMPOUND) ? PoliticalNbt.settlement(entry.getCompound("settlement")) : null;
            if (actor == null || settlement == null || !entry.contains("lectern", Tag.TAG_LONG)) continue;
            seats.add(new Seat(entry.getString("actor_kind"), actor, settlement, BlockPos.of(entry.getLong("lectern")),
                    entry.getInt("building"), entry.getLong("designated_at"), entry.getString("damage"), entry.getLong("damaged_at")));
        }
        List<Founding> foundings = new ArrayList<>();
        for (CompoundTag entry : list(tag, "founding_records")) {
            SettlementRef settlement = entry.contains("settlement", Tag.TAG_COMPOUND) ? PoliticalNbt.settlement(entry.getCompound("settlement")) : null;
            ResourceLocation profile = PoliticalNbt.id(entry, "profile");
            if (settlement == null || profile == null) continue;
            foundings.add(new Founding(settlement, profile, PoliticalNbt.id(entry, "culture"), PoliticalNbt.id(entry, "government"),
                    PoliticalNbt.id(entry, "founding_biome"), entry.getFloat("natural_weight"), entry.getLong("founded_at")));
        }
        Map<ResourceLocation, Double> legitimacy = new LinkedHashMap<>();
        CompoundTag stored = tag.getCompound("legitimacy");
        for (String key : stored.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(key);
            if (id != null) legitimacy.put(id, stored.getDouble(key));
        }
        Set<ResourceLocation> external = new LinkedHashSet<>();
        ListTag list = tag.getList("external_governments", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = ResourceLocation.tryParse(list.getString(i));
            if (id != null) external.add(id);
        }
        return new Input(organizations, polities, affiliations, memberships, seats, foundings, legitimacy, external);
    }

    private static List<Relation> relations(CompoundTag tag, String key) {
        List<Relation> out = new ArrayList<>();
        for (CompoundTag entry : list(tag, key)) {
            ResourceLocation id = PoliticalNbt.id(entry, "id"), actor = PoliticalNbt.id(entry, "actor_id");
            ResourceLocation kind = PoliticalNbt.id(entry, "kind"), provenance = PoliticalNbt.id(entry, "provenance");
            if (id == null || actor == null || kind == null || provenance == null || !entry.hasUUID("person")) continue;
            Set<ResourceLocation> roles = new LinkedHashSet<>();
            for (CompoundTag role : list(entry, "roles")) {
                ResourceLocation value = PoliticalNbt.id(role, "id");
                if (value != null) roles.add(value);
            }
            out.add(new Relation(id, entry.getUUID("person"), actor, kind, entry.getString("status"), entry.getLong("started_at"),
                    entry.contains("ended_at", Tag.TAG_LONG) ? entry.getLong("ended_at") : BondInstance.ONGOING, provenance, roles));
        }
        return out;
    }

    private static ResourceLocation child(ResourceLocation id, String suffix) {
        return id(id.getNamespace() + ":" + id.getPath() + "/" + suffix);
    }

    private static List<CompoundTag> list(CompoundTag tag, String key) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        List<CompoundTag> out = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getCompound(i));
        return out;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) throw new IllegalStateException(value);
        return id;
    }
}
