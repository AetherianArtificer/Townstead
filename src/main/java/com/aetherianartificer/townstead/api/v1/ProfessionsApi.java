package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.model.ProfessionProgressSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProfessionSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProgressionTrackSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SkillSnapshot;
import com.aetherianartificer.townstead.api.v1.result.SkillResult;
import com.aetherianartificer.townstead.api.v1.result.XpResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Careers, progression tracks, skills, and the mutations that move them. */
public interface ProfessionsApi {

    /**
     * The progression behind a profession, or empty when none is configured. A profession with
     * no track never gains XP and never tiers up; that is the case this method exists to name.
     * Both {@code minecraft:farmer} and {@code farmer} resolve.
     */
    Optional<ProgressionTrackSnapshot> track(String professionId);

    Optional<ProfessionSnapshot> profession(String professionId);

    /** Every registered profession's canonical id. */
    List<String> professionIds();

    /** Where an entity stands in one profession. Empty when the profession has no track. */
    Optional<ProfessionProgressSnapshot> progress(Entity entity, String professionId);

    Set<ResourceLocation> skillIds();

    boolean isKnownSkill(ResourceLocation skillId);

    Optional<SkillSnapshot> skill(ResourceLocation skillId);

    Set<ResourceLocation> learnedSkills(Entity entity);

    boolean hasSkill(Entity entity, ResourceLocation skillId);

    /**
     * Awards profession XP, built-in or data-driven alike, with the same tier-up bookkeeping work
     * itself gets. {@code respectDailyCap} false ignores the track's daily allowance.
     */
    XpResult awardXp(Entity entity, String professionId, int amount, boolean respectDailyCap,
                     ResourceLocation source);

    /** Teaches a skill; {@code force} skips prerequisite checks. Unknown ids are refused. */
    SkillResult learnSkill(Entity entity, ResourceLocation skillId, boolean force, ResourceLocation source);

    /** Removes a skill and everything that depended on it; {@code force} ignores retraining locks. */
    SkillResult forgetSkill(Entity entity, ResourceLocation skillId, boolean force, ResourceLocation source);
}
