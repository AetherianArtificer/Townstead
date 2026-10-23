package com.aetherianartificer.townstead.politics.state;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

final class PoliticalNbt {
    private PoliticalNbt() {}

    static CompoundTag save(Faction value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "id", value.id());
        id(tag, "kind", value.kind());
        tag.putString("name", value.name());
        tag.putInt("color", value.color());
        optionalId(tag, "emblem", value.emblem());
        tag.putLong("created_at", value.createdAt());
        id(tag, "provenance", value.provenance());
        tag.putString("status", value.status().id());
        ListTag settlements = new ListTag();
        for (SettlementRef settlement : value.settlements()) settlements.add(save(settlement));
        tag.put("settlements", settlements);
        if (value.home() != null) tag.put("home", save(value.home()));
        return tag;
    }

    static @Nullable Faction faction(CompoundTag tag) {
        ResourceLocation id = id(tag, "id"), kind = id(tag, "kind"), provenance = id(tag, "provenance");
        if (id == null || kind == null || provenance == null || tag.getString("name").isBlank()) return null;
        try {
            return new Faction(id, kind, tag.getString("name"), tag.getInt("color"), id(tag, "emblem"),
                    tag.getLong("created_at"), provenance, Faction.Status.parse(tag.getString("status")),
                    settlements(tag, "settlements"),
                    tag.contains("home", Tag.TAG_COMPOUND) ? settlement(tag.getCompound("home")) : null);
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(BondInstance value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "id", value.id());
        id(tag, "kind", value.kind());
        ListTag sides = new ListTag();
        for (BondInstance.Side side : value.sides()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("role", side.role());
            entry.putString("party", side.party().encode());
            sides.add(entry);
        }
        tag.put("sides", sides);
        tag.putLong("started_at", value.startedAt());
        if (!value.active()) {
            tag.putLong("ended_at", value.endedAt());
            tag.putString("ended_by", value.endedBy());
        }
        id(tag, "provenance", value.provenance());
        return tag;
    }

    static @Nullable BondInstance bond(CompoundTag tag) {
        ResourceLocation id = id(tag, "id"), kind = id(tag, "kind"), provenance = id(tag, "provenance");
        if (id == null || kind == null || provenance == null) return null;
        List<BondInstance.Side> sides = new ArrayList<>();
        ListTag list = tag.getList("sides", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Party party = Party.decode(entry.getString("party"));
            if (party == null || entry.getString("role").isBlank()) return null;
            sides.add(new BondInstance.Side(entry.getString("role"), party));
        }
        try {
            return new BondInstance(id, kind, sides, tag.getLong("started_at"),
                    tag.contains("ended_at", Tag.TAG_LONG) ? tag.getLong("ended_at") : BondInstance.ONGOING,
                    provenance, tag.getString("ended_by"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(SettlementFoundingRecord value) {
        CompoundTag tag = new CompoundTag();
        tag.put("settlement", save(value.settlement()));
        id(tag, "profile", value.profile());
        optionalId(tag, "culture", value.culture());
        optionalId(tag, "faction_kind", value.factionKind());
        optionalId(tag, "founding_biome", value.foundingBiome());
        tag.putFloat("natural_weight", value.naturalWeight());
        tag.putLong("founded_at", value.foundedAt());
        return tag;
    }

    static @Nullable SettlementFoundingRecord founding(CompoundTag tag) {
        SettlementRef settlement = tag.contains("settlement", Tag.TAG_COMPOUND)
                ? settlement(tag.getCompound("settlement")) : null;
        ResourceLocation profile = id(tag, "profile");
        if (settlement == null || profile == null) return null;
        try {
            return new SettlementFoundingRecord(settlement, profile, id(tag, "culture"), id(tag, "faction_kind"),
                    id(tag, "founding_biome"), tag.getFloat("natural_weight"), tag.getLong("founded_at"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(SeatInstance value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "faction", value.faction());
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
        ResourceLocation faction = id(tag, "faction");
        SettlementRef settlement = tag.contains("settlement", Tag.TAG_COMPOUND)
                ? settlement(tag.getCompound("settlement")) : null;
        if (faction == null || settlement == null || !tag.contains("lectern", Tag.TAG_LONG)) return null;
        try {
            return new SeatInstance(faction, settlement, BlockPos.of(tag.getLong("lectern")), tag.getInt("building"),
                    tag.getLong("designated_at"), tag.getString("damage"), tag.getLong("damaged_at"));
        } catch (RuntimeException error) {
            return null;
        }
    }

    static CompoundTag save(SettlementRef value) {
        CompoundTag tag = new CompoundTag();
        id(tag, "dimension", value.dimension());
        tag.putInt("village", value.villageId());
        return tag;
    }

    static @Nullable SettlementRef settlement(CompoundTag tag) {
        ResourceLocation dimension = id(tag, "dimension");
        return dimension == null ? null : new SettlementRef(dimension, tag.getInt("village"));
    }

    static List<SettlementRef> settlements(CompoundTag tag, String key) {
        List<SettlementRef> out = new ArrayList<>();
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            SettlementRef settlement = settlement(list.getCompound(i));
            if (settlement != null) out.add(settlement);
        }
        return out;
    }

    static void id(CompoundTag tag, String key, ResourceLocation value) {
        tag.putString(key, value.toString());
    }

    static void optionalId(CompoundTag tag, String key, @Nullable ResourceLocation value) {
        if (value != null) id(tag, key, value);
    }

    static @Nullable ResourceLocation id(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_STRING)) return null;
        return DataPackLang.parseId(tag.getString(key));
    }
}
