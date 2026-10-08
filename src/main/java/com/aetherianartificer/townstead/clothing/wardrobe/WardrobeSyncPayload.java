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

/** Server -> Client: outfit template names, the village weather layers, and each asked-for resident's wardrobe. */
//? if neoforge {
public record WardrobeSyncPayload(List<WardrobeTemplate> templates, boolean villageWarm, boolean villageLight,
                                  Map<UUID, Resident> residents) implements CustomPacketPayload {
//?} else {
/*public record WardrobeSyncPayload(List<WardrobeTemplate> templates, boolean villageWarm, boolean villageLight,
                                  Map<UUID, Resident> residents) {
*///?}

    /**
     * One resident's row and what the picker needs to know about them.
     *
     * @param gender    MCA gender ordinal
     * @param household a key shared by villagers with the same home, or empty
     * @param worksite  a key shared by villagers with the same workplace, or empty
     */
    public record Resident(List<String> row, String work, byte warm, byte light, List<String> starred,
                           Map<String, Integer> picks, int gender, String rootId, boolean locked,
                           String household, String worksite, String clothes) {

        void write(FriendlyByteBuf buf) {
            writeStrings(buf, row);
            buf.writeUtf(work);
            buf.writeByte(warm);
            buf.writeByte(light);
            writeStrings(buf, starred);
            buf.writeVarInt(picks.size());
            for (Map.Entry<String, Integer> e : picks.entrySet()) {
                buf.writeUtf(e.getKey());
                buf.writeVarInt(e.getValue());
            }
            buf.writeVarInt(gender);
            buf.writeUtf(rootId);
            buf.writeBoolean(locked);
            buf.writeUtf(household);
            buf.writeUtf(worksite);
            buf.writeUtf(clothes);
        }

        static Resident read(FriendlyByteBuf buf) {
            List<String> row = readStrings(buf);
            String work = buf.readUtf();
            byte warm = buf.readByte();
            byte light = buf.readByte();
            List<String> starred = readStrings(buf);
            int n = buf.readVarInt();
            Map<String, Integer> picks = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) picks.put(buf.readUtf(), buf.readVarInt());
            return new Resident(row, work, warm, light, starred, picks, buf.readVarInt(), buf.readUtf(),
                    buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf());
        }
    }

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
        buf.writeBoolean(villageWarm);
        buf.writeBoolean(villageLight);
        buf.writeVarInt(residents.size());
        for (Map.Entry<UUID, Resident> e : residents.entrySet()) {
            buf.writeUUID(e.getKey());
            e.getValue().write(buf);
        }
    }

    private static void writeStrings(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) buf.writeUtf(value == null ? "" : value);
    }

    private static List<String> readStrings(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf());
        return out;
    }

    public static WardrobeSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<WardrobeTemplate> templates = new ArrayList<>(n);
        for (int i = 0; i < n; i++) templates.add(WardrobeTemplate.read(buf));
        boolean warm = buf.readBoolean();
        boolean light = buf.readBoolean();
        int m = buf.readVarInt();
        Map<UUID, Resident> residents = new LinkedHashMap<>();
        for (int i = 0; i < m; i++) {
            UUID uuid = buf.readUUID();
            residents.put(uuid, Resident.read(buf));
        }
        return new WardrobeSyncPayload(templates, warm, light, residents);
    }
}
