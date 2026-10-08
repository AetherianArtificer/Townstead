package com.aetherianartificer.townstead.dialogue;

//? if neoforge {
import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server → client: the data-pack dialogue themes, as JSON text by id. */
//? if neoforge {
public record DialogueThemesSyncPayload(Map<ResourceLocation, String> themes) implements CustomPacketPayload {
//?} else {
/*public record DialogueThemesSyncPayload(Map<ResourceLocation, String> themes) {
*///?}

    public static DialogueThemesSyncPayload snapshot() {
        return new DialogueThemesSyncPayload(DialogueThemeData.all());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(themes.size());
        themes.forEach((id, json) -> {
            buf.writeResourceLocation(id);
            buf.writeUtf(json, 65536);
        });
    }

    public static DialogueThemesSyncPayload read(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 4096);
        Map<ResourceLocation, String> themes = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) themes.put(buf.readResourceLocation(), buf.readUtf(65536));
        return new DialogueThemesSyncPayload(themes);
    }

    //? if neoforge {
    public static final Type<DialogueThemesSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "dialogue_themes_sync"));
    public static final StreamCodec<FriendlyByteBuf, DialogueThemesSyncPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), DialogueThemesSyncPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
