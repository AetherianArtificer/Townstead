package com.aetherianartificer.townstead.pheno.power;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The source-agnostic facade every behavior applier reads through. Sources register at
 * startup (genetics now, professions later); {@link #active} unions them for an entity.
 * Today only {@code GenePowerSource} is registered, so behavior is identical to reading
 * expressed genes directly, but the seam means a new source needs no applier changes.
 */
public final class Powers {

    private static final List<PowerSource> SOURCES = new CopyOnWriteArrayList<>();

    private Powers() {}

    public static void register(PowerSource source) {
        SOURCES.add(source);
    }

    /** Every power granted to {@code entity} across all sources. */
    public static List<Power> active(LivingEntity entity) {
        List<Power> out = new ArrayList<>();
        for (PowerSource source : SOURCES) source.collect(entity, out);
        return out;
    }

    /** The components of the given type granted to {@code entity} (id-less convenience). */
    public static <T> List<T> componentsOf(LivingEntity entity, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Power power : active(entity)) {
            if (type.isInstance(power.component())) out.add(type.cast(power.component()));
        }
        return out;
    }
}
