package com.aetherianartificer.townstead.aspect;

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
 * Server to client: the aspects {@code entityId} can be, each with its looks (a werewolf's coat) as
 * they would be drawn on them; the one they are ({@code current}, empty for none); why this player
 * may not change it ({@code locked}, a lang key, empty when they may); and whether they may change
 * the looks.
 */
//? if neoforge {
public record AspectS2CPayload(int entityId, List<Choice> options, String current, String locked, boolean styleAllowed) implements CustomPacketPayload {
//?} else {
/*public record AspectS2CPayload(int entityId, List<Choice> options, String current, String locked, boolean styleAllowed) {
*///?}

    /** One aspect on offer and its looks. */
    public record Choice(String id, List<Look> looks) {}

    /** One look of an aspect: its option id, how many there are, and the one shown now. */
    public record Look(String id, int count, int value) {}

    //? if neoforge {
    public static final Type<AspectS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "aspect_s2c"));
    public static final StreamCodec<FriendlyByteBuf, AspectS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AspectS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    /** Whether this player may change the aspect. */
    public boolean allowed() {
        return locked.isEmpty();
    }

    /** The choice with {@code id}, or null. */
    public Choice choice(String id) {
        for (Choice choice : options) if (choice.id().equals(id)) return choice;
        return null;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeVarInt(options.size());
        for (Choice choice : options) {
            buf.writeUtf(choice.id(), 256);
            buf.writeVarInt(choice.looks().size());
            for (Look look : choice.looks()) {
                buf.writeUtf(look.id(), 64);
                buf.writeVarInt(look.count());
                buf.writeVarInt(look.value());
            }
        }
        buf.writeUtf(current, 256);
        buf.writeUtf(locked, 256);
        buf.writeBoolean(styleAllowed);
    }

    public static AspectS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readInt();
        int size = Math.min(buf.readVarInt(), 64);
        List<Choice> options = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String choiceId = buf.readUtf(256);
            int lookCount = Math.min(buf.readVarInt(), 16);
            List<Look> looks = new ArrayList<>(lookCount);
            for (int j = 0; j < lookCount; j++) looks.add(new Look(buf.readUtf(64), buf.readVarInt(), buf.readVarInt()));
            options.add(new Choice(choiceId, List.copyOf(looks)));
        }
        String current = buf.readUtf(256);
        String locked = buf.readUtf(256);
        return new AspectS2CPayload(id, List.copyOf(options), current, locked, buf.readBoolean());
    }
}
