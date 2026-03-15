package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> Server shift assignment payload.
 * If shifts is empty (length 0), treated as a query for the villager's current shifts.
 */
//? if neoforge {
public record ShiftSetPayload(UUID villagerUuid, int[] shifts) implements CustomPacketPayload {
//?} else {
/*public record ShiftSetPayload(UUID villagerUuid, int[] shifts) {
*///?}

    //? if neoforge {
    public static final Type<ShiftSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_set"));

    public static final StreamCodec<FriendlyByteBuf, ShiftSetPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShiftSetPayload decode(FriendlyByteBuf buf) {
                    UUID uuid = new UUID(buf.readLong(), buf.readLong());
                    int len = buf.readVarInt();
                    int[] shifts = new int[len];
                    for (int i = 0; i < len; i++) shifts[i] = buf.readVarInt();
                    return new ShiftSetPayload(uuid, shifts);
                }

                @Override
                public void encode(FriendlyByteBuf buf, ShiftSetPayload payload) {
                    buf.writeLong(payload.villagerUuid().getMostSignificantBits());
                    buf.writeLong(payload.villagerUuid().getLeastSignificantBits());
                    buf.writeVarInt(payload.shifts().length);
                    for (int s : payload.shifts()) buf.writeVarInt(s);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "shift_set");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeLong(villagerUuid.getMostSignificantBits());
        buf.writeLong(villagerUuid.getLeastSignificantBits());
        buf.writeVarInt(shifts.length);
        for (int s : shifts) buf.writeVarInt(s);
    }

    public static ShiftSetPayload read(FriendlyByteBuf buf) {
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        int len = buf.readVarInt();
        int[] shifts = new int[len];
        for (int i = 0; i < len; i++) shifts[i] = buf.readVarInt();
        return new ShiftSetPayload(uuid, shifts);
    }
    *///?}
}
