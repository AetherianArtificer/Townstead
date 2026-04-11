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
 * Client -> Server: player applies Field Post configuration (config + cell plan).
 */
//? if neoforge {
public record FieldPostConfigSetPayload(BlockPos pos, FieldPostConfig config) implements CustomPacketPayload {

    public static final Type<FieldPostConfigSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "field_post_config_set"));

    public static final StreamCodec<FriendlyByteBuf, FieldPostConfigSetPayload> STREAM_CODEC =
            StreamCodec.of(FieldPostConfigSetPayload::encode, FieldPostConfigSetPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record FieldPostConfigSetPayload(BlockPos pos, FieldPostConfig config) {
*///?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "field_post_config_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "field_post_config_set");
    *///?}

    private static void encode(FriendlyByteBuf buf, FieldPostConfigSetPayload p) { p.write(buf); }
    private static FieldPostConfigSetPayload decode(FriendlyByteBuf buf) { return read(buf); }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        config.write(buf);
    }

    public static FieldPostConfigSetPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        FieldPostConfig config = FieldPostConfig.read(buf);
        return new FieldPostConfigSetPayload(pos, config);
    }
}
