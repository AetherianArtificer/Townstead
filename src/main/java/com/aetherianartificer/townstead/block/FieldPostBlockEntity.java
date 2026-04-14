package com.aetherianartificer.townstead.block;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.farming.cellplan.CellPlan;
import com.aetherianartificer.townstead.farming.cellplan.FieldPostConfig;
import net.minecraft.core.BlockPos;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FieldPostBlockEntity extends BlockEntity implements MenuProvider {

    public static final String DEFAULT_PATTERN = "auto";
    public static final int DEFAULT_TIER_CAP = 5;
    public static final int DEFAULT_RADIUS = 16;
    public static final int DEFAULT_PRIORITY = 5;
    public static final int DEFAULT_MAX_WATER_CELLS = 8;
    public static final int DEFAULT_GROOM_RADIUS = 4;

    private String patternId = DEFAULT_PATTERN;
    private int tierCap = DEFAULT_TIER_CAP;
    private int radius = DEFAULT_RADIUS;
    private int priority = DEFAULT_PRIORITY;
    private boolean autoSeedMode = true;
    private final List<String> seedFilter = new ArrayList<>();
    private boolean waterEnabled = true;
    private int maxWaterCells = DEFAULT_MAX_WATER_CELLS;
    private boolean groomEnabled = true;
    private int groomRadius = DEFAULT_GROOM_RADIUS;
    private boolean rotationEnabled = false;
    private final List<String> rotationPatterns = new ArrayList<>();
    private int rotationIndex = 0;
    private CellPlan cellPlan = CellPlan.EMPTY;
    @Nullable private UUID ownerUuid;
    private String ownerName = "";

    public FieldPostBlockEntity(BlockPos pos, BlockState state) {
        super(Townstead.FIELD_POST_BE.get(), pos, state);
    }

    // ── Getters ──

    public String getPatternId() { return patternId; }
    public int getTierCap() { return tierCap; }
    public int getRadius() { return radius; }
    public int getPriority() { return priority; }
    public boolean isAutoSeedMode() { return autoSeedMode; }
    public List<String> getSeedFilter() { return List.copyOf(seedFilter); }
    public boolean isWaterEnabled() { return waterEnabled; }
    public int getMaxWaterCells() { return maxWaterCells; }
    public boolean isGroomEnabled() { return groomEnabled; }
    public int getGroomRadius() { return groomRadius; }
    public boolean isRotationEnabled() { return rotationEnabled; }
    public List<String> getRotationPatterns() { return List.copyOf(rotationPatterns); }
    public int getRotationIndex() { return rotationIndex; }
    public CellPlan getCellPlan() { return cellPlan; }
    @Nullable public UUID getOwnerUuid() { return ownerUuid; }
    public String getOwnerName() { return ownerName; }

    public String getEffectivePatternId() {
        if (rotationEnabled && !rotationPatterns.isEmpty()) {
            int idx = Math.floorMod(rotationIndex, rotationPatterns.size());
            return rotationPatterns.get(idx);
        }
        return patternId;
    }

    public void advanceRotation() {
        if (!rotationEnabled || rotationPatterns.isEmpty()) return;
        rotationIndex = (rotationIndex + 1) % rotationPatterns.size();
        setChanged();
    }

    /**
     * Builds a snapshot of the current config for use in payloads and the screen.
     */
    public FieldPostConfig toConfig() {
        return new FieldPostConfig(
                patternId, tierCap, radius, priority,
                autoSeedMode, List.copyOf(seedFilter),
                waterEnabled, maxWaterCells,
                groomEnabled, groomRadius,
                rotationEnabled, List.copyOf(rotationPatterns),
                cellPlan
        );
    }

    // ── Setters ──

    public void applyConfig(FieldPostConfig config) {
        this.patternId = config.patternId() == null || config.patternId().isBlank() ? DEFAULT_PATTERN : config.patternId();
        this.tierCap = Math.max(1, Math.min(config.tierCap(), 5));
        this.radius = Math.max(8, Math.min(config.radius(), 32));
        this.priority = Math.max(0, Math.min(config.priority(), 10));
        this.autoSeedMode = config.autoSeedMode();
        this.seedFilter.clear();
        if (config.seedFilter() != null) this.seedFilter.addAll(config.seedFilter());
        this.waterEnabled = config.waterEnabled();
        this.maxWaterCells = Math.max(0, Math.min(config.maxWaterCells(), 32));
        this.groomEnabled = config.groomEnabled();
        this.groomRadius = Math.max(1, Math.min(config.groomRadius(), 8));
        this.rotationEnabled = config.rotationEnabled();
        this.rotationPatterns.clear();
        if (config.rotationPatterns() != null) this.rotationPatterns.addAll(config.rotationPatterns());
        if (this.rotationIndex >= this.rotationPatterns.size() && !this.rotationPatterns.isEmpty()) {
            this.rotationIndex = 0;
        }
        this.cellPlan = config.cellPlan() != null ? config.cellPlan() : CellPlan.EMPTY;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            // Flush farmer snapshot caches so the new plan is picked up on next tick
            FieldPostIndex.notifyConfigChanged(level, worldPosition);
        }
    }

    public void setOwner(Player player) {
        this.ownerUuid = player.getUUID();
        this.ownerName = player.getName().getString();
        setChanged();
    }

    // ── NBT ──

    @Override
    //? if >=1.21 {
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    //?} else {
    /*protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
    *///?}
        tag.putString("patternId", patternId);
        tag.putInt("tierCap", tierCap);
        tag.putInt("radius", radius);
        tag.putInt("priority", priority);
        tag.putBoolean("autoSeedMode", autoSeedMode);
        tag.put("seedFilter", toStringList(seedFilter));
        tag.putBoolean("waterEnabled", waterEnabled);
        tag.putInt("maxWaterCells", maxWaterCells);
        tag.putBoolean("groomEnabled", groomEnabled);
        tag.putInt("groomRadius", groomRadius);
        tag.putBoolean("rotationEnabled", rotationEnabled);
        tag.put("rotationPatterns", toStringList(rotationPatterns));
        tag.putInt("rotationIndex", rotationIndex);
        CellPlan.save(tag, cellPlan);
        if (ownerUuid != null) tag.putUUID("ownerUuid", ownerUuid);
        tag.putString("ownerName", ownerName);
    }

    @Override
    //? if >=1.21 {
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    //?} else {
    /*public void load(CompoundTag tag) {
        super.load(tag);
    *///?}
        patternId = tag.getString("patternId");
        if (patternId.isBlank()) patternId = DEFAULT_PATTERN;
        tierCap = tag.contains("tierCap") ? Math.max(1, Math.min(tag.getInt("tierCap"), 5)) : DEFAULT_TIER_CAP;
        radius = tag.contains("radius") ? Math.max(8, Math.min(tag.getInt("radius"), 32)) : DEFAULT_RADIUS;
        priority = tag.contains("priority") ? Math.max(0, Math.min(tag.getInt("priority"), 10)) : DEFAULT_PRIORITY;
        autoSeedMode = !tag.contains("autoSeedMode") || tag.getBoolean("autoSeedMode");
        seedFilter.clear();
        seedFilter.addAll(fromStringList(tag.getList("seedFilter", Tag.TAG_STRING)));
        waterEnabled = !tag.contains("waterEnabled") || tag.getBoolean("waterEnabled");
        maxWaterCells = tag.contains("maxWaterCells") ? Math.max(0, Math.min(tag.getInt("maxWaterCells"), 32)) : DEFAULT_MAX_WATER_CELLS;
        groomEnabled = !tag.contains("groomEnabled") || tag.getBoolean("groomEnabled");
        groomRadius = tag.contains("groomRadius") ? Math.max(1, Math.min(tag.getInt("groomRadius"), 8)) : DEFAULT_GROOM_RADIUS;
        rotationEnabled = tag.getBoolean("rotationEnabled");
        rotationPatterns.clear();
        rotationPatterns.addAll(fromStringList(tag.getList("rotationPatterns", Tag.TAG_STRING)));
        rotationIndex = tag.getInt("rotationIndex");
        cellPlan = CellPlan.load(tag);
        ownerUuid = tag.hasUUID("ownerUuid") ? tag.getUUID("ownerUuid") : null;
        ownerName = tag.getString("ownerName");
    }

    private static ListTag toStringList(List<String> list) {
        ListTag tag = new ListTag();
        for (String s : list) tag.add(StringTag.valueOf(s));
        return tag;
    }

    private static List<String> fromStringList(ListTag tag) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < tag.size(); i++) list.add(tag.getString(i));
        return list;
    }

    // ── Client sync ──

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    //? if >=1.21 {
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
    //?} else {
    /*public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
    *///?}
        return tag;
    }

    // ── MenuProvider ──

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.townstead.field_post");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new FieldPostMenu(containerId, playerInventory, this);
    }

    // ── Lifecycle ──

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) FieldPostIndex.remove(level, worldPosition);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) FieldPostIndex.register(level, worldPosition, this);
    }
}
