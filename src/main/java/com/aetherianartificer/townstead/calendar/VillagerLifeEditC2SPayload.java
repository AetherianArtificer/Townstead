package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> server: explicit Townstead life-editor changes. MCA's Done button
 * may skip its vanilla sync when only our transient keys changed, so life edits
 * need their own save route.
 */
//? if neoforge {
public record VillagerLifeEditC2SPayload(
        UUID villagerUuid, int bioAgeDays, int frozenStageIndex, int birthMonth, int birthDay
) implements CustomPacketPayload {
//?} else {
/*public record VillagerLifeEditC2SPayload(
        UUID villagerUuid, int bioAgeDays, int frozenStageIndex, int birthMonth, int birthDay
) {
*///?}

    public static final int ABSENT = -1;

    public boolean hasBioAge() {
        return bioAgeDays >= 0;
    }

    public boolean hasFrozenStage() {
        return frozenStageIndex >= 0;
    }

    public boolean hasBirthday() {
        return birthMonth > 0 || birthDay > 0;
    }

    //? if neoforge {
    public static final Type<VillagerLifeEditC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_edit_c2s"));

    public static final StreamCodec<FriendlyByteBuf, VillagerLifeEditC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), VillagerLifeEditC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_edit_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "villager_life_edit_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(villagerUuid);
        buf.writeVarInt(bioAgeDays);
        buf.writeVarInt(frozenStageIndex);
        buf.writeVarInt(birthMonth);
        buf.writeVarInt(birthDay);
    }

    public static VillagerLifeEditC2SPayload read(FriendlyByteBuf buf) {
        return new VillagerLifeEditC2SPayload(
                buf.readUUID(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }
}
