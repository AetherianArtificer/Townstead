package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Server to client: the entities a {@code pheno:glow} with {@code on} outlines for this player alone. */
//? if neoforge {
public record EntityGlowS2CPayload(int[] entityIds) implements CustomPacketPayload {
//?} else {
/*public record EntityGlowS2CPayload(int[] entityIds) {
*///?}

    private static final int MAX = 1024;

    //? if neoforge {
    public static final Type<EntityGlowS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "entity_glow_s2c"));

    public static final StreamCodec<FriendlyByteBuf, EntityGlowS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), EntityGlowS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        int count = Math.min(entityIds.length, MAX);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeVarInt(entityIds[i]);
    }

    public static EntityGlowS2CPayload read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX);
        int[] ids = new int[count];
        for (int i = 0; i < count; i++) ids[i] = buf.readVarInt();
        return new EntityGlowS2CPayload(ids);
    }
}
