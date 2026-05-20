package com.aetherianartificer.townstead.shift.weekplan;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client -> Server: delete a user week plan by id. Built-ins are rejected server-side. */
//? if neoforge {
public record WeekPlanDeletePayload(ResourceLocation id) implements CustomPacketPayload {
//?} else {
/*public record WeekPlanDeletePayload(ResourceLocation id) {
*///?}

    //? if neoforge {
    public static final Type<WeekPlanDeletePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_delete"));

    public static final StreamCodec<FriendlyByteBuf, WeekPlanDeletePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WeekPlanDeletePayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, WeekPlanDeletePayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_delete");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "week_plan_delete");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(id);
    }

    public static WeekPlanDeletePayload read(FriendlyByteBuf buf) {
        return new WeekPlanDeletePayload(buf.readResourceLocation());
    }
}
