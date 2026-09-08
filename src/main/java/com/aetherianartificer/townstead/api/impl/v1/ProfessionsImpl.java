package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.ProfessionsApi;
import com.aetherianartificer.townstead.api.v1.model.CareerSnapshot;
import com.aetherianartificer.townstead.profession.career.CareerProfile;
import com.aetherianartificer.townstead.api.v1.model.ProfessionProgressSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProfessionSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ProgressionTrackSnapshot;
import com.aetherianartificer.townstead.api.v1.model.SkillSnapshot;
import com.aetherianartificer.townstead.api.v1.result.SkillResult;
import com.aetherianartificer.townstead.api.v1.result.XpResult;
import com.aetherianartificer.townstead.profession.career.CareerProgression;
import com.aetherianartificer.townstead.profession.career.PlayerCareers;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.profession.skill.LearnedSkills;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionProgressions;
import com.aetherianartificer.townstead.villager.ProfessionXp;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import com.aetherianartificer.townstead.villager.ProgressionSpec;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class ProfessionsImpl implements ProfessionsApi {

    @Override
    public Optional<ProgressionTrackSnapshot> track(String professionId) {
        try {
            ProfessionDef def = resolveDef(professionId);
            return def == null ? Optional.empty() : track(def);
        } catch (Throwable t) {
            ApiSupport.swallow("professions.track", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<ProfessionSnapshot> profession(String professionId) {
        try {
            ProfessionDef def = resolveDef(professionId);
            if (def == null) return Optional.empty();
            List<String> aliases = new ArrayList<>();
            for (ResourceLocation alias : def.aliases()) aliases.add(alias.toString());
            Optional<ProgressionTrackSnapshot> track = track(def);
            List<String> levelNames = new ArrayList<>();
            int maxTier = track.map(ProgressionTrackSnapshot::maxTier).orElse(1);
            for (int tier = 1; tier <= maxTier; tier++) levelNames.add(def.levelName(tier).getString());
            return Optional.of(new ProfessionSnapshot(def.id().toString(), def.displayName(),
                    def.description() == null ? Component.empty() : def.description(), track, def.skills(),
                    aliases, levelNames, def.hidden()));
        } catch (Throwable t) {
            ApiSupport.swallow("professions.profession", t);
            return Optional.empty();
        }
    }

    @Override
    public List<String> professionIds() {
        List<String> out = new ArrayList<>();
        for (ResourceLocation id : ProfessionDefs.all().keySet()) out.add(id.toString());
        return out;
    }

    @Override
    public Optional<ProfessionProgressSnapshot> progress(Entity entity, String professionId) {
        try {
            ProfessionDef def = resolveDef(professionId);
            ProfessionXpStore store = store(entity);
            if (def == null || store == null) return Optional.empty();
            Optional<ProgressionTrackSnapshot> track = track(def);
            if (track.isEmpty()) return Optional.empty();
            ResourceLocation career = ProfessionDefs.canonicalId(def.id());
            ProfessionXp state = store.professionXp(career.toString());
            long day = ApiSupport.gameTime(entity) / 24000L;
            int xpToday = state.xpDay() == day ? Math.max(0, state.xpToday()) : 0;
            return Optional.of(new ProfessionProgressSnapshot(career.toString(),
                    ProfessionProgress.getXp(store, career), ProfessionProgress.getTier(store, career),
                    track.get().maxTier(), xpToday, ProfessionProgress.getXpToNextTier(store, career),
                    ProfessionProgress.getLastTierUpTick(store, career)));
        } catch (Throwable t) {
            ApiSupport.swallow("professions.progress", t);
            return Optional.empty();
        }
    }

    @Override
    public Optional<CareerSnapshot> career(Entity entity) {
        try {
            CareerProfile profile = profile(entity);
            ProfessionXpStore store = store(entity);
            if (profile == null || store == null) return Optional.empty();
            Set<String> history = new java.util.LinkedHashSet<>();
            for (ResourceLocation id : profile.careerHistory()) history.add(id.toString());
            Set<String> acquired = new java.util.LinkedHashSet<>();
            for (ResourceLocation id : profile.acquiredCareers()) acquired.add(id.toString());
            Set<String> discoveries = new java.util.LinkedHashSet<>();
            for (ResourceLocation id : profile.discoveries()) discoveries.add(id.toString());
            List<ProfessionProgressSnapshot> progress = new ArrayList<>();
            Set<ResourceLocation> careers = new java.util.LinkedHashSet<>(profile.careerHistory());
            if (profile.primaryVocation() != null) careers.add(profile.primaryVocation());
            careers.addAll(profile.acquiredCareers());
            for (ResourceLocation career : careers) {
                progress(entity, career.toString()).ifPresent(progress::add);
            }
            return Optional.of(new CareerSnapshot(
                    Optional.ofNullable(profile.primaryVocation()).map(ResourceLocation::toString), history, acquired,
                    discoveries, profile.learnedChoices(), profile.activeBySkillGroup(), profile.lastVocationChangeDay(),
                    progress));
        } catch (Throwable t) {
            ApiSupport.swallow("professions.career", t);
            return Optional.empty();
        }
    }

    static @Nullable CareerProfile profile(@Nullable Entity entity) {
        if (entity instanceof VillagerEntityMCA villager) return TownsteadVillagers.get(villager).professionMemory().careerProfile();
        if (entity instanceof Player player) return PlayerCareers.get(player);
        return null;
    }

    @Override
    public Set<ResourceLocation> skillIds() {
        return Set.copyOf(SkillDefs.all().keySet());
    }

    @Override
    public boolean isKnownSkill(ResourceLocation skillId) {
        return skillId != null && SkillDefs.byId(SkillDefs.canonicalId(skillId)) != null;
    }

    @Override
    public Optional<SkillSnapshot> skill(ResourceLocation skillId) {
        try {
            if (skillId == null) return Optional.empty();
            SkillDef def = SkillDefs.byId(SkillDefs.canonicalId(skillId));
            if (def == null) return Optional.empty();
            return Optional.of(new SkillSnapshot(def.id(), def.displayName(),
                    def.description() == null ? Component.empty() : def.description(), def.profession(), def.tier(),
                    def.cost(), def.requires(), def.exclusiveWith(), Optional.ofNullable(def.skillGroup())));
        } catch (Throwable t) {
            ApiSupport.swallow("professions.skill", t);
            return Optional.empty();
        }
    }

    @Override
    public Set<ResourceLocation> learnedSkills(Entity entity) {
        try {
            return entity instanceof LivingEntity living ? Set.copyOf(LearnedSkills.learned(living)) : Set.of();
        } catch (Throwable t) {
            ApiSupport.swallow("professions.learnedSkills", t);
            return Set.of();
        }
    }

    @Override
    public boolean hasSkill(Entity entity, ResourceLocation skillId) {
        try {
            return entity instanceof LivingEntity living && skillId != null && LearnedSkills.has(living, skillId);
        } catch (Throwable t) {
            ApiSupport.swallow("professions.hasSkill", t);
            return false;
        }
    }

    @Override
    public XpResult awardXp(Entity entity, String professionId, int amount, boolean respectDailyCap,
                            ResourceLocation source) {
        try {
            if (!ApiSupport.writesAllowed(source)) {
                return XpResult.failed(XpResult.Status.DISABLED, professionId, amount, "writes from " + source + " are disabled");
            }
            if (amount <= 0) return XpResult.failed(XpResult.Status.INVALID, professionId, amount, "amount must be positive");
            ProfessionDef def = resolveDef(professionId);
            if (def == null || track(def).isEmpty()) {
                return XpResult.failed(XpResult.Status.NO_PROGRESSION, professionId, amount, "no progression configured");
            }
            ProfessionXpStore store = store(entity);
            if (store == null || !(entity instanceof LivingEntity worker)) {
                return XpResult.failed(XpResult.Status.NOT_A_VILLAGER, professionId, amount, "no career store for this entity");
            }
            ResourceLocation career = ProfessionDefs.canonicalId(def.id());
            ProgressionSpec spec = ProfessionProgressions.spec(career);
            int xpBefore = ProfessionProgress.getXp(store, career);
            int tierBefore = ProfessionProgress.getTier(store, career);
            if (xpBefore >= spec.maxXp()) {
                return new XpResult(XpResult.Status.AT_MAX, career.toString(), amount, 0, xpBefore, xpBefore,
                        tierBefore, tierBefore, "at the track's ceiling");
            }
            ProfessionProgress.GainResult gain = CareerProgression.award(worker, career, amount,
                    ApiSupport.gameTime(entity), respectDailyCap);
            int xpAfter = ProfessionProgress.getXp(store, career);
            if (gain.appliedXp() <= 0) {
                return new XpResult(XpResult.Status.DAILY_CAP, career.toString(), amount, 0, xpBefore, xpAfter,
                        gain.tierBefore(), gain.tierAfter(), "daily allowance spent");
            }
            if (entity instanceof VillagerEntityMCA villager) TownsteadVillagers.flush(villager);
            return new XpResult(XpResult.Status.APPLIED, career.toString(), amount, gain.appliedXp(), xpBefore,
                    xpAfter, gain.tierBefore(), gain.tierAfter(), "");
        } catch (Throwable t) {
            ApiSupport.swallow("professions.awardXp", t);
            return XpResult.failed(XpResult.Status.ERROR, professionId, amount, t.toString());
        }
    }

    @Override
    public SkillResult learnSkill(Entity entity, ResourceLocation skillId, boolean force, ResourceLocation source) {
        try {
            if (!ApiSupport.writesAllowed(source)) {
                return SkillResult.failed(SkillResult.Status.DISABLED, skillId, "writes from " + source + " are disabled");
            }
            if (!isKnownSkill(skillId)) return SkillResult.failed(SkillResult.Status.UNKNOWN_SKILL, skillId, "unknown skill");
            if (!(entity instanceof LivingEntity living)) {
                return SkillResult.failed(SkillResult.Status.NOT_A_VILLAGER, skillId, "not a living entity");
            }
            ResourceLocation canonical = SkillDefs.canonicalId(skillId);
            if (LearnedSkills.has(living, canonical)) {
                return new SkillResult(SkillResult.Status.ALREADY, canonical, Set.of(), "already learned");
            }
            LearnedSkills.Result result = force ? LearnedSkills.forceLearn(living, canonical)
                    : LearnedSkills.learn(living, canonical);
            if (result.ok()) {
                if (entity instanceof VillagerEntityMCA villager) TownsteadVillagers.flush(villager);
                return new SkillResult(SkillResult.Status.APPLIED, canonical, Set.of(), "");
            }
            String error = result.error() == null ? "" : result.error();
            SkillResult.Status status = error.contains("prerequisite") ? SkillResult.Status.PREREQUISITES_UNMET
                    : error.contains("unknown") ? SkillResult.Status.UNKNOWN_SKILL : SkillResult.Status.ERROR;
            return SkillResult.failed(status, canonical, error);
        } catch (Throwable t) {
            ApiSupport.swallow("professions.learnSkill", t);
            return SkillResult.failed(SkillResult.Status.ERROR, skillId, t.toString());
        }
    }

    @Override
    public SkillResult forgetSkill(Entity entity, ResourceLocation skillId, boolean force, ResourceLocation source) {
        try {
            if (!ApiSupport.writesAllowed(source)) {
                return SkillResult.failed(SkillResult.Status.DISABLED, skillId, "writes from " + source + " are disabled");
            }
            if (!isKnownSkill(skillId)) return SkillResult.failed(SkillResult.Status.UNKNOWN_SKILL, skillId, "unknown skill");
            if (!(entity instanceof LivingEntity living)) {
                return SkillResult.failed(SkillResult.Status.NOT_A_VILLAGER, skillId, "not a living entity");
            }
            ResourceLocation canonical = SkillDefs.canonicalId(skillId);
            if (!LearnedSkills.has(living, canonical)) {
                return new SkillResult(SkillResult.Status.ALREADY, canonical, Set.of(), "not learned");
            }
            LearnedSkills.ForgetResult result = force ? LearnedSkills.forceForget(living, canonical)
                    : LearnedSkills.forget(living, canonical);
            if (result.ok()) {
                if (entity instanceof VillagerEntityMCA villager) TownsteadVillagers.flush(villager);
                return new SkillResult(SkillResult.Status.APPLIED, canonical, result.removed(), "");
            }
            String error = result.error() == null ? "" : result.error();
            SkillResult.Status status = error.contains("retraining") ? SkillResult.Status.LOCKED : SkillResult.Status.ERROR;
            return SkillResult.failed(status, canonical, error);
        } catch (Throwable t) {
            ApiSupport.swallow("professions.forgetSkill", t);
            return SkillResult.failed(SkillResult.Status.ERROR, skillId, t.toString());
        }
    }

    // ---- helpers ----

    /** Full id, alias, or bare legacy path, the way {@code ProfessionProgressions} resolves. */
    static @Nullable ProfessionDef resolveDef(@Nullable String professionId) {
        if (professionId == null || professionId.isBlank()) return null;
        ResourceLocation parsed = ResourceLocation.tryParse(professionId);
        if (parsed != null) {
            ProfessionDef direct = ProfessionDefs.byId(parsed);
            if (direct != null) return direct;
        }
        String wanted = professionId.toLowerCase(Locale.ROOT);
        for (Map.Entry<ResourceLocation, ProfessionDef> entry : ProfessionDefs.all().entrySet()) {
            ResourceLocation id = entry.getKey();
            if (id.toString().equals(wanted) || id.getPath().equals(wanted)) return entry.getValue();
        }
        return null;
    }

    /** Empty when the def's track is inert: no XP can ever land on it. */
    static Optional<ProgressionTrackSnapshot> track(ProfessionDef def) {
        ProgressionSpec spec = ProfessionProgressions.spec(def.id());
        if (spec.maxXp() <= 0 || spec.dailyXpCap() <= 0) return Optional.empty();
        List<Integer> thresholds = new ArrayList<>();
        for (int threshold : spec.tierThresholds()) thresholds.add(threshold);
        return Optional.of(new ProgressionTrackSnapshot(def.id().toString(), thresholds, spec.maxTier(),
                spec.maxXp(), spec.dailyXpCap()));
    }

    static @Nullable ProfessionXpStore store(@Nullable Entity entity) {
        if (entity instanceof VillagerEntityMCA villager) return TownsteadVillagers.get(villager).professionMemory();
        if (entity instanceof Player player) return PlayerCareers.xpStore(player);
        return null;
    }
}
