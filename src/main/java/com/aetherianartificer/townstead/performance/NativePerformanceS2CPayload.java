package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Starts or stops a dependency-free semantic clip on a tracked living entity. */
//? if neoforge {
public record NativePerformanceS2CPayload(int entityId, String channel, String clipId,
                                          int durationTicks, int priority, long startedAt) implements CustomPacketPayload {
//?} else {
/*public record NativePerformanceS2CPayload(int entityId, String channel, String clipId,
                                          int durationTicks, int priority, long startedAt) {
*///?}
    public NativePerformanceS2CPayload(int entityId, String channel, String clipId, int durationTicks, int priority) {
        this(entityId, channel, clipId, durationTicks, priority, -1L);
    }
    //? if neoforge {
    public static final Type<NativePerformanceS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "native_performance_s2c"));
    public static final StreamCodec<FriendlyByteBuf, NativePerformanceS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NativePerformanceS2CPayload::entityId,
            ByteBufCodecs.STRING_UTF8, NativePerformanceS2CPayload::channel,
            ByteBufCodecs.STRING_UTF8, NativePerformanceS2CPayload::clipId,
            ByteBufCodecs.VAR_INT, NativePerformanceS2CPayload::durationTicks,
            ByteBufCodecs.VAR_INT, NativePerformanceS2CPayload::priority,
            ByteBufCodecs.VAR_LONG, NativePerformanceS2CPayload::startedAt,
            NativePerformanceS2CPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId); buf.writeUtf(channel); buf.writeUtf(clipId);
        buf.writeVarInt(durationTicks); buf.writeVarInt(priority); buf.writeVarLong(startedAt);
    }
    public static NativePerformanceS2CPayload read(FriendlyByteBuf buf) {
        return new NativePerformanceS2CPayload(buf.readVarInt(), buf.readUtf(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarLong());
    }
    *///?}
}
