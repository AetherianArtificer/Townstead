package com.aetherianartificer.townstead.farming.cellplan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Two-layer cell plan: soil type and seed assignment per XZ column.
 * Keys are packed XZ offsets relative to the Field Post position.
 * Y is not stored — it is resolved at blueprint-build time via heightmap scan.
 * Immutable after construction; use {@link Builder} to create.
 */
public final class CellPlan {
    public static final CellPlan EMPTY = new CellPlan(Map.of(), Map.of());

    private static final String NBT_KEY = "cellPlanV2";
    private static final String NBT_SOIL = "soil";
    private static final String NBT_SEED = "seed";

    private final Map<Integer, SoilType> soilPlan;
    private final Map<Integer, String> seedPlan;

    private CellPlan(Map<Integer, SoilType> soilPlan, Map<Integer, String> seedPlan) {
        this.soilPlan = Collections.unmodifiableMap(soilPlan);
        this.seedPlan = Collections.unmodifiableMap(seedPlan);
    }

    // ── Key packing: high 16 bits = xOffset (signed), low 16 bits = zOffset (signed) ──

    public static int packXZ(int xOffset, int zOffset) {
        return ((xOffset & 0xFFFF) << 16) | (zOffset & 0xFFFF);
    }

    public static int unpackX(int key) { return (short) (key >>> 16); }
    public static int unpackZ(int key) { return (short) (key & 0xFFFF); }

    // ── Accessors ──

    public Map<Integer, SoilType> soilPlan() { return soilPlan; }
    public Map<Integer, String> seedPlan() { return seedPlan; }
    public boolean isEmpty() { return soilPlan.isEmpty() && seedPlan.isEmpty(); }

    public SoilType soilAt(int xOffset, int zOffset) {
        return soilPlan.get(packXZ(xOffset, zOffset));
    }

    public String seedAt(int xOffset, int zOffset) {
        return seedPlan.get(packXZ(xOffset, zOffset));
    }

    public boolean isProtected(int xOffset, int zOffset) {
        int key = packXZ(xOffset, zOffset);
        if (SoilType.PROTECTED == soilPlan.get(key)) return true;
        return SeedAssignment.PROTECTED.equals(seedPlan.get(key));
    }

    public Set<Integer> protectedKeys() {
        Set<Integer> keys = new HashSet<>();
        soilPlan.forEach((k, v) -> { if (v == SoilType.PROTECTED) keys.add(k); });
        seedPlan.forEach((k, v) -> { if (SeedAssignment.PROTECTED.equals(v)) keys.add(k); });
        return Collections.unmodifiableSet(keys);
    }

    public long signature() {
        return soilPlan.hashCode() * 31L + seedPlan.hashCode();
    }

    // ── NBT ──

    public static void save(CompoundTag parent, CellPlan plan) {
        CompoundTag root = new CompoundTag();
        ListTag soilList = new ListTag();
        plan.soilPlan.forEach((key, type) -> {
            CompoundTag e = new CompoundTag();
            e.putInt("xz", key);
            e.putString("type", type.name());
            soilList.add(e);
        });
        ListTag seedList = new ListTag();
        plan.seedPlan.forEach((key, val) -> {
            CompoundTag e = new CompoundTag();
            e.putInt("xz", key);
            e.putString("val", val);
            seedList.add(e);
        });
        root.put(NBT_SOIL, soilList);
        root.put(NBT_SEED, seedList);
        parent.put(NBT_KEY, root);
    }

    public static CellPlan load(CompoundTag parent) {
        if (!parent.contains(NBT_KEY)) return EMPTY;
        CompoundTag root = parent.getCompound(NBT_KEY);
        Builder b = builder();
        ListTag soilList = root.getList(NBT_SOIL, Tag.TAG_COMPOUND);
        for (int i = 0; i < soilList.size(); i++) {
            CompoundTag e = soilList.getCompound(i);
            SoilType t = SoilType.fromName(e.getString("type"));
            if (t != null) b.rawSoil(e.getInt("xz"), t);
        }
        ListTag seedList = root.getList(NBT_SEED, Tag.TAG_COMPOUND);
        for (int i = 0; i < seedList.size(); i++) {
            CompoundTag e = seedList.getCompound(i);
            b.rawSeed(e.getInt("xz"), e.getString("val"));
        }
        return b.build();
    }

    // ── Network ──

    public void write(FriendlyByteBuf buf) {
        SoilType[] soilValues = SoilType.values();
        buf.writeVarInt(soilPlan.size());
        soilPlan.forEach((k, v) -> { buf.writeInt(k); buf.writeByte(v.ordinal()); });
        buf.writeVarInt(seedPlan.size());
        seedPlan.forEach((k, v) -> { buf.writeInt(k); buf.writeUtf(v); });
    }

    public static CellPlan read(FriendlyByteBuf buf) {
        SoilType[] soilValues = SoilType.values();
        int soilCount = buf.readVarInt();
        Builder b = builder();
        for (int i = 0; i < soilCount; i++) {
            int k = buf.readInt();
            byte ord = buf.readByte();
            if (ord >= 0 && ord < soilValues.length) b.rawSoil(k, soilValues[ord]);
        }
        int seedCount = buf.readVarInt();
        for (int i = 0; i < seedCount; i++) {
            b.rawSeed(buf.readInt(), buf.readUtf());
        }
        return b.build();
    }

    // ── Builder ──

    public Builder toBuilder() {
        return new Builder(new HashMap<>(soilPlan), new HashMap<>(seedPlan));
    }

    public static Builder builder() {
        return new Builder(new HashMap<>(), new HashMap<>());
    }

    public static final class Builder {
        private final Map<Integer, SoilType> soilPlan;
        private final Map<Integer, String> seedPlan;

        private Builder(Map<Integer, SoilType> s, Map<Integer, String> p) {
            this.soilPlan = s;
            this.seedPlan = p;
        }

        public Builder soil(int xOffset, int zOffset, SoilType type) {
            int key = packXZ(xOffset, zOffset);
            if (type == null) soilPlan.remove(key); else soilPlan.put(key, type);
            return this;
        }

        public Builder seed(int xOffset, int zOffset, String assignment) {
            int key = packXZ(xOffset, zOffset);
            if (assignment == null) seedPlan.remove(key); else seedPlan.put(key, assignment);
            return this;
        }

        public Builder protect(int xOffset, int zOffset) {
            int key = packXZ(xOffset, zOffset);
            soilPlan.put(key, SoilType.PROTECTED);
            seedPlan.put(key, SeedAssignment.PROTECTED);
            return this;
        }

        // Raw key methods for NBT/network loading and screen construction (key already packed)
        public Builder rawSoil(int packedKey, SoilType t) { soilPlan.put(packedKey, t); return this; }
        public Builder rawSeed(int packedKey, String v) { seedPlan.put(packedKey, v); return this; }
        public Builder removeSoil(int packedKey) { soilPlan.remove(packedKey); return this; }
        public Builder removeSeed(int packedKey) { seedPlan.remove(packedKey); return this; }

        public CellPlan build() {
            return new CellPlan(new HashMap<>(soilPlan), new HashMap<>(seedPlan));
        }
    }
}
