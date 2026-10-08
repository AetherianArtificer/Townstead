package com.aetherianartificer.townstead.client.building;

import com.aetherianartificer.townstead.building.pin.BuildingPinProgressS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Client copy of the server-authoritative active building pin. */
public final class BuildingPinClientStore {
    private static BuildingPinProgressS2CPayload current = BuildingPinProgressS2CPayload.unpinned();

    private BuildingPinClientStore() {}

    public static BuildingPinProgressS2CPayload current() {
        return current;
    }

    public static boolean isPinned(String buildingType) {
        return current.active() && current.buildingType().equals(buildingType);
    }

    /** Makes the catalog control respond immediately while the authoritative reply is in flight. */
    public static void optimistic(String buildingType) {
        if (buildingType == null || buildingType.isBlank()) {
            current = BuildingPinProgressS2CPayload.unpinned();
        } else if (!isPinned(buildingType)) {
            current = new BuildingPinProgressS2CPayload(true, buildingType, false, false, java.util.List.of());
        }
    }

    public static void setFrom(BuildingPinProgressS2CPayload payload) {
        if (payload.completed()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable(
                        "townstead.building_pin.completed",
                        Component.translatable("buildingType." + payload.buildingType())), true);
            }
            current = BuildingPinProgressS2CPayload.unpinned();
            return;
        }
        current = payload;
    }
}
