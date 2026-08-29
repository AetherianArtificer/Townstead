package com.aetherianartificer.townstead.storage;

import net.minecraft.world.Container;

/** Chooses one canonical API view for a physical inventory block. */
public final class StorageInventoryPolicy {
    private StorageInventoryPolicy() {}

    /** A native Container is authoritative; its capability commonly wraps the exact same slots. */
    public static boolean useItemHandlerView(Object inventoryHost) {
        return useItemHandlerView(inventoryHost instanceof Container);
    }

    /** Pure form used by policy tests without bootstrapping Minecraft registries. */
    static boolean useItemHandlerView(boolean nativeContainer) {
        return !nativeContainer;
    }
}
