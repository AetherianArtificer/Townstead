package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server -> Client: every outfit template plus the whole assignment grid. */
//? if neoforge {
public record WardrobeSyncPayload(List<WardrobeTemplate> templates, List<String> village,
                                  Map<UUID, List<String>> villagers) implements CustomPacketPayload {
//?} else {
/*public record WardrobeSyncPayload(List<WardrobeTemplate> templates, List<String> village,
                                  Map<UUID, List<String>> villagers) {
*///?}

    //? if neoforge {
    public static final Type<WardrobeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wardrobe_sync"));

    public static final StreamCodec<FriendlyByteBuf, WardrobeSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WardrobeSyncPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, WardrobeSyncPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wardrobe_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "wardrobe_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(templates.size());
        for (WardrobeTemplate template : templates) template.write(buf);
        writeRow(buf, village);
        buf.writeVarInt(villagers.size());
        for (Map.Entry<UUID, List<String>> e : villagers.entrySet()) {
            buf.writeUUID(e.getKey());
            writeRow(buf, e.getValue());
        }
    }

    private static void writeRow(FriendlyByteBuf buf, List<String> row) {
        buf.writeVarInt(row.size());
        for (String cell : row) buf.writeUtf(cell == null ? "" : cell);
    }

    private static List<String> readRow(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> row = new ArrayList<>(n);
        for (int i = 0; i < n; i++) row.add(buf.readUtf());
        return row;
    }

    public static WardrobeSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<WardrobeTemplate> templates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) templates.add(WardrobeTemplate.read(buf));
        List<String> village = readRow(buf);
        int m = buf.readVarInt();
        Map<UUID, List<String>> villagers = new LinkedHashMap<>();
        for (int i = 0; i < m; i++) {
            UUID uuid = buf.readUUID();
            villagers.put(uuid, readRow(buf));
        }
        return new WardrobeSyncPayload(templates, village, villagers);
    }
}
