package com.aetherianartificer.townstead.work.job;

import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent leases for environmental preparation performed by Jobs. A preparation is cleaned
 * only after every worker using it has released it, and abandoned holders expire independently.
 */
public final class ManagedRequirementLeases extends SavedData {
    public static final String FILE_ID = "townstead_work_requirements";
    private static final long LEASE_TICKS = 400L;

    public record Key(ResourceLocation dimension, long source, ResourceLocation job,
                      String requirement) {}

    private static final class Entry {
        private final Key key;
        private final long target;
        private final ResourceLocation managedBlock;
        private final Map<UUID, Long> holders = new LinkedHashMap<>();

        private Entry(Key key, long target, ResourceLocation managedBlock) {
            this.key = key;
            this.target = target;
            this.managedBlock = managedBlock;
        }
    }

    private final Map<Key, Entry> entries = new LinkedHashMap<>();

    public ManagedRequirementLeases() {}

    public static ManagedRequirementLeases get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ManagedRequirementLeases::new, ManagedRequirementLeases::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                ManagedRequirementLeases::load, ManagedRequirementLeases::new, FILE_ID);
        *///?}
    }

    /** Finds and shares a managed source already satisfying this Job requirement. */
    public @Nullable Key acquireExisting(ServerLevel level, ResourceLocation job, String requirement,
                                         List<BlockPos> sources, UUID holder) {
        long now = level.getServer().overworld().getGameTime();
        for (BlockPos source : sources) {
            Key key = new Key(level.dimension().location(), source.asLong(), job, requirement);
            Entry entry = entries.get(key);
            if (entry == null) continue;
            entry.holders.put(holder, now + LEASE_TICKS);
            setDirty();
            return key;
        }
        return null;
    }

    /** Records a preparation immediately after its start action has been verified. */
    public Key acquireNew(ServerLevel level, ResourceLocation job, String requirement,
                          BlockPos target, BlockPos source, UUID holder) {
        Key key = new Key(level.dimension().location(), source.asLong(), job, requirement);
        Entry entry = entries.computeIfAbsent(key, ignored -> new Entry(key, target.asLong(),
                BuiltInRegistries.BLOCK.getKey(level.getBlockState(source).getBlock())));
        entry.holders.put(holder, level.getServer().overworld().getGameTime() + LEASE_TICKS);
        setDirty();
        return key;
    }

    public void renew(MinecraftServer server, Key key, UUID holder) {
        Entry entry = entries.get(key);
        if (entry == null || !entry.holders.containsKey(holder)) return;
        entry.holders.put(holder, server.overworld().getGameTime() + LEASE_TICKS);
    }

    public void release(MinecraftServer server, Key key, UUID holder) {
        Entry entry = entries.get(key);
        if (entry == null) return;
        entry.holders.remove(holder);
        if (entry.holders.isEmpty()) {
            cleanup(server, entry);
            entries.remove(key);
        }
        setDirty();
    }

    /** Expires interrupted sessions; invoked by the ordinary server tick hook. */
    public static void tick(MinecraftServer server) {
        if ((server.getTickCount() % 20) != 0) return;
        ManagedRequirementLeases ledger = get(server);
        long now = server.overworld().getGameTime();
        List<Entry> abandoned = new ArrayList<>();
        for (Entry entry : ledger.entries.values()) {
            entry.holders.entrySet().removeIf(holder -> holder.getValue() <= now);
            if (entry.holders.isEmpty()) abandoned.add(entry);
        }
        if (abandoned.isEmpty()) return;
        for (Entry entry : abandoned) {
            ledger.cleanup(server, entry);
            ledger.entries.remove(entry.key);
        }
        ledger.setDirty();
    }

    private void cleanup(MinecraftServer server, Entry entry) {
        ServerLevel level = level(server, entry.key.dimension());
        WorkJobDef job = WorkJobs.byId(entry.key.job());
        if (level == null || job == null || job.target() == null) return;
        WorkJobDef.ManagedRequirement requirement = null;
        for (WorkJobDef.ManagedRequirement candidate : job.target().requirements()) {
            if (candidate.id().equals(entry.key.requirement())) {
                requirement = candidate;
                break;
            }
        }
        if (requirement == null || requirement.provision() == null) return;
        BlockPos source = BlockPos.of(entry.key.source());
        ResourceLocation current = BuiltInRegistries.BLOCK.getKey(level.getBlockState(source).getBlock());
        if (!entry.managedBlock.equals(current)
                || !requirement.provision().sourceManaged(level, source)) return;
        requirement.provision().stop().run(new BlockActionContext(level, source));
    }

    private static @Nullable ServerLevel level(MinecraftServer server, ResourceLocation dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    //? if >=1.21 {
    public static ManagedRequirementLeases load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static ManagedRequirementLeases load(CompoundTag tag) {
    *///?}
        ManagedRequirementLeases result = new ManagedRequirementLeases();
        ListTag list = tag.getList("leases", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag raw = list.getCompound(i);
            ResourceLocation dimension = ResourceLocation.tryParse(raw.getString("dimension"));
            ResourceLocation job = ResourceLocation.tryParse(raw.getString("job"));
            ResourceLocation block = ResourceLocation.tryParse(raw.getString("block"));
            String requirement = raw.getString("requirement");
            if (dimension == null || job == null || block == null || requirement.isEmpty()) continue;
            Key key = new Key(dimension, raw.getLong("source"), job, requirement);
            Entry entry = new Entry(key, raw.getLong("target"), block);
            ListTag holders = raw.getList("holders", Tag.TAG_COMPOUND);
            for (int h = 0; h < holders.size(); h++) {
                CompoundTag holder = holders.getCompound(h);
                if (holder.hasUUID("id")) entry.holders.put(holder.getUUID("id"), holder.getLong("expires"));
            }
            result.entries.put(key, entry);
        }
        return result;
    }

    //? if >=1.21 {
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override public CompoundTag save(CompoundTag tag) {
    *///?}
        ListTag list = new ListTag();
        for (Entry entry : entries.values()) {
            CompoundTag raw = new CompoundTag();
            raw.putString("dimension", entry.key.dimension().toString());
            raw.putLong("source", entry.key.source());
            raw.putLong("target", entry.target);
            raw.putString("job", entry.key.job().toString());
            raw.putString("requirement", entry.key.requirement());
            raw.putString("block", entry.managedBlock.toString());
            ListTag holders = new ListTag();
            for (Map.Entry<UUID, Long> holder : entry.holders.entrySet()) {
                CompoundTag owner = new CompoundTag();
                owner.putUUID("id", holder.getKey());
                owner.putLong("expires", holder.getValue());
                holders.add(owner);
            }
            raw.put("holders", holders);
            list.add(raw);
        }
        tag.put("leases", list);
        return tag;
    }
}
