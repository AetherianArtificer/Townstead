package com.aetherianartificer.townstead.building.pin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

import java.util.ArrayList;
import java.util.List;

/** Authoritative progress for the single building recipe pinned by this player. */
//? if neoforge {
public record BuildingPinProgressS2CPayload(boolean active, String buildingType, boolean insideBuilding,
                                            boolean completed, List<Row> rows) implements CustomPacketPayload {
//?} else {
/*public record BuildingPinProgressS2CPayload(boolean active, String buildingType, boolean insideBuilding,
                                            boolean completed, List<Row> rows) {
*///?}
    public record Row(ResourceLocation requirement, int required, int inventory, int placed) {}

    public BuildingPinProgressS2CPayload {
        buildingType = buildingType == null ? "" : buildingType;
        rows = rows == null ? List.of() : List.copyOf(rows);
    }

    public static BuildingPinProgressS2CPayload unpinned() {
        return new BuildingPinProgressS2CPayload(false, "", false, false, List.of());
    }

    public static BuildingPinProgressS2CPayload completed(String buildingType) {
        return new BuildingPinProgressS2CPayload(false, buildingType, false, true, List.of());
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
        buf.writeUtf(buildingType, 256);
        buf.writeBoolean(insideBuilding);
        buf.writeBoolean(completed);
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            buf.writeResourceLocation(row.requirement());
            buf.writeVarInt(row.required());
            buf.writeVarInt(row.inventory());
            buf.writeVarInt(row.placed());
        }
    }

    public static BuildingPinProgressS2CPayload read(FriendlyByteBuf buf) {
        boolean active = buf.readBoolean();
        String buildingType = buf.readUtf(256);
        boolean insideBuilding = buf.readBoolean();
        boolean completed = buf.readBoolean();
        int size = Math.min(512, buf.readVarInt());
        List<Row> rows = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rows.add(new Row(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new BuildingPinProgressS2CPayload(active, buildingType, insideBuilding, completed, rows);
    }

    //? if neoforge {
    public static final Type<BuildingPinProgressS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "building_pin_progress"));
    public static final StreamCodec<FriendlyByteBuf, BuildingPinProgressS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), BuildingPinProgressS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}
}
