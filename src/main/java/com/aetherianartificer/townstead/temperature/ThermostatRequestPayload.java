package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}
//? if neoforge {
public record ThermostatRequestPayload(BlockPos pos, int mode, int target) implements CustomPacketPayload {
    public static final Type<ThermostatRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead","thermostat_request"));
    public static final StreamCodec<FriendlyByteBuf,ThermostatRequestPayload> STREAM_CODEC = StreamCodec.of((buf,value)->value.write(buf),ThermostatRequestPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record ThermostatRequestPayload(BlockPos pos, int mode, int target) {
*///?}
    public void write(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeInt(mode); buf.writeInt(target); }
    public static ThermostatRequestPayload read(FriendlyByteBuf buf) { return new ThermostatRequestPayload(buf.readBlockPos(),buf.readInt(),buf.readInt()); }
}
