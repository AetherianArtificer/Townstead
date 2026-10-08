package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * The building types still locked in one village (a lodge, until an order is based there), sent
 * alongside MCA's village snapshot so the catalog can leave them out.
 */
//? if neoforge {
public record VillageLocksS2CPayload(int villageId, List<String> locked) implements CustomPacketPayload {
//?} else {
/*public record VillageLocksS2CPayload(int villageId, List<String> locked) {
*///?}
    public VillageLocksS2CPayload {
        locked = List.copyOf(locked);
    }

    //? if neoforge {
    public static final Type<VillageLocksS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "village_locks"));

    public static final StreamCodec<FriendlyByteBuf, VillageLocksS2CPayload> STREAM_CODEC =
            StreamCodec.of(VillageLocksS2CPayload::write, VillageLocksS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, VillageLocksS2CPayload payload) {
        buf.writeVarInt(payload.villageId);
        buf.writeVarInt(payload.locked.size());
        for (String type : payload.locked) buf.writeUtf(type);
    }

    public static VillageLocksS2CPayload read(FriendlyByteBuf buf) {
        int villageId = buf.readVarInt();
        int count = buf.readVarInt();
        List<String> locked = new ArrayList<>(count);
        for (int i = 0; i < count; i++) locked.add(buf.readUtf());
        return new VillageLocksS2CPayload(villageId, locked);
    }
}
