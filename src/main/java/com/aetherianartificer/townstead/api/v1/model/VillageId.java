package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

/**
 * A village, unique across the server. MCA allocates village ids per dimension, so the id alone
 * is not enough; this is the same shape MCA: Reputation's and MCA: Crime's community keys use.
 */
public record VillageId(ResourceLocation dimension, int villageId) {

    /** The {@code dimension/id} form, for example {@code minecraft:overworld/3}. */
    public String asString() {
        return dimension + "/" + villageId;
    }
}
