package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectorType;
import com.aetherianartificer.townstead.pheno.selector.spatial.RayCast;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The block form of {@code pheno:ray}: the block a ray strikes first, so a block action with
 * {@code on: { type: pheno:ray, ... }} acts on the block in the line of fire (Apugli
 * {@code raycast_between} via {@code toward}). Same id, resolved by domain like the rest of the
 * grant-vs-test pairs.
 */
public final class RayBlockSelectorType implements BlockSelectorType {

    public static final String KEY = "pheno:ray";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockSelector parse(JsonObject json) {
        RayCast ray = RayCast.parse(json);
        return ctx -> {
            var hit = ray.blockHit(ctx);
            return hit == null ? List.of() : List.of(hit.getBlockPos());
        };
    }
}
