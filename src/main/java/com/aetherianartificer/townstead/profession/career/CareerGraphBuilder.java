package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.RequirementHint;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionProgressions;
import com.aetherianartificer.townstead.villager.ProfessionXp;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders a character's career registry record server-side: node states, progress, routes,
 * evidence, and chronicle moments resolved, localized, and masked before anything reaches
 * the wire. Skill nodes appear only under acquired careers; a hidden unmet specialization is
 * sent as a masked silhouette so discovering it stays gameplay.
 */
public final class CareerGraphBuilder {
    private CareerGraphBuilder() {}

    public static List<CareerGraphS2CPayload.Node> build(MinecraftServer server, LivingEntity entity,
                                                         Map<String, List<String>> momentsByCareer) {
        List<CareerGraphS2CPayload.Node> nodes = new ArrayList<>();
        CareerProfile profile = CareerProfiles.of(entity);
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (profile == null || store == null) return nodes;
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (def.isRoot()) {
                appendCareer(nodes, server, entity, profile, store, def, def.id(), momentsByCareer);
            }
        }
        return nodes;
    }

    private static void appendCareer(List<CareerGraphS2CPayload.Node> nodes, MinecraftServer server,
                                     LivingEntity entity, CareerProfile profile,
                                     ProfessionXpStore store, ProfessionDef def,
                                     ResourceLocation rootId,
                                     Map<String, List<String>> momentsByCareer) {
        if (nodes.size() > 512) return;
        ResourceLocation careerId = def.id();
        int xp = ProfessionProgress.getXp(store, careerId);
        int currentTier = ProfessionProgress.getTier(store, careerId);
        boolean primary = careerId.equals(profile.primaryVocation());
        boolean acquired = def.isRoot() ? (xp > 0 || primary) : profile.acquiredCareers().contains(careerId);
        boolean eligible = !acquired && def.eligible(entity);

        final byte state;
        if (acquired) {
            state = CareerGraphS2CPayload.STATE_ACQUIRED;
        } else if (def.isRoot() || eligible) {
            state = CareerGraphS2CPayload.STATE_READY;
        } else if (def.hidden()) {
            state = CareerGraphS2CPayload.STATE_HIDDEN;
        } else {
            state = CareerGraphS2CPayload.STATE_LOCKED;
        }

        boolean masked = state == CareerGraphS2CPayload.STATE_HIDDEN;
        List<CareerGraphS2CPayload.Evidence> evidence = masked
                ? List.of() : evidenceFor(server, entity, profile, def, acquired);
        String parentId = def.isRoot() || def.parents().isEmpty() ? "" : def.parents().get(0).toString();

        // Today's allowance: xpToday is only meaningful if it was earned today.
        ProfessionXp xpState = store.professionXp(ProfessionDefs.canonicalId(careerId).toString());
        long today = entity.level().getDayTime() / 24000L;
        int xpToday = xpState.xpDay() == today ? Math.max(0, xpState.xpToday()) : 0;
        int dailyCap = ProfessionProgressions.spec(careerId).dailyXpCap();
        int maxTier = ProfessionProgressions.spec(careerId).maxTier();

        String routesLine = masked || acquired || def.isRoot() || def.acquisitionRoutes().isEmpty()
                ? "" : routesLine(def.acquisitionRoutes());
        List<String> moments = masked ? List.of()
                : momentsByCareer.getOrDefault(careerId.toString(), List.of());

        nodes.add(new CareerGraphS2CPayload.Node(
                careerId.toString(), rootId.toString(), parentId,
                def.isRoot() ? CareerGraphS2CPayload.KIND_ROOT : CareerGraphS2CPayload.KIND_ADVANCED,
                state,
                masked ? "" : def.displayName().getString(),
                masked || def.description() == null ? "" : def.description().getString(),
                masked || def.icon() == null ? "" : def.icon().toString(),
                currentTier, maxTier, xp,
                ProfessionProgress.getXpToNextTier(store, careerId), xpToday, dailyCap,
                primary, false, profile.trackedCareers().contains(careerId),
                routesLine, "", evidence, moments,
                masked ? "" : def.levelName(currentTier).getString(),
                acquired ? SkillPoints.available(entity, def) : 0));

        if (acquired) {
            for (ResourceLocation choice : def.skills()) {
                SkillDef skill = SkillDefs.byId(choice);
                if (skill == null) continue;
                boolean learned = com.aetherianartificer.townstead.profession.skill.LearnedSkills
                        .has(entity, choice);
                boolean equipped = CareerChoices.isActive(entity, choice);
                boolean learnable = !learned && SkillPoints.canLearn(entity, def, skill);
                byte skillState = learned ? CareerGraphS2CPayload.STATE_ACQUIRED
                        : learnable ? CareerGraphS2CPayload.STATE_READY
                        : CareerGraphS2CPayload.STATE_LOCKED;
                nodes.add(new CareerGraphS2CPayload.Node(
                        choice.toString(), rootId.toString(), careerId.toString(),
                        CareerGraphS2CPayload.KIND_SKILL,
                        skillState,
                        skill.displayName().getString(),
                        skill.description() == null ? "" : skill.description().getString(),
                        skill.icon() == null ? "" : skill.icon().toString(),
                        skill.tier(), 0, 0, 0, 0, 0, false, equipped, false,
                        "", equipped || (!learned && !learnable)
                                ? "" : replacedSkillName(entity, def, skill),
                        List.of(), List.of(),
                        def.levelName(skill.tier()).getString(), Math.max(0, skill.cost())));
            }
        }

        for (ProfessionDef child : ProfessionDefs.all().values()) {
            if (child.parents().contains(careerId)) {
                appendCareer(nodes, server, entity, profile, store, child, rootId, momentsByCareer);
            }
        }
    }

    /** The currently equipped sibling this skill would replace within its choice group. */
    private static String replacedSkillName(LivingEntity entity, ProfessionDef career, SkillDef skill) {
        if (skill.skillGroup() == null) return "";
        for (ResourceLocation otherId : career.skills()) {
            if (otherId.equals(skill.id())) continue;
            SkillDef other = SkillDefs.byId(otherId);
            if (other == null || !skill.skillGroup().equals(other.skillGroup())) continue;
            if (CareerChoices.isActive(entity, otherId)) return other.displayName().getString();
        }
        return "";
    }

    private static String routesLine(List<String> routes) {
        List<String> parts = new ArrayList<>(routes.size());
        for (String route : routes) {
            parts.add(localizeOr(("townstead.career.route." + route), prettify(route)));
        }
        return String.join(" · ", parts);
    }

    private static List<CareerGraphS2CPayload.Evidence> evidenceFor(
            MinecraftServer server, LivingEntity entity, CareerProfile profile,
            ProfessionDef def, boolean acquired) {
        List<CareerGraphS2CPayload.Evidence> evidence = new ArrayList<>();
        if (acquired) {
            for (String counter : def.historyCounters()) {
                evidence.add(new CareerGraphS2CPayload.Evidence(counterLabel(counter),
                        Chronicles.count(server, entity.getUUID(), counter), 0, true));
            }
            return evidence;
        }
        for (RequirementHint hint : def.requirementHints()) {
            switch (hint.kind()) {
                case RequirementHint.KIND_CHRONICLE_COUNT -> {
                    int current = Chronicles.count(server, entity.getUUID(), hint.key());
                    evidence.add(new CareerGraphS2CPayload.Evidence(counterLabel(hint.key()),
                            current, hint.target(), current >= hint.target()));
                }
                case RequirementHint.KIND_CAREER_XP -> {
                    ResourceLocation careerId = ResourceLocation.tryParse(Careers.resolve(hint.key()));
                    ProfessionDef named = careerId == null ? null : ProfessionDefs.byId(careerId);
                    String label = (named != null ? named.displayName().getString() : prettify(hint.key()))
                            + " XP";
                    int current = profile.professionXp(Careers.resolve(hint.key())).xp();
                    evidence.add(new CareerGraphS2CPayload.Evidence(label,
                            current, hint.target(), current >= hint.target()));
                }
                default -> evidence.add(new CareerGraphS2CPayload.Evidence(
                        prettify(hint.key()), 0, 0, false));
            }
        }
        return evidence;
    }

    /** "townstead:cooked" localizes via {@code townstead.counter.cooked}, else reads as "cooked". */
    private static String counterLabel(String key) {
        int colon = key.indexOf(':');
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        return localizeOr("townstead.counter." + path, prettify(key));
    }

    private static String localizeOr(String langKey, String fallback) {
        return Language.getInstance().has(langKey)
                ? Component.translatable(langKey).getString()
                : fallback;
    }

    private static String prettify(String key) {
        int colon = key.indexOf(':');
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        return path.replace('_', ' ');
    }
}
