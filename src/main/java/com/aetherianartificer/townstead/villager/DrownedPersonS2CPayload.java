package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The zombie body a drowned villager is drawn as: its MCA zombie type and saved data, keyed by the Drowned's id. */
//? if neoforge {
public record DrownedPersonS2CPayload(int entityId, String zombieType, CompoundTag data) implements CustomPacketPayload {
//?} else {
/*public record DrownedPersonS2CPayload(int entityId, String zombieType, CompoundTag data) {
*///?}
    private static final Map<Integer, DrownedPersonS2CPayload> BODIES = new ConcurrentHashMap<>();

    //? if neoforge {
    public static final Type<DrownedPersonS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "drowned_person"));

    public static final StreamCodec<FriendlyByteBuf, DrownedPersonS2CPayload> STREAM_CODEC =
            StreamCodec.of(DrownedPersonS2CPayload::write, DrownedPersonS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, DrownedPersonS2CPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.zombieType);
        buf.writeNbt(payload.data);
    }

    public static DrownedPersonS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String type = buf.readUtf();
        CompoundTag data = buf.readNbt();
        return new DrownedPersonS2CPayload(id, type, data == null ? new CompoundTag() : data);
    }

    /** Client side: records the body until the Drowned leaves the client's world. */
    public static void accept(DrownedPersonS2CPayload payload) {
        BODIES.put(payload.entityId, payload);
    }

    /** Client side: the body this entity is drawn as, or null. */
    public static DrownedPersonS2CPayload of(int entityId) {
        return BODIES.get(entityId);
    }

    public static void forget(int entityId) {
        BODIES.remove(entityId);
    }

    public static void clear() {
        BODIES.clear();
    }
}
