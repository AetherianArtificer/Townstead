package com.aetherianartificer.townstead.shift.weekplan;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> Server: create or update a user week plan. Pass empty id ("") to
 * request the server assign one (typical for first save).
 */
//? if neoforge {
public record WeekPlanSavePayload(String id, String name, List<String> days) implements CustomPacketPayload {
//?} else {
/*public record WeekPlanSavePayload(String id, String name, List<String> days) {
*///?}

    //? if neoforge {
    public static final Type<WeekPlanSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_save"));

    public static final StreamCodec<FriendlyByteBuf, WeekPlanSavePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WeekPlanSavePayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, WeekPlanSavePayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_save");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "week_plan_save");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id == null ? "" : id);
        buf.writeUtf(name == null ? "" : name);
        buf.writeVarInt(days == null ? 0 : days.size());
        if (days != null) {
            for (String d : days) buf.writeUtf(d == null ? "" : d);
        }
    }

    public static WeekPlanSavePayload read(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String name = buf.readUtf();
        int n = buf.readVarInt();
        List<String> days = new ArrayList<>(n);
        for (int i = 0; i < n; i++) days.add(buf.readUtf());
        return new WeekPlanSavePayload(id, name, days);
    }
}
