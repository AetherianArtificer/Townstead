package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-client snapshot of one villager's birth date. Sent when a player
 * starts tracking the villager and again when {@link VillagerLifeStamper}
 * fabricates a DOB.
 *
 * Like {@link CalendarSyncPayload}, the month name travels as a
 * (translate key, fallback) pair for per-client locale resolution.
 */
public record VillagerLifeSyncPayload(
        int entityId,
        int birthYear,
        int birthMonthIndex,
        int birthDayOfMonth,
        String birthMonthKey,
        String birthMonthFallback,
        int ageYears,
        boolean stamped
//? if neoforge {
) implements CustomPacketPayload {
//?} else {
/*) {
*///?}

    //? if neoforge {
    public static final Type<VillagerLifeSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_sync"));

    public static final StreamCodec<FriendlyByteBuf, VillagerLifeSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.entityId());
                buf.writeVarInt(p.birthYear());
                buf.writeVarInt(p.birthMonthIndex());
                buf.writeVarInt(p.birthDayOfMonth());
                buf.writeUtf(p.birthMonthKey());
                buf.writeUtf(p.birthMonthFallback());
                buf.writeVarInt(p.ageYears());
                buf.writeBoolean(p.stamped());
            },
            buf -> new VillagerLifeSyncPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readUtf(),
                    buf.readUtf(),
                    buf.readVarInt(),
                    buf.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "villager_life_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(birthYear);
        buf.writeVarInt(birthMonthIndex);
        buf.writeVarInt(birthDayOfMonth);
        buf.writeUtf(birthMonthKey);
        buf.writeUtf(birthMonthFallback);
        buf.writeVarInt(ageYears);
        buf.writeBoolean(stamped);
    }

    public static VillagerLifeSyncPayload read(FriendlyByteBuf buf) {
        return new VillagerLifeSyncPayload(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readVarInt(),
                buf.readBoolean()
        );
    }
    *///?}
}
