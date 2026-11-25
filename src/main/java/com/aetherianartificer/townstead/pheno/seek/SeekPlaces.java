package com.aetherianartificer.townstead.pheno.seek;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where a seek searches. A place turns the seeker (and whoever started the seek, and the block the action was about)
 * into the regions to search, nearest first; a mod that knows villages, districts or claims registers its own, so a
 * seek can say {@code "within":{"place":"my_mod:village"}} without Pheno knowing what a village is. Pheno's own place,
 * {@code pheno:around}, is a radius around the seeker ({@code "of":"self"}, the default), whoever started it
 * ({@code origin}), the action's block ({@code focus}), or the seeker's {@code job_site} or {@code home}.
 */
public final class SeekPlaces {

    /** Where to search: the seeker, who started it, the block the action was about, and the place's own JSON. */
    @FunctionalInterface
    public interface Place {
        List<AABB> regions(Mob seeker, @Nullable LivingEntity origin, @Nullable BlockPos focus, JsonObject params);
    }

    public static final ResourceLocation AROUND = ResourceLocation.tryParse("pheno:around");
    private static final Map<ResourceLocation, Place> PLACES = new ConcurrentHashMap<>();

    static {
        register(AROUND, SeekPlaces::around);
    }

    private SeekPlaces() {}

    public static void register(ResourceLocation id, Place place) {
        PLACES.put(id, place);
    }

    public static @Nullable Place get(ResourceLocation id) {
        return PLACES.get(id);
    }

    private static List<AABB> around(Mob seeker, @Nullable LivingEntity origin, @Nullable BlockPos focus, JsonObject params) {
        int radius = Mth.clamp(GsonHelper.getAsInt(params, "radius", 16), 1, 128);
        Vec3 center = switch (GsonHelper.getAsString(params, "of", "self")) {
            case "origin" -> origin == null ? null : origin.position();
            case "focus" -> focus == null ? null : Vec3.atCenterOf(focus);
            case "job_site" -> remembered(seeker, MemoryModuleType.JOB_SITE);
            case "home" -> remembered(seeker, MemoryModuleType.HOME);
            default -> seeker.position();
        };
        return center == null ? List.of() : List.of(new AABB(center, center).inflate(radius));
    }

    private static @Nullable Vec3 remembered(Mob seeker, MemoryModuleType<GlobalPos> memory) {
        if (!seeker.getBrain().hasMemoryValue(memory)) return null;
        Optional<GlobalPos> pos = seeker.getBrain().getMemory(memory);
        if (pos.isEmpty() || !pos.get().dimension().equals(seeker.level().dimension())) return null;
        return Vec3.atCenterOf(pos.get().pos());
    }
}
