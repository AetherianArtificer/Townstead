package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.chronicle.Chronicles;
import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import com.aetherianartificer.townstead.profession.def.RequirementHint;
import com.aetherianartificer.townstead.profession.def.SkillDef;
import com.aetherianartificer.townstead.profession.def.SkillDefs;
import com.aetherianartificer.townstead.villager.ProfessionProgress;
import com.aetherianartificer.townstead.villager.ProfessionXpStore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders a character's career constellation server-side: node states, progress, and
 * evidence resolved and masked per player before anything reaches the wire. Skill nodes
 * appear only under acquired careers; a hidden unmet specialization is sent as a masked
 * silhouette so discovering it stays gameplay.
 */
public final class CareerGraphBuilder {
    private CareerGraphBuilder() {}

    public static List<CareerGraphS2CPayload.Node> build(MinecraftServer server, LivingEntity entity) {
        List<CareerGraphS2CPayload.Node> nodes = new ArrayList<>();
        CareerProfile profile = CareerProfiles.of(entity);
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (profile == null || store == null) return nodes;
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            if (def.isRoot()) appendCareer(nodes, server, entity, profile, store, def, def.id());
        }
        return nodes;
    }

    private static void appendCareer(List<CareerGraphS2CPayload.Node> nodes, MinecraftServer server,
                                     LivingEntity entity, CareerProfile profile,
                                     ProfessionXpStore store, ProfessionDef def,
                                     ResourceLocation rootId) {
        if (nodes.size() > 512) return;
        ResourceLocation careerId = def.id();
        int xp = ProfessionProgress.getXp(store, careerId);
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
        nodes.add(new CareerGraphS2CPayload.Node(
                careerId.toString(), rootId.toString(), parentId,
                def.isRoot() ? CareerGraphS2CPayload.KIND_ROOT : CareerGraphS2CPayload.KIND_ADVANCED,
                state,
                masked ? "" : def.displayName().getString(),
                masked || def.description() == null ? "" : def.description().getString(),
                masked || def.icon() == null ? "" : def.icon().toString(),
                ProfessionProgress.getTier(store, careerId), xp,
                ProfessionProgress.getXpToNextTier(store, careerId),
                primary, false, evidence));

        if (acquired) {
            for (ResourceLocation choice : def.skills()) {
                SkillDef skill = SkillDefs.byId(choice);
                if (skill == null) continue;
                boolean equipped = CareerChoices.isActive(entity, choice);
                nodes.add(new CareerGraphS2CPayload.Node(
                        choice.toString(), rootId.toString(), careerId.toString(),
                        CareerGraphS2CPayload.KIND_SKILL,
                        CareerGraphS2CPayload.STATE_READY,
                        skill.displayName().getString(),
                        skill.description() == null ? "" : skill.description().getString(),
                        skill.icon() == null ? "" : skill.icon().toString(),
                        0, 0, 0, false, equipped, List.of()));
            }
        }

        for (ProfessionDef child : ProfessionDefs.all().values()) {
            if (child.parents().contains(careerId)) {
                appendCareer(nodes, server, entity, profile, store, child, rootId);
            }
        }
    }

    private static List<CareerGraphS2CPayload.Evidence> evidenceFor(
            MinecraftServer server, LivingEntity entity, CareerProfile profile,
            ProfessionDef def, boolean acquired) {
        List<CareerGraphS2CPayload.Evidence> evidence = new ArrayList<>();
        if (acquired) {
            for (String counter : def.historyCounters()) {
                evidence.add(new CareerGraphS2CPayload.Evidence(prettify(counter),
                        Chronicles.count(server, entity.getUUID(), counter), 0, true));
            }
            return evidence;
        }
        for (RequirementHint hint : def.requirementHints()) {
            switch (hint.kind()) {
                case RequirementHint.KIND_CHRONICLE_COUNT -> {
                    int current = Chronicles.count(server, entity.getUUID(), hint.key());
                    evidence.add(new CareerGraphS2CPayload.Evidence(prettify(hint.key()),
                            current, hint.target(), current >= hint.target()));
                }
                case RequirementHint.KIND_CAREER_XP -> {
                    int current = profile.professionXp(Careers.resolve(hint.key())).xp();
                    evidence.add(new CareerGraphS2CPayload.Evidence(prettify(hint.key()) + " XP",
                            current, hint.target(), current >= hint.target()));
                }
                default -> evidence.add(new CareerGraphS2CPayload.Evidence(
                        prettify(hint.key()), 0, 0, false));
            }
        }
        return evidence;
    }

    /** "townstead:cooked" reads as "cooked"; underscores read as spaces. */
    private static String prettify(String key) {
        int colon = key.indexOf(':');
        String path = colon >= 0 ? key.substring(colon + 1) : key;
        return path.replace('_', ' ');
    }
}
