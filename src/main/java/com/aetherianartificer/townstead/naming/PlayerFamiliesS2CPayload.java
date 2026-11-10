package com.aetherianartificer.townstead.naming;

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

/**
 * Server to client: every player's family name, by player id, including players who are offline.
 * The family tree shows people the client has no entity for.
 */
//? if neoforge {
public record PlayerFamiliesS2CPayload(Map<UUID, Entry> families) implements CustomPacketPayload {
//?} else {
/*public record PlayerFamiliesS2CPayload(Map<UUID, Entry> families) {
*///?}

    public record Entry(String family, NamingTradition.Order order) {}

    private static final int MAX_LENGTH = 256;

    //? if neoforge {
    public static final Type<PlayerFamiliesS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "player_families"));

    public static final StreamCodec<FriendlyByteBuf, PlayerFamiliesS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), PlayerFamiliesS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "player_families");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "player_families");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(families.size());
        families.forEach((id, entry) -> {
            buf.writeUUID(id);
            buf.writeUtf(entry.family(), MAX_LENGTH);
            buf.writeEnum(entry.order());
        });
    }

    public static PlayerFamiliesS2CPayload read(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 4096);
        Map<UUID, Entry> families = new HashMap<>();
        for (int i = 0; i < size; i++) {
            UUID id = buf.readUUID();
            families.put(id, new Entry(buf.readUtf(MAX_LENGTH), buf.readEnum(NamingTradition.Order.class)));
        }
        return new PlayerFamiliesS2CPayload(Map.copyOf(families));
    }
}
