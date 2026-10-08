package com.aetherianartificer.townstead.building.pin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** Selects one catalog building to track, or clears the selection with an empty id. */
//? if neoforge {
public record BuildingPinSetC2SPayload(String buildingType) implements CustomPacketPayload {
//?} else {
/*public record BuildingPinSetC2SPayload(String buildingType) {
*///?}
    public BuildingPinSetC2SPayload {
        buildingType = buildingType == null ? "" : buildingType;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(buildingType, 256);
    }

    public static BuildingPinSetC2SPayload read(FriendlyByteBuf buf) {
        return new BuildingPinSetC2SPayload(buf.readUtf(256));
    }

    //? if neoforge {
    public static final Type<BuildingPinSetC2SPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "building_pin_set"));
    public static final StreamCodec<FriendlyByteBuf, BuildingPinSetC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), BuildingPinSetC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}
}
