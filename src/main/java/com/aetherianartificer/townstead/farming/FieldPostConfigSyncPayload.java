package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Server -> Client: Field Post configuration + cell plan + live status.
 */
//? if neoforge {
public record FieldPostConfigSyncPayload(
        BlockPos pos,
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
        Map<Long, String> cellPlan,
        // Status
        String resolvedPatternId,
        int farmerCount,
        int totalPlots,
        int tilledPlots,
        int hydrationPercent
) implements CustomPacketPayload {

    public static final Type<FieldPostConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "field_post_config_sync"));

    public static final StreamCodec<FriendlyByteBuf, FieldPostConfigSyncPayload> STREAM_CODEC =
            StreamCodec.of(FieldPostConfigSyncPayload::encode, FieldPostConfigSyncPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record FieldPostConfigSyncPayload(
        BlockPos pos,
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
        Map<Long, String> cellPlan,
        // Status
        String resolvedPatternId,
        int farmerCount,
        int totalPlots,
        int tilledPlots,
        int hydrationPercent
) {
*///?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "field_post_config_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "field_post_config_sync");
    *///?}

    private static void encode(FriendlyByteBuf buf, FieldPostConfigSyncPayload p) { p.write(buf); }
    private static FieldPostConfigSyncPayload decode(FriendlyByteBuf buf) { return read(buf); }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
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
        buf.writeVarInt(cellPlan.size());
        cellPlan.forEach((posLong, seedId) -> {
            buf.writeLong(posLong);
            buf.writeUtf(seedId);
        });
        buf.writeUtf(resolvedPatternId);
        buf.writeVarInt(farmerCount);
        buf.writeVarInt(totalPlots);
        buf.writeVarInt(tilledPlots);
        buf.writeVarInt(hydrationPercent);
    }

    public static FieldPostConfigSyncPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
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
        int planCount = buf.readVarInt();
        Map<Long, String> cellPlan = new HashMap<>();
        for (int i = 0; i < planCount; i++) cellPlan.put(buf.readLong(), buf.readUtf());
        String resolvedPatternId = buf.readUtf();
        int farmerCount = buf.readVarInt();
        int totalPlots = buf.readVarInt();
        int tilledPlots = buf.readVarInt();
        int hydrationPercent = buf.readVarInt();
        return new FieldPostConfigSyncPayload(pos, patternId, tierCap, radius, priority,
                autoSeedMode, seedFilter, waterEnabled, maxWaterCells,
                groomEnabled, groomRadius, rotationEnabled, rotationPatterns, cellPlan,
                resolvedPatternId, farmerCount, totalPlots, tilledPlots, hydrationPercent);
    }
}
