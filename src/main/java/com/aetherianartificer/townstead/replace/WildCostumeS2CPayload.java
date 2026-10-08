package com.aetherianartificer.townstead.replace;

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

/** The person a replaced mob shows: the villager type and its saved data, keyed by the mob's id. */
//? if neoforge {
public record WildCostumeS2CPayload(int entityId, String villagerType, CompoundTag data) implements CustomPacketPayload {
//?} else {
/*public record WildCostumeS2CPayload(int entityId, String villagerType, CompoundTag data) {
*///?}
    private static final Map<Integer, WildCostumeS2CPayload> COSTUMES = new ConcurrentHashMap<>();

    //? if neoforge {
    public static final Type<WildCostumeS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "wild_costume"));

    public static final StreamCodec<FriendlyByteBuf, WildCostumeS2CPayload> STREAM_CODEC =
            StreamCodec.of(WildCostumeS2CPayload::write, WildCostumeS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, WildCostumeS2CPayload payload) {
        buf.writeVarInt(payload.entityId);
        buf.writeUtf(payload.villagerType);
        buf.writeNbt(payload.data);
    }

    public static WildCostumeS2CPayload read(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String type = buf.readUtf();
        CompoundTag data = buf.readNbt();
        return new WildCostumeS2CPayload(id, type, data == null ? new CompoundTag() : data);
    }

    /** Client side: records the look until the mob leaves the client's world. */
    public static void accept(WildCostumeS2CPayload payload) {
        COSTUMES.put(payload.entityId, payload);
    }

    /** Client side: the look this entity wears, or null. */
    public static WildCostumeS2CPayload of(int entityId) {
        return COSTUMES.get(entityId);
    }

    public static void forget(int entityId) {
        COSTUMES.remove(entityId);
    }

    public static void clear() {
        COSTUMES.clear();
    }
}
