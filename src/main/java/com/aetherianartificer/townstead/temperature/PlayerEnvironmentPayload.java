package com.aetherianartificer.townstead.temperature;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record PlayerEnvironmentPayload(ResourceLocation dimension, int entityId, float celsius) implements CustomPacketPayload {
    public static final Type<PlayerEnvironmentPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("townstead", "player_environment"));
    public static final StreamCodec<FriendlyByteBuf, PlayerEnvironmentPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), PlayerEnvironmentPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record PlayerEnvironmentPayload(ResourceLocation dimension, int entityId, float celsius) {
*///?}
    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(dimension); buf.writeVarInt(entityId); buf.writeFloat(celsius);
    }
    public static PlayerEnvironmentPayload read(FriendlyByteBuf buf) {
        return new PlayerEnvironmentPayload(buf.readResourceLocation(), buf.readVarInt(), buf.readFloat());
    }
}
