package com.aetherianartificer.townstead.client.species;

import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Where a worn curio that nothing else draws sits on the body, by Curios slot type. Offsets are model
 * pixels from the host bone's origin, rotations in degrees, plus the uniform scale of the drawn item.
 * Only slots with a natural place on the body get a seat; rings, charms and the like stay unseen, as
 * they do on a player.
 */
public record CurioItemSeat(String channel, float[] offset, float[] rotation, float scale) {

    private static final Map<String, CurioItemSeat> BY_SLOT = Map.of(
            "back", new CurioItemSeat("body", new float[]{0f, 6f, 4.5f}, new float[]{0f, 180f, 0f}, 0.55f),
            "head", new CurioItemSeat("head", new float[]{0f, -9f, 0f}, new float[]{0f, 0f, 0f}, 0.55f),
            "necklace", new CurioItemSeat("body", new float[]{0f, 4f, -2.5f}, new float[]{0f, 0f, 0f}, 0.3f),
            "body", new CurioItemSeat("body", new float[]{0f, 6f, -2.5f}, new float[]{0f, 0f, 0f}, 0.35f),
            "belt", new CurioItemSeat("body", new float[]{0f, 10.5f, -2.5f}, new float[]{0f, 0f, 0f}, 0.3f)
    );

    @Nullable
    public static CurioItemSeat forSlot(String slotId) {
        return BY_SLOT.get(slotId);
    }
}
