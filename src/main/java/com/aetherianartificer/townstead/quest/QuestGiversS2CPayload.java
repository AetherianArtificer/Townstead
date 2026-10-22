package com.aetherianartificer.townstead.quest;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Where the givers of a player's open quests are, or were last seen, for the quest tracker. */
//? if neoforge {
public record QuestGiversS2CPayload(List<Giver> givers) implements CustomPacketPayload {
//?} else {
/*public record QuestGiversS2CPayload(List<Giver> givers) {
*///?}
    public record Giver(UUID id, String dimension, BlockPos pos) {}

    private static volatile Map<UUID, Giver> known = Map.of();

    //? if neoforge {
    public static final Type<QuestGiversS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "quest_givers"));

    public static final StreamCodec<FriendlyByteBuf, QuestGiversS2CPayload> STREAM_CODEC =
            StreamCodec.of(QuestGiversS2CPayload::write, QuestGiversS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, QuestGiversS2CPayload payload) {
        buf.writeVarInt(payload.givers.size());
        for (Giver giver : payload.givers) {
            buf.writeUUID(giver.id());
            buf.writeUtf(giver.dimension());
            buf.writeBlockPos(giver.pos());
        }
    }

    public static QuestGiversS2CPayload read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 256);
        List<Giver> givers = new ArrayList<>(count);
        for (int i = 0; i < count; i++) givers.add(new Giver(buf.readUUID(), buf.readUtf(), buf.readBlockPos()));
        return new QuestGiversS2CPayload(List.copyOf(givers));
    }

    /** Client side: replaces what is known. */
    public static void accept(QuestGiversS2CPayload payload) {
        Map<UUID, Giver> next = new ConcurrentHashMap<>();
        for (Giver giver : payload.givers) next.put(giver.id(), giver);
        known = next;
    }

    /** Client side: where this giver is, or null when unknown. */
    public static Giver of(UUID id) {
        return known.get(id);
    }

    public static void clear() {
        known = Map.of();
    }
}
