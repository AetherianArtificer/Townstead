package com.aetherianartificer.townstead.farming;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.farming.cellplan.FieldPostConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> Client: Field Post configuration + cell plan + live status.
 */
//? if neoforge {
public record FieldPostConfigSyncPayload(
        BlockPos pos,
        FieldPostConfig config,
        // Status fields
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
        FieldPostConfig config,
        // Status fields
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
        config.write(buf);
        buf.writeUtf(resolvedPatternId);
        buf.writeVarInt(farmerCount);
        buf.writeVarInt(totalPlots);
        buf.writeVarInt(tilledPlots);
        buf.writeVarInt(hydrationPercent);
    }

    public static FieldPostConfigSyncPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        FieldPostConfig config = FieldPostConfig.read(buf);
        String resolvedPatternId = buf.readUtf();
        int farmerCount = buf.readVarInt();
        int totalPlots = buf.readVarInt();
        int tilledPlots = buf.readVarInt();
        int hydrationPercent = buf.readVarInt();
        return new FieldPostConfigSyncPayload(pos, config, resolvedPatternId,
                farmerCount, totalPlots, tilledPlots, hydrationPercent);
    }
}
