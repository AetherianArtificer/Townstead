package com.aetherianartificer.townstead.farming.cellplan;

import com.aetherianartificer.townstead.block.FieldPostBlockEntity;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable snapshot of all user-configurable Field Post settings.
 * Serialized in both Set and Sync payloads. Passed as a single argument to applyConfig.
 */
public record FieldPostConfig(
        String patternId,
        int tierCap,
        int radius,
        int priority,
        boolean autoSeedMode,
        List<String> seedFilter,
        boolean waterEnabled,
        int maxWaterCells,
        boolean groomEnabled,
        int groomRadius,
        boolean rotationEnabled,
        List<String> rotationPatterns,
        CellPlan cellPlan
) {
    public FieldPostConfig withCellPlan(CellPlan plan) {
        return new FieldPostConfig(patternId, tierCap, radius, priority,
                autoSeedMode, seedFilter, waterEnabled, maxWaterCells,
                groomEnabled, groomRadius, rotationEnabled, rotationPatterns, plan);
    }

    public static FieldPostConfig defaults() {
        return new FieldPostConfig(
                FieldPostBlockEntity.DEFAULT_PATTERN,
                FieldPostBlockEntity.DEFAULT_TIER_CAP,
                FieldPostBlockEntity.DEFAULT_RADIUS,
                FieldPostBlockEntity.DEFAULT_PRIORITY,
                true, List.of(), true,
                FieldPostBlockEntity.DEFAULT_MAX_WATER_CELLS,
                true, FieldPostBlockEntity.DEFAULT_GROOM_RADIUS,
                false, List.of(), CellPlan.EMPTY
        );
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(patternId);
        buf.writeVarInt(tierCap);
        buf.writeVarInt(radius);
        buf.writeVarInt(priority);
        buf.writeBoolean(autoSeedMode);
        buf.writeVarInt(seedFilter.size());
        for (String s : seedFilter) buf.writeUtf(s);
        buf.writeBoolean(waterEnabled);
        buf.writeVarInt(maxWaterCells);
        buf.writeBoolean(groomEnabled);
        buf.writeVarInt(groomRadius);
        buf.writeBoolean(rotationEnabled);
        buf.writeVarInt(rotationPatterns.size());
        for (String s : rotationPatterns) buf.writeUtf(s);
        cellPlan.write(buf);
    }

    public static FieldPostConfig read(FriendlyByteBuf buf) {
        String patternId = buf.readUtf();
        int tierCap = buf.readVarInt();
        int radius = buf.readVarInt();
        int priority = buf.readVarInt();
        boolean autoSeedMode = buf.readBoolean();
        int seedCount = buf.readVarInt();
        List<String> seedFilter = new ArrayList<>();
        for (int i = 0; i < seedCount; i++) seedFilter.add(buf.readUtf());
        boolean waterEnabled = buf.readBoolean();
        int maxWaterCells = buf.readVarInt();
        boolean groomEnabled = buf.readBoolean();
        int groomRadius = buf.readVarInt();
        boolean rotationEnabled = buf.readBoolean();
        int rotCount = buf.readVarInt();
        List<String> rotationPatterns = new ArrayList<>();
        for (int i = 0; i < rotCount; i++) rotationPatterns.add(buf.readUtf());
        CellPlan cellPlan = CellPlan.read(buf);
        return new FieldPostConfig(patternId, tierCap, radius, priority,
                autoSeedMode, seedFilter, waterEnabled, maxWaterCells,
                groomEnabled, groomRadius, rotationEnabled, rotationPatterns, cellPlan);
    }
}
