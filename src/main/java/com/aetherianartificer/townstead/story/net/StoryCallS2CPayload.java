package com.aetherianartificer.townstead.story.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Server to client: what a villager has waiting for this player (see {@code StoryCalls}), 0 for nothing. */
//? if neoforge {
public record StoryCallS2CPayload(int entityId, byte state) implements CustomPacketPayload {
//?} else {
/*public record StoryCallS2CPayload(int entityId, byte state) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeByte(state);
    }

    public static StoryCallS2CPayload read(FriendlyByteBuf buf) {
        return new StoryCallS2CPayload(buf.readVarInt(), buf.readByte());
    }

    //? if neoforge {
    public static final Type<StoryCallS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "story_call"));
    public static final StreamCodec<FriendlyByteBuf, StoryCallS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), StoryCallS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
