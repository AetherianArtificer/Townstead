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

/** Server -> Client: full snapshot of all known week plans. */
//? if neoforge {
public record WeekPlanSyncPayload(List<WeekPlan> plans) implements CustomPacketPayload {
//?} else {
/*public record WeekPlanSyncPayload(List<WeekPlan> plans) {
*///?}

    //? if neoforge {
    public static final Type<WeekPlanSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_sync"));

    public static final StreamCodec<FriendlyByteBuf, WeekPlanSyncPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WeekPlanSyncPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, WeekPlanSyncPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "week_plan_sync");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(plans.size());
        for (WeekPlan p : plans) {
            buf.writeResourceLocation(p.id());
            buf.writeUtf(p.displayName());
            List<String> days = p.dayTemplates();
            buf.writeVarInt(days.size());
            for (String d : days) buf.writeUtf(d == null ? "" : d);
            buf.writeBoolean(p.builtIn());
        }
    }

    public static WeekPlanSyncPayload read(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<WeekPlan> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String name = buf.readUtf();
            int dn = buf.readVarInt();
            List<String> days = new ArrayList<>(dn);
            for (int j = 0; j < dn; j++) days.add(buf.readUtf());
            boolean builtIn = buf.readBoolean();
            try {
                out.add(new WeekPlan(id, name, days, builtIn));
            } catch (IllegalArgumentException ignored) {}
        }
        return new WeekPlanSyncPayload(out);
    }
}
