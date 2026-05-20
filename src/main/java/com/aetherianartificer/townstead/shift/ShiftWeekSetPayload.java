package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Client -> Server: set a villager's weekly schedule state (mode + per-weekday
 * template references). Empty {@code weekDays} entries fall back to the daily
 * schedule for that day.
 */
//? if neoforge {
public record ShiftWeekSetPayload(UUID villagerUuid, String mode, List<String> weekDays)
        implements CustomPacketPayload {
//?} else {
/*public record ShiftWeekSetPayload(UUID villagerUuid, String mode, List<String> weekDays) {
*///?}

    //? if neoforge {
    public static final Type<ShiftWeekSetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_week_set"));

    public static final StreamCodec<FriendlyByteBuf, ShiftWeekSetPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ShiftWeekSetPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, ShiftWeekSetPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "shift_week_set");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "shift_week_set");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeLong(villagerUuid.getMostSignificantBits());
        buf.writeLong(villagerUuid.getLeastSignificantBits());
        buf.writeUtf(mode == null ? ShiftData.MODE_DAILY : mode);
        buf.writeVarInt(weekDays == null ? 0 : weekDays.size());
        if (weekDays != null) {
            for (String d : weekDays) buf.writeUtf(d == null ? "" : d);
        }
    }

    public static ShiftWeekSetPayload read(FriendlyByteBuf buf) {
        UUID uuid = new UUID(buf.readLong(), buf.readLong());
        String mode = buf.readUtf();
        int n = buf.readVarInt();
        List<String> days = new ArrayList<>(n);
        for (int i = 0; i < n; i++) days.add(buf.readUtf());
        return new ShiftWeekSetPayload(uuid, mode, days);
    }
}
