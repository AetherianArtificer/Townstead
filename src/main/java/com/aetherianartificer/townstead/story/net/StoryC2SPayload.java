package com.aetherianartificer.townstead.story.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player's side of a story conversation, from the dialogue screen. The server checks the
 * villager, distance and open session for every action; the screen is never an authority.
 */
//? if neoforge {
public record StoryC2SPayload(int villagerId, byte action, int index) implements CustomPacketPayload {
//?} else {
/*public record StoryC2SPayload(int villagerId, byte action, int index) {
*///?}
    /** Ask whether this villager has a story for the player, answered with an offer. */
    public static final byte OFFER = 0;
    /** Start talking about the story. */
    public static final byte TALK = 1;
    /** Show the next line. */
    public static final byte NEXT = 2;
    /** Pick choice {@code index}. */
    public static final byte CHOOSE = 3;
    /** The screen closed. */
    public static final byte CLOSE = 4;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerId);
        buf.writeByte(action);
        buf.writeVarInt(index);
    }

    public static StoryC2SPayload read(FriendlyByteBuf buf) {
        return new StoryC2SPayload(buf.readVarInt(), buf.readByte(), buf.readVarInt());
    }

    //? if neoforge {
    public static final Type<StoryC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "story_c2s"));
    public static final StreamCodec<FriendlyByteBuf, StoryC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), StoryC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "story_c2s");
    *///?}
}
