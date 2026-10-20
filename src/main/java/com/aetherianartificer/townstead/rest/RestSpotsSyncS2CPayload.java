package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The rest spots that hide a sleeper ({@code sleep.hidden_when}), for the client to render by. */
//? if neoforge {
public record RestSpotsSyncS2CPayload(List<Hiding> spots) implements CustomPacketPayload {
//?} else {
/*public record RestSpotsSyncS2CPayload(List<Hiding> spots) {
*///?}
    public record Hiding(List<RestSpot.BlockMatch> blocks, Map<String, String> hiddenWhen) {}

    private static volatile List<Hiding> client = List.of();

    //? if neoforge {
    public static final Type<RestSpotsSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "rest_spots_sync"));

    public static final StreamCodec<FriendlyByteBuf, RestSpotsSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of(RestSpotsSyncS2CPayload::write, RestSpotsSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static RestSpotsSyncS2CPayload snapshot() {
        List<Hiding> out = new ArrayList<>();
        for (RestSpot spot : RestSpots.all()) {
            if (!spot.hiddenWhen().isEmpty()) out.add(new Hiding(spot.blocks(), spot.hiddenWhen()));
        }
        return new RestSpotsSyncS2CPayload(out);
    }

    public static void write(FriendlyByteBuf buf, RestSpotsSyncS2CPayload payload) {
        buf.writeVarInt(payload.spots.size());
        for (Hiding spot : payload.spots) {
            buf.writeVarInt(spot.blocks.size());
            for (RestSpot.BlockMatch block : spot.blocks) {
                buf.writeResourceLocation(block.id());
                buf.writeBoolean(block.tag());
                writeMap(buf, block.state());
            }
            writeMap(buf, spot.hiddenWhen);
        }
    }

    public static RestSpotsSyncS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Hiding> spots = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int blockCount = buf.readVarInt();
            List<RestSpot.BlockMatch> blocks = new ArrayList<>(blockCount);
            for (int b = 0; b < blockCount; b++) {
                blocks.add(new RestSpot.BlockMatch(buf.readResourceLocation(), buf.readBoolean(), readMap(buf)));
            }
            spots.add(new Hiding(List.copyOf(blocks), readMap(buf)));
        }
        return new RestSpotsSyncS2CPayload(List.copyOf(spots));
    }

    private static void writeMap(FriendlyByteBuf buf, Map<String, String> map) {
        buf.writeVarInt(map.size());
        for (Map.Entry<String, String> entry : map.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    private static Map<String, String> readMap(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) map.put(buf.readUtf(), buf.readUtf());
        return Map.copyOf(map);
    }

    /** Client side. */
    public static void accept(RestSpotsSyncS2CPayload payload) {
        client = payload.spots;
    }

    /** Client side: the hiding spots the server sent. */
    public static List<Hiding> hiding() {
        return client;
    }
}
