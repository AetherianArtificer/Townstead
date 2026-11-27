package com.aetherianartificer.townstead.fatigue;

import com.aetherianartificer.townstead.pheno.state.TimedFlags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Bed rest: a villager carrying the timed flag {@code townstead:bed_rest} keeps to bed, at any hour and however
 * rested, until the flag runs out. Anything can set it in JSON with {@code pheno:set_flag}, for as long as a reason
 * holds: an illness, an injury, a story. It uses the same forced rest as an exhausted villager, so the villager goes
 * home to bed, or borrows one, and is not woken by its work hours.
 */
public final class BedRest {

    public static final ResourceLocation FLAG = ResourceLocation.tryParse("townstead:bed_rest");

    private BedRest() {}

    public static boolean active(LivingEntity entity) {
        return entity != null && TimedFlags.has(entity, FLAG);
    }
}
