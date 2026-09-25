package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class PoliticalNbt {
    private PoliticalNbt() {}

    static CompoundTag save(OrganizationInstance value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "id", value.id());
        id(tag, "kind", value.kind());
        id(tag, "membership_policy", value.membershipPolicy());
        tag.putString("name", value.name());
        tag.putString("short_name", value.shortName());
        tag.putInt("color", value.color());
        optionalId(tag, "emblem", value.emblem());
        tag.putLong("created_at", value.createdAt());
        id(tag, "provenance", value.provenance());
        tag.putString("status", value.status().id());
        if (value.home() != null) tag.put("home", save(value.home()));
        return tag;
    }

    static @Nullable OrganizationInstance organization(CompoundTag tag) {
        ResourceLocation id = id(tag, "id"), kind = id(tag, "kind");
        ResourceLocation policy = id(tag, "membership_policy"), provenance = id(tag, "provenance");
        if (id == null || kind == null || policy == null || provenance == null || tag.getString("name").isBlank()) {
            return null;
        }
        try {
            return new OrganizationInstance(id, kind, policy, tag.getString("name"), tag.getString("short_name"),
                    tag.getInt("color"), id(tag, "emblem"), tag.getLong("created_at"), provenance,
                    PoliticalStatus.Organization.parse(tag.getString("status")),
                    tag.contains("home", Tag.TAG_COMPOUND) ? settlement(tag.getCompound("home")) : null);
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(PolityInstance value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "id", value.id());
        tag.putString("name", value.name());
        tag.putInt("color", value.color());
        optionalId(tag, "emblem", value.emblem());
        tag.putLong("created_at", value.createdAt());
        id(tag, "provenance", value.provenance());
        tag.putString("status", value.status().id());
        optionalId(tag, "government", value.governmentOrganization());
        ListTag settlements = new ListTag();
        for (SettlementRef settlement : value.settlements()) settlements.add(save(settlement));
        tag.put("settlements", settlements);
        return tag;
    }

    static @Nullable PolityInstance polity(CompoundTag tag) {
        ResourceLocation id = id(tag, "id"), provenance = id(tag, "provenance");
        if (id == null || provenance == null || tag.getString("name").isBlank()) return null;
        List<SettlementRef> settlements = new ArrayList<>();
        ListTag list = tag.getList("settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            SettlementRef settlement = settlement(list.getCompound(i));
            if (settlement != null) settlements.add(settlement);
        }
        try {
            return new PolityInstance(id, tag.getString("name"), tag.getInt("color"), id(tag, "emblem"),
                    tag.getLong("created_at"), provenance,
                    PoliticalStatus.Polity.parse(tag.getString("status")), settlements, id(tag, "government"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(AffiliationInstance value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "id", value.id());
        tag.putUUID("person", value.person());
        tag.putString("actor_kind", value.actor().kind().id());
        id(tag, "actor_id", value.actor().id());
        id(tag, "kind", value.kind());
        tag.putString("status", value.status().id());
        tag.putLong("started_at", value.startedAt());
        if (value.endedAt() != AffiliationInstance.NOT_ENDED) tag.putLong("ended_at", value.endedAt());
        id(tag, "provenance", value.provenance());
        tag.putString("visibility", value.visibility().id());
        return tag;
    }

    static @Nullable AffiliationInstance affiliation(CompoundTag tag) {
        ResourceLocation id = id(tag, "id"), actorId = id(tag, "actor_id");
        ResourceLocation kind = id(tag, "kind"), provenance = id(tag, "provenance");
        PoliticalActorRef.Kind actorKind = PoliticalActorRef.Kind.parse(tag.getString("actor_kind"));
        if (id == null || actorId == null || kind == null || provenance == null || actorKind == null
                || !tag.hasUUID("person")) return null;
        try {
            return new AffiliationInstance(id, tag.getUUID("person"), new PoliticalActorRef(actorKind, actorId),
                    kind, PoliticalStatus.Affiliation.parse(tag.getString("status")), tag.getLong("started_at"),
                    tag.contains("ended_at", Tag.TAG_LONG) ? tag.getLong("ended_at") : AffiliationInstance.NOT_ENDED,
                    provenance, PoliticalStatus.Visibility.parse(tag.getString("visibility")));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(MembershipInstance value) {
        CompoundTag tag = save(value.affiliation());
        id(tag, "membership_policy", value.membershipPolicy());
        id(tag, "admission_procedure", value.admissionProcedure());
        id(tag, "departure_procedure", value.departureProcedure());
        tag.put("roles", ids(value.roles()));
        return tag;
    }

    static @Nullable MembershipInstance membership(CompoundTag tag) {
        AffiliationInstance affiliation = affiliation(tag);
        ResourceLocation policy = id(tag, "membership_policy");
        ResourceLocation admission = id(tag, "admission_procedure");
        ResourceLocation departure = id(tag, "departure_procedure");
        if (affiliation == null || policy == null || admission == null || departure == null) return null;
        try {
            return new MembershipInstance(affiliation, policy, admission, departure, ids(tag, "roles"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(SettlementFoundingRecord value) {
        CompoundTag tag = new CompoundTag();
        tag.put("settlement", save(value.settlement()));
        id(tag, "profile", value.profile());
        optionalId(tag, "culture", value.culture());
        optionalId(tag, "government", value.government());
        optionalId(tag, "founding_biome", value.foundingBiome());
        tag.putFloat("natural_weight", value.naturalWeight());
        tag.putLong("founded_at", value.foundedAt());
        return tag;
    }

    static @Nullable SettlementFoundingRecord founding(CompoundTag tag) {
        SettlementRef settlement = tag.contains("settlement", Tag.TAG_COMPOUND)
                ? settlement(tag.getCompound("settlement")) : null;
        ResourceLocation profile = id(tag, "profile"), government = id(tag, "government");
        if (settlement == null || profile == null) return null;
        try {
            return new SettlementFoundingRecord(settlement, profile, id(tag, "culture"), government,
                    id(tag, "founding_biome"), tag.getFloat("natural_weight"), tag.getLong("founded_at"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(SeatInstance value) {
        CompoundTag tag = new CompoundTag();
        tag.putString("actor_kind", value.actor().kind().id());
        id(tag, "actor_id", value.actor().id());
        tag.put("settlement", save(value.settlement()));
        tag.putLong("lectern", value.lectern().asLong());
        tag.putInt("building", value.buildingId());
        tag.putLong("designated_at", value.designatedAt());
        if (value.damaged()) {
            tag.putString("damage", value.damage());
            tag.putLong("damaged_at", value.damagedAt());
        }
        return tag;
    }

    static @Nullable SeatInstance seat(CompoundTag tag) {
        PoliticalActorRef.Kind actorKind = PoliticalActorRef.Kind.parse(tag.getString("actor_kind"));
        ResourceLocation actorId = id(tag, "actor_id");
        SettlementRef settlement = tag.contains("settlement", Tag.TAG_COMPOUND)
                ? settlement(tag.getCompound("settlement")) : null;
        if (actorKind == null || actorId == null || settlement == null
                || !tag.contains("lectern", Tag.TAG_LONG)) return null;
        try {
            return new SeatInstance(new PoliticalActorRef(actorKind, actorId), settlement,
                    net.minecraft.core.BlockPos.of(tag.getLong("lectern")), tag.getInt("building"),
                    tag.getLong("designated_at"), tag.getString("damage"), tag.getLong("damaged_at"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static CompoundTag save(SettlementRef value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "dimension", value.dimension());
        tag.putInt("village", value.villageId());
        return tag;
    }

    private static @Nullable SettlementRef settlement(CompoundTag tag) {
        ResourceLocation dimension = id(tag, "dimension");
        return dimension == null ? null : new SettlementRef(dimension, tag.getInt("village"));
    }

    private static ListTag ids(Set<ResourceLocation> values) {
        ListTag list = new ListTag();
        for (ResourceLocation value : values) {
            CompoundTag entry = new CompoundTag();
            id(entry, "id", value);
            list.add(entry);
        }
        return list;
    }

    private static Set<ResourceLocation> ids(CompoundTag tag, String key) {
        LinkedHashSet<ResourceLocation> out = new LinkedHashSet<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ResourceLocation id = id(list.getCompound(i), "id");
            if (id != null) out.add(id);
        }
        return Set.copyOf(out);
    }

    private static void id(CompoundTag tag, String key, ResourceLocation value) {
        tag.putString(key, value.toString());
    }

    private static void optionalId(CompoundTag tag, String key, @Nullable ResourceLocation value) {
        if (value != null) id(tag, key, value);
    }

    private static @Nullable ResourceLocation id(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) return null;
        return DataPackLang.parseId(tag.getString(key));
    }
}
