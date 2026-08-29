package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.ProfessionPaths;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import org.jetbrains.annotations.Nullable;

/** Semantic Career and Path matching across native and compatibility profession identities. */
public final class ProfessionIdentity {

    private ProfessionIdentity() {}

    @Nullable
    public static ResourceLocation rawId(LivingEntity entity) {
        if (!(entity instanceof VillagerDataHolder holder)) return null;
        return BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                holder.getVillagerData().getProfession());
    }

    /**
     * The entity's effective Path in {@code profession}: first an implied compatibility Path,
     * then the ordinary path derived from learned skills.
     */
    @Nullable
    public static ProfessionPaths.Path path(LivingEntity entity, ResourceLocation profession) {
        ResourceLocation raw = rawId(entity);
        if (raw != null && profession.equals(ProfessionDefs.canonicalId(raw))) {
            String implied = ProfessionDefs.pathId(raw);
            if (implied != null) return ProfessionPaths.byId(profession, implied);
        }
        return ProfessionPaths.committedPath(profession,
                skill -> LearnedSkills.has(entity, skill));
    }

    /**
     * Matches an authored profession reference. Root aliases match the entire Career; a
     * Path-specific alias matches only an entity on that Path.
     */
    public static boolean matches(LivingEntity entity, ResourceLocation authored) {
        ResourceLocation actual = rawId(entity);
        return actual != null && matches(entity, actual, authored);
    }

    public static boolean matches(@Nullable LivingEntity entity, ResourceLocation actual,
                                  ResourceLocation authored) {
        if (actual == null || authored == null) return false;
        ResourceLocation actualCareer = ProfessionDefs.canonicalId(actual);
        ResourceLocation authoredCareer = ProfessionDefs.canonicalId(authored);
        if (!authoredCareer.equals(actualCareer)) return false;
        String requiredPath = ProfessionDefs.pathId(authored);
        if (requiredPath == null) return true;
        if (requiredPath.equals(ProfessionDefs.pathId(actual))) return true;
        if (entity == null) return false;
        ProfessionPaths.Path path = path(entity, actualCareer);
        return path != null && requiredPath.equals(path.id());
    }
}
