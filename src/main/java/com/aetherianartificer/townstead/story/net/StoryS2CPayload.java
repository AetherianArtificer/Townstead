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
 * {@code speakerId} is who says the line: the villager the player is talking to, or another villager
 * in the scene. {@code theme} is that speaker's own dialogue theme id, or empty (for an offer, the
 * villager's). {@code questChoices} has a bit set for each choice that moves the story on. A
 * Persona's offer lists, as its choices, the MCA answers the Persona hides, and carries their own
 * {@code greeting} line to say in place of MCA's.
 */
//? if neoforge {
public record StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more,
                              int speakerId, String theme, int questChoices, String greeting) implements CustomPacketPayload {
//?} else {
/*public record StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more,
                              int speakerId, String theme, int questChoices, String greeting) {
*///?}
    public static final byte OFFER = 0;
    public static final byte LINE = 1;
    public static final byte END = 2;
    /** An offer from a Persona: the same as {@link #OFFER}, and the screen hides MCA's romance options. */
    public static final byte OFFER_PERSONA = 3;

    public StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more) {
        this(villagerId, kind, text, choices, more, villagerId, "", 0, "");
    }

    public StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more,
                           int speakerId, String theme) {
        this(villagerId, kind, text, choices, more, speakerId, theme, 0, "");
    }

    public StoryS2CPayload(int villagerId, byte kind, String text, List<String> choices, boolean more,
                           int speakerId, String theme, int questChoices) {
        this(villagerId, kind, text, choices, more, speakerId, theme, questChoices, "");
    }

    /** Whether choice {@code index} moves the story on (tagged {@code # advance} in the Ink). */
    public boolean questChoice(int index) {
        return index >= 0 && index < 32 && (questChoices & (1 << index)) != 0;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(villagerId);
        buf.writeByte(kind);
        buf.writeUtf(text);
        buf.writeVarInt(choices.size());
        for (String choice : choices) buf.writeUtf(choice);
        buf.writeBoolean(more);
        buf.writeVarInt(speakerId);
        buf.writeUtf(theme);
        buf.writeInt(questChoices);
        buf.writeUtf(greeting);
    }

    public static StoryS2CPayload read(FriendlyByteBuf buf) {
        int villagerId = buf.readVarInt();
        byte kind = buf.readByte();
        String text = buf.readUtf();
        int count = Math.min(buf.readVarInt(), 64);
        List<String> choices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) choices.add(buf.readUtf());
        boolean more = buf.readBoolean();
        int speakerId = buf.readVarInt();
        String theme = buf.readUtf();
        int questChoices = buf.readInt();
        return new StoryS2CPayload(villagerId, kind, text, List.copyOf(choices), more, speakerId, theme, questChoices, buf.readUtf());
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
