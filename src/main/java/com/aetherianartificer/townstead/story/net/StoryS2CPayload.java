package com.aetherianartificer.townstead.story.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * A story step for the dialogue screen. An offer carries the menu label (empty when there is no
 * story). A line carries one line of speech and, when it is the last before a choice, the choices;
 * {@code more} says another line follows. An end returns the screen to the villager's menu.
 */
//? if neoforge {
public record StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more)
        implements CustomPacketPayload {
//?} else {
/*public record StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more) {
*///?}
    public static final byte OFFER = 0;
    public static final byte LINE = 1;
    public static final byte END = 2;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerId);
        buf.writeByte(kind);
        buf.writeUtf(text);
        buf.writeVarInt(choices.size());
        for (String choice : choices) buf.writeUtf(choice);
        buf.writeBoolean(more);
    }

    public static StoryS2CPayload read(FriendlyByteBuf buf) {
        int villagerId = buf.readVarInt();
        byte kind = buf.readByte();
        String text = buf.readUtf();
        int count = Math.min(buf.readVarInt(), 64);
        List<String> choices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) choices.add(buf.readUtf());
        return new StoryS2CPayload(villagerId, kind, text, List.copyOf(choices), buf.readBoolean());
    }

    //? if neoforge {
    public static final Type<StoryS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "story_s2c"));
    public static final StreamCodec<FriendlyByteBuf, StoryS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), StoryS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "story_s2c");
    *///?}
}
