package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Server-authoritative acquisition of gated careers. Careers are flat: any def may declare
 * pheno requirements and acquisition routes, and it becomes part of a character's history when
 * those requirements pass through one of its declared routes. A def without routes is simply
 * practiced.
 */
public final class CareerAcquisitions {
    private CareerAcquisitions() {}

    public record Result(boolean acquired, String reason) {}

    public static Result acquire(LivingEntity entity, ResourceLocation careerId, String route) {
        ProfessionDef def = ProfessionDefs.byId(careerId);
        if (def == null) return new Result(false, "unknown career");
        if (def.acquisitionRoutes().isEmpty()) return new Result(false, "career is practiced, not acquired");
        if (!def.acquisitionRoutes().contains(route)) return new Result(false, "invalid acquisition route");
        CareerProfile profile = CareerProfiles.of(entity);
        if (profile == null) return new Result(false, "unsupported character");
        if (!def.eligible(entity)) return new Result(false, "requirements not met");

        if (entity instanceof VillagerEntityMCA villager) {
            boolean changed = profile.discover(careerId) | profile.acquireCareer(careerId);
            if (changed) TownsteadVillagers.get(villager).professionMemory().markCareerDirty();
            return new Result(changed, changed ? "acquired" : "already acquired");
        }
        if (entity instanceof Player player) {
            final boolean[] changed = {false};
            PlayerCareers.mutate(player, stored -> {
                changed[0] = stored.discover(careerId) | stored.acquireCareer(careerId);
            });
            return new Result(changed[0], changed[0] ? "acquired" : "already acquired");
        }
        return new Result(false, "unsupported character");
    }

    /**
     * Acquire every newly eligible gated career that permits this route. Flat model: any def
     * with acquisition routes qualifies; its own requirements decide. The {@code affected}
     * careers are accepted for call-site compatibility but no longer filter — requirements may
     * reference any career or chronicle state, so relatedness cannot be derived from a graph.
     * (At hundreds of defs, index requirement-referenced careers instead of sweeping.)
     */
    public static List<ResourceLocation> acquireEligible(LivingEntity entity, String route,
                                                         Collection<ResourceLocation> affected) {
        CareerProfile profile = CareerProfiles.of(entity);
        if (profile == null) return List.of();
        List<ResourceLocation> acquired = new ArrayList<>();
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (profile.acquiredCareers().contains(def.id())
                    || !def.acquisitionRoutes().contains(route)) continue;
            Result result = acquire(entity, def.id(), route);
            if (result.acquired()) acquired.add(def.id());
        }
        return List.copyOf(acquired);
    }
}
