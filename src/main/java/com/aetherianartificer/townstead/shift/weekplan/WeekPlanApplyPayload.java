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
import java.util.UUID;

/** Client -> Server: apply a week plan to a list of villager UUIDs. */
//? if neoforge {
public record WeekPlanApplyPayload(ResourceLocation planId, List<UUID> villagerUuids)
        implements CustomPacketPayload {
//?} else {
/*public record WeekPlanApplyPayload(ResourceLocation planId, List<UUID> villagerUuids) {
*///?}

    //? if neoforge {
    public static final Type<WeekPlanApplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_apply"));

    public static final StreamCodec<FriendlyByteBuf, WeekPlanApplyPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public WeekPlanApplyPayload decode(FriendlyByteBuf buf) { return read(buf); }

                @Override
                public void encode(FriendlyByteBuf buf, WeekPlanApplyPayload payload) { payload.write(buf); }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "week_plan_apply");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "week_plan_apply");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeResourceLocation(planId);
        buf.writeVarInt(villagerUuids.size());
        for (UUID uuid : villagerUuids) {
            buf.writeLong(uuid.getMostSignificantBits());
            buf.writeLong(uuid.getLeastSignificantBits());
        }
    }

    public static WeekPlanApplyPayload read(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readResourceLocation();
        int n = buf.readVarInt();
        List<UUID> uuids = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            uuids.add(new UUID(buf.readLong(), buf.readLong()));
        }
        return new WeekPlanApplyPayload(id, uuids);
    }
}
