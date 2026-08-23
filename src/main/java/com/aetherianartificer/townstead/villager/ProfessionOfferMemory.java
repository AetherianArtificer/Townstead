package com.aetherianartificer.townstead.villager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/** Durable, defensive storage for one villager's offer ledger per profession. */
final class ProfessionOfferMemory {
    private final Map<String, CompoundTag> byProfession = new HashMap<>();

    CompoundTag get(String professionId) {
        CompoundTag offers = byProfession.get(professionId);
        return offers == null ? null : offers.copy();
    }

    void put(String professionId, CompoundTag offers) {
        if (professionId == null || professionId.isBlank() || offers == null) return;
        byProfession.put(professionId, offers.copy());
    }

    CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, CompoundTag> entry : byProfession.entrySet()) {
            tag.put(entry.getKey(), entry.getValue().copy());
        }
        return tag;
    }

    void load(CompoundTag tag) {
        byProfession.clear();
        for (String key : tag.getAllKeys()) {
            if (tag.contains(key, Tag.TAG_COMPOUND)) {
                byProfession.put(key, tag.getCompound(key).copy());
            }
        }
    }
}
