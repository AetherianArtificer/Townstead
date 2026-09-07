package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.api.impl.v1.NeedScales;
import com.aetherianartificer.townstead.api.v1.model.NeedBand;
import com.aetherianartificer.townstead.api.v1.model.NeedLevel;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.api.v1.model.VillageNeedsSummary;
import com.aetherianartificer.townstead.api.v1.model.VillagerRecord;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleEmitter;
import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The last state Townstead saw for every villager it has ticked, kept whether or not the
 * villager is loaded. Needs only advance while an entity ticks, so this is a roll of last known
 * readings with their age, not a simulation. It backs the API's per-villager record and the
 * village-wide need summaries, which is what lets "is the village fed?" be answered for the
 * residents that are not in the world right now.
 */
public final class ResidentRegister extends SavedData {
    public static final String FILE_ID = "townstead_residents";

    /** Per-villager refresh stride while loaded. */
    private static final long REFRESH_STRIDE_TICKS = 600L;
    /** Entries unseen for this many days are dropped. */
    private static final long PRUNE_AFTER_DAYS = 120L;

    private final Map<UUID, Entry> entries = new HashMap<>();

    public ResidentRegister() {}

    public static ResidentRegister get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(ResidentRegister::new, ResidentRegister::load), FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(ResidentRegister::load, ResidentRegister::new, FILE_ID);
        *///?}
    }

    // ---- writes ----

    /** Called from the villager tick; refreshes on a per-villager stride. */
    public static void onVillagerTick(VillagerEntityMCA villager, long gameTime) {
        if (villager == null || villager.level() == null || villager.level().isClientSide()) return;
        if (Math.floorMod(gameTime + villager.getUUID().hashCode(), REFRESH_STRIDE_TICKS) != 0L) return;
        MinecraftServer server = villager.level().getServer();
        if (server != null) get(server).update(villager);
    }

    /** Refreshes one villager's entry from its live state. */
    public void update(VillagerEntityMCA villager) {
        if (villager == null || !(villager.level() instanceof ServerLevel level)) return;
        MinecraftServer server = level.getServer();
        TownsteadVillager state = TownsteadVillagers.get(villager);
        TownsteadVillager.Needs needs = state.needs();
        ResourceLocation professionKey = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        ResourceLocation career = professionKey == null ? null : ProfessionDefs.canonicalId(professionKey);
        int tier = 0;
        if (career != null) {
            try {
                tier = ProfessionProgress.getTier(state.professionMemory(), career);
            } catch (Throwable ignored) {
                tier = 0;
            }
        }
        int villageId = ChronicleEmitter.resolveVillageId(villager);
        Entry entry = new Entry(
                villager.getName().getString(),
                villageId == ChronicleEvent.VILLAGE_NONE ? null : level.dimension().location(),
                villageId,
                career == null ? "" : career.toString(),
                tier,
                needs.hunger(),
                needs.thirst(),
                needs.fatigue(),
                needs.bodyTempTenths(),
                needs.thermalTier(),
                needs.thermalCrisis(),
                needs.collapsed(),
                level.getGameTime(),
                TownsteadCalendar.worldDay(server),
                true);
        entries.put(villager.getUUID(), entry);
        setDirty();
    }

    public static void markDead(MinecraftServer server, UUID uuid) {
        if (server == null || uuid == null) return;
        ResidentRegister register = get(server);
        Entry entry = register.entries.get(uuid);
        if (entry == null) return;
        register.entries.put(uuid, entry.dead());
        register.setDirty();
    }

    /** Drops entries that have not been seen for a long time. Called on the lifecycle cadence. */
    public void prune(MinecraftServer server) {
        long today = TownsteadCalendar.worldDay(server);
        boolean changed = entries.entrySet().removeIf(e ->
                !e.getValue().alive || today - e.getValue().lastSeenWorldDay > PRUNE_AFTER_DAYS);
        if (changed) setDirty();
    }

    // ---- reads ----

    public Optional<VillagerRecord> record(MinecraftServer server, UUID uuid, boolean loaded) {
        Entry entry = entries.get(uuid);
        if (entry == null) return Optional.empty();
        return Optional.of(new VillagerRecord(uuid, entry.name, entry.village(), entry.professionId, entry.tier,
                entry.levels(), entry.collapsed, loaded, entry.lastSeenGameTime, entry.lastSeenWorldDay, entry.alive));
    }

    /** Every village this register knows residents of. */
    public List<VillageId> knownVillages() {
        List<VillageId> out = new ArrayList<>();
        for (Entry entry : entries.values()) {
            if (!entry.alive) continue;
            entry.village().ifPresent(id -> {
                if (!out.contains(id)) out.add(id);
            });
        }
        return out;
    }

    public Optional<VillageNeedsSummary> summary(MinecraftServer server, VillageId village) {
        if (server == null || village == null) return Optional.empty();
        ServerLevel level = null;
        for (ServerLevel candidate : server.getAllLevels()) {
            if (candidate.dimension().location().equals(village.dimension())) {
                level = candidate;
                break;
            }
        }
        int residentCount = 0;
        List<UUID> roll = List.of();
        if (level != null) {
            Optional<Village> mca = VillageManager.get(level).getOrEmpty(village.villageId());
            if (mca.isPresent()) {
                roll = mca.get().getResidentsUUIDs().toList();
                residentCount = roll.size();
            }
        }
        long today = TownsteadCalendar.worldDay(server);
        Map<String, List<NeedLevel>> samples = new LinkedHashMap<>();
        for (String id : NeedScales.IDS) samples.put(id, new ArrayList<>());
        int known = 0;
        int loaded = 0;
        int collapsed = 0;
        long oldest = today;
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            Entry entry = e.getValue();
            if (!entry.alive || entry.villageId != village.villageId()
                    || entry.dimension == null || !entry.dimension.equals(village.dimension())) continue;
            if (level != null) {
                Entity live = level.getEntity(e.getKey());
                if (live instanceof VillagerEntityMCA villager) {
                    update(villager);
                    entry = entries.get(e.getKey());
                    loaded++;
                }
            }
            known++;
            if (entry.collapsed) collapsed++;
            oldest = Math.min(oldest, entry.lastSeenWorldDay);
            for (Map.Entry<String, NeedLevel> levelEntry : entry.levels().entrySet()) {
                if (!levelEntry.getValue().enabled()) continue;
                samples.computeIfAbsent(levelEntry.getKey(), k -> new ArrayList<>()).add(levelEntry.getValue());
            }
        }
        if (known == 0 && residentCount == 0) return Optional.empty();
        Map<String, VillageNeedsSummary.NeedStat> byNeed = new LinkedHashMap<>();
        for (Map.Entry<String, List<NeedLevel>> sample : samples.entrySet()) {
            List<NeedLevel> levels = sample.getValue();
            if (levels.isEmpty()) continue;
            double sum = 0;
            double fractionSum = 0;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            int crisis = 0;
            for (NeedLevel needLevel : levels) {
                sum += needLevel.value();
                fractionSum += needLevel.fraction();
                min = Math.min(min, needLevel.value());
                max = Math.max(max, needLevel.value());
                if (needLevel.crisis()) crisis++;
            }
            byNeed.put(sample.getKey(), new VillageNeedsSummary.NeedStat(sample.getKey(), levels.size(),
                    sum / levels.size(), min, max, crisis, NeedBand.of(fractionSum / levels.size())));
        }
        return Optional.of(new VillageNeedsSummary(village, residentCount, known, loaded, byNeed, collapsed,
                known == 0 ? today : oldest, today));
    }

    // ---- persistence ----

    //? if >=1.21 {
    public static ResidentRegister load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ResidentRegister load(CompoundTag tag) {
    *///?}
        ResidentRegister data = new ResidentRegister();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (!t.hasUUID("uuid")) continue;
            data.entries.put(t.getUUID("uuid"), Entry.load(t));
        }
        return data;
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Entry> e : entries.entrySet()) {
            CompoundTag t = e.getValue().save();
            t.putUUID("uuid", e.getKey());
            list.add(t);
        }
        tag.put("entries", list);
        return tag;
    }

    public int size() {
        return entries.size();
    }

    private record Entry(String name, @Nullable ResourceLocation dimension, int villageId, String professionId,
                         int tier, int hunger, int thirst, int fatigue, int bodyTenths, int thermalTier,
                         boolean thermalCrisis, boolean collapsed, long lastSeenGameTime, long lastSeenWorldDay,
                         boolean alive) {

        Optional<VillageId> village() {
            return dimension == null || villageId == ChronicleEvent.VILLAGE_NONE
                    ? Optional.empty() : Optional.of(new VillageId(dimension, villageId));
        }

        Map<String, NeedLevel> levels() {
            return NeedScales.levels(hunger, thirst, fatigue, bodyTenths, thermalTier, thermalCrisis);
        }

        Entry dead() {
            return new Entry(name, dimension, villageId, professionId, tier, hunger, thirst, fatigue, bodyTenths,
                    thermalTier, thermalCrisis, collapsed, lastSeenGameTime, lastSeenWorldDay, false);
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("name", name);
            if (dimension != null) t.putString("dim", dimension.toString());
            t.putInt("village", villageId);
            t.putString("profession", professionId);
            t.putInt("tier", tier);
            t.putInt("hunger", hunger);
            t.putInt("thirst", thirst);
            t.putInt("fatigue", fatigue);
            t.putInt("bodyTenths", bodyTenths);
            t.putInt("thermalTier", thermalTier);
            t.putBoolean("thermalCrisis", thermalCrisis);
            t.putBoolean("collapsed", collapsed);
            t.putLong("seenTick", lastSeenGameTime);
            t.putLong("seenDay", lastSeenWorldDay);
            t.putBoolean("alive", alive);
            return t;
        }

        static Entry load(CompoundTag t) {
            ResourceLocation dim = t.contains("dim") ? ResourceLocation.tryParse(t.getString("dim")) : null;
            return new Entry(t.getString("name"), dim, t.getInt("village"), t.getString("profession"), t.getInt("tier"),
                    t.getInt("hunger"), t.getInt("thirst"), t.getInt("fatigue"),
                    t.contains("bodyTenths") ? t.getInt("bodyTenths") : Integer.MIN_VALUE, t.getInt("thermalTier"),
                    t.getBoolean("thermalCrisis"), t.getBoolean("collapsed"), t.getLong("seenTick"), t.getLong("seenDay"),
                    !t.contains("alive") || t.getBoolean("alive"));
        }
    }
}
