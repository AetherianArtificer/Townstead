package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;

import javax.annotation.Nullable;

/**
 * Keeps the ordinary merchant ledger alongside Townstead's per-profession
 * level and XP memory. Changing current work must not erase the offers a
 * villager had already established in an earlier profession.
 */
public final class ProfessionTradeLedger {
    private static final int MAX_MERCHANT_LEVEL = 5;

    private ProfessionTradeLedger() {}

    /** Save the profession the villager is leaving, including mutable offer state. */
    public static void rememberCurrent(VillagerEntityMCA villager, VillagerProfession profession) {
        String key = professionKey(profession);
        if (key == null || "minecraft:none".equals(key)) return;
        TownsteadVillager.ProfessionMemory memory = TownsteadVillagers.get(villager).professionMemory();
        memory.putProgress(key, villager.getVillagerData().getLevel(), villager.getVillagerXp());
        MerchantOffers current = villager.getOffers();
        // Empty is the signature left by the old assignment bug. Do not make
        // that corruption durable; a later return can then regenerate it.
        if (current.isEmpty()) return;
        CompoundTag encoded = encode(villager, current);
        if (encoded != null) memory.putTradeOffers(key, encoded);
    }

    /** Repair an already-empty ledger without changing profession or POI. */
    public static void ensureCurrent(VillagerEntityMCA villager) {
        if (!villager.getOffers().isEmpty()) return;
        VillagerProfession profession = villager.getVillagerData().getProfession();
        String key = professionKey(profession);
        if (key == null || "minecraft:none".equals(key)) return;
        TownsteadVillager.ProfessionMemory memory = TownsteadVillagers.get(villager).professionMemory();
        memory.putProgress(key, villager.getVillagerData().getLevel(), villager.getVillagerXp());
        activate(villager, profession);
    }

    /**
     * Activate a profession after its POI has been claimed. A returning
     * profession receives its saved offers exactly; a first-time profession
     * rolls offers for every previously saved merchant level.
     */
    public static void activate(VillagerEntityMCA villager, VillagerProfession profession) {
        String key = professionKey(profession);
        TownsteadVillager.ProfessionMemory memory = TownsteadVillagers.get(villager).professionMemory();
        TownsteadVillager.ProfessionMemory.Progress saved = key == null ? null : memory.progress(key);
        int level = saved == null ? 1 : Math.min(MAX_MERCHANT_LEVEL, Math.max(1, saved.level()));
        int xp = saved == null ? 0 : Math.max(0, saved.xp());

        villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(1));
        villager.setVillagerXp(0);

        CompoundTag stored = key == null ? null : memory.tradeOffers(key);
        MerchantOffers restored = stored == null ? null : decode(villager, stored);
        if (restored != null && !restored.isEmpty()) {
            villager.setOffers(restored);
            villager.setVillagerData(villager.getVillagerData().setLevel(level));
        } else {
            // setOffers(null), rather than clear(), lets vanilla create the
            // new profession's novice catalogue. customLevelUp then adds each
            // higher tier instead of merely changing the displayed number.
            villager.setOffers(null);
            villager.getOffers();
            while (villager.getVillagerData().getLevel() < level) {
                villager.customLevelUp();
            }
        }
        villager.setVillagerXp(xp);

        if (key != null) {
            memory.putProgress(key, level, xp);
            memory.setLastProfession(key);
        }
        TownsteadVillagers.flush(villager);
    }

    @Nullable
    private static CompoundTag encode(VillagerEntityMCA villager, MerchantOffers offers) {
        //? if >=1.21 {
        Tag encoded = MerchantOffers.CODEC.encodeStart(
                        villager.registryAccess().createSerializationContext(NbtOps.INSTANCE), offers)
                .resultOrPartial(message -> Townstead.LOGGER.warn(
                        "Could not save profession trades for villager {}: {}", villager.getUUID(), message))
                .orElse(null);
        return encoded instanceof CompoundTag tag ? tag : null;
        //?} else {
        /*return offers.createTag();
        *///?}
    }

    @Nullable
    private static MerchantOffers decode(VillagerEntityMCA villager, CompoundTag stored) {
        //? if >=1.21 {
        return MerchantOffers.CODEC.parse(
                        villager.registryAccess().createSerializationContext(NbtOps.INSTANCE), stored)
                .resultOrPartial(message -> Townstead.LOGGER.warn(
                        "Could not restore profession trades for villager {}: {}", villager.getUUID(), message))
                .orElse(null);
        //?} else {
        /*return new MerchantOffers(stored);
        *///?}
    }

    @Nullable
    private static String professionKey(VillagerProfession profession) {
        if (profession == null) return null;
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return id == null ? null : id.toString();
    }
}
