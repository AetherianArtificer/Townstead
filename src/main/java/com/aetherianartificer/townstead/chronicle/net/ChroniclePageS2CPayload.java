package com.aetherianartificer.townstead.chronicle.net;

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
 * Server -> Client page of chronicle entries. Display is server-resolved
 * (literal fallback + lang key + args) and, for knowledge views, BELIEVED —
 * the overlay is applied server-side and the truth never ships to players.
 */
//? if neoforge {
public record ChroniclePageS2CPayload(int requestId, byte mode, boolean hasMore,
                                      List<EntryView> entries) implements CustomPacketPayload {
//?} else {
/*public record ChroniclePageS2CPayload(int requestId, byte mode, boolean hasMore,
                                      List<EntryView> entries) {
*///?}

    /** One rendered line. {@code channel} is empty outside knowledge views. */
    public record EntryView(long eventId, long worldDay, String dateLabel,
                            String headlineLiteral, String headlineLangKey, List<String> args,
                            String category, String channel, float fidelity) {

        void write(FriendlyByteBuf buf) {
            buf.writeLong(eventId);
            buf.writeLong(worldDay);
            buf.writeUtf(dateLabel);
            buf.writeUtf(headlineLiteral);
            buf.writeUtf(headlineLangKey);
            buf.writeVarInt(args.size());
            for (String arg : args) buf.writeUtf(arg);
            buf.writeUtf(category);
            buf.writeUtf(channel);
            buf.writeFloat(fidelity);
        }

        static EntryView read(FriendlyByteBuf buf) {
            long eventId = buf.readLong();
            long worldDay = buf.readLong();
            String dateLabel = buf.readUtf();
            String literal = buf.readUtf();
            String langKey = buf.readUtf();
            int argCount = buf.readVarInt();
            List<String> args = new ArrayList<>(argCount);
            for (int i = 0; i < argCount; i++) args.add(buf.readUtf());
            return new EntryView(eventId, worldDay, dateLabel, literal, langKey, args,
                    buf.readUtf(), buf.readUtf(), buf.readFloat());
        }
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeByte(mode);
        buf.writeBoolean(hasMore);
        buf.writeVarInt(entries.size());
        for (EntryView entry : entries) entry.write(buf);
    }

    public static ChroniclePageS2CPayload read(FriendlyByteBuf buf) {
        int requestId = buf.readVarInt();
        byte mode = buf.readByte();
        boolean hasMore = buf.readBoolean();
        int count = buf.readVarInt();
        List<EntryView> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) entries.add(EntryView.read(buf));
        return new ChroniclePageS2CPayload(requestId, mode, hasMore, entries);
    }

    //? if neoforge {
    public static final Type<ChroniclePageS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "chronicle_page"));

    public static final StreamCodec<FriendlyByteBuf, ChroniclePageS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ChroniclePageS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "chronicle_page");
    *///?}
}
