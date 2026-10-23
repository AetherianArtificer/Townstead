package com.aetherianartificer.townstead.rebirth;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server to client: every reborn player's current name, by player id. */
//? if neoforge {
public record CharacterNamesS2CPayload(Map<UUID, String> names) implements CustomPacketPayload {
//?} else {
/*public record CharacterNamesS2CPayload(Map<UUID, String> names) {
*///?}

    //? if neoforge {
    public static final Type<CharacterNamesS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "character_names_s2c"));

    public static final StreamCodec<FriendlyByteBuf, CharacterNamesS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), CharacterNamesS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(names.size());
        names.forEach((id, name) -> {
            buf.writeUUID(id);
            buf.writeUtf(name, Rebirth.MAX_NAME_LENGTH);
        });
    }

    public static CharacterNamesS2CPayload read(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 4096);
        Map<UUID, String> names = new HashMap<>();
        for (int i = 0; i < size; i++) names.put(buf.readUUID(), buf.readUtf(Rebirth.MAX_NAME_LENGTH));
        return new CharacterNamesS2CPayload(Map.copyOf(names));
    }
}
