package com.aetherianartificer.townstead.temperature;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}
//? if neoforge {
public record ThermostatSnapshotPayload(BlockPos pos, int mode, int target, float air, boolean powered, boolean editable, boolean open, boolean coldSweat) implements CustomPacketPayload {
    public static final Type<ThermostatSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead","thermostat_snapshot"));
    public static final StreamCodec<FriendlyByteBuf,ThermostatSnapshotPayload> STREAM_CODEC = StreamCodec.of((buf,value)->value.write(buf),ThermostatSnapshotPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record ThermostatSnapshotPayload(BlockPos pos, int mode, int target, float air, boolean powered, boolean editable, boolean open, boolean coldSweat) {
*///?}
    public void write(FriendlyByteBuf buf) { buf.writeBlockPos(pos); buf.writeInt(mode); buf.writeInt(target); buf.writeFloat(air); buf.writeBoolean(powered); buf.writeBoolean(editable); buf.writeBoolean(open); buf.writeBoolean(coldSweat); }
    public static ThermostatSnapshotPayload read(FriendlyByteBuf buf) { return new ThermostatSnapshotPayload(buf.readBlockPos(),buf.readInt(),buf.readInt(),buf.readFloat(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean(),buf.readBoolean()); }
}
