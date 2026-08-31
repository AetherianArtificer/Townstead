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
 * evidence, and chronicle moments resolved and localized before anything reaches the wire.
 * Every career sends its complete skill tree: an unregistered career is still useful as a map,
 * while server-side acquisition checks keep its preview nodes from becoming learnable.
 */
public final class CareerGraphBuilder {
    private CareerGraphBuilder() {}

    public static List<CareerGraphS2CPayload.Node> build(MinecraftServer server, LivingEntity entity,
                                                         Map<String, List<String>> momentsByCareer,
                                                         String locale) {
        List<CareerGraphS2CPayload.Node> nodes = new ArrayList<>();
        CareerProfile profile = CareerProfiles.of(entity);
        ProfessionXpStore store = CareerTreeRows.storeOf(entity);
        if (profile == null || store == null) return nodes;
        // Careers are flat: every def is its own top-level section on the board.
        for (ProfessionDef def : ProfessionDefs.all().values()) {
            appendCareer(nodes, server, entity, profile, store, def, def.id(), momentsByCareer,
                    locale);
        }
        appendComboSkills(nodes, entity, store, locale);
        return nodes;
    }

    /**
     * Combo Skills: the lateral joints between careers. Evidence rows carry each career
     * threshold with live progress, so the plaque explains exactly which histories it wants.
     */
    private static void appendComboSkills(List<CareerGraphS2CPayload.Node> nodes,
                                          LivingEntity entity, ProfessionXpStore store,
                                          String locale) {
        var unlockedIds = new java.util.HashSet<ResourceLocation>();
        for (var combo : com.aetherianartificer.townstead.profession.def.ComboSkills.unlockedFor(entity)) {
            unlockedIds.add(combo.id());
        }
        for (var combo : com.aetherianartificer.townstead.profession.def.ComboSkills.all().values()) {
            boolean unlocked = unlockedIds.contains(combo.id());
            List<CareerGraphS2CPayload.Evidence> evidence = new ArrayList<>();
            for (var threshold : combo.thresholds().entrySet()) {
                ProfessionDef careerDef = ProfessionDefs.byId(threshold.getKey());
                String careerName = careerDef != null
                        ? localized(careerDef.displayName(), locale) : threshold.getKey().toString();
                int current = ProfessionProgress.getTier(store, threshold.getKey());
                evidence.add(new CareerGraphS2CPayload.Evidence(
                        Component.translatable("townstead.career.combo.threshold",
                                careerName, threshold.getValue()).getString(),
                        current, threshold.getValue(), current >= threshold.getValue()));
            }
            // The shared plaque: one node per involved career, so the combo shows on every
            // tab it joins (the same combo id selects the same detail from any side).
            for (ResourceLocation involved : combo.thresholds().keySet()) {
                if (nodes.size() > 512) return;
                if (ProfessionDefs.byId(involved) == null) continue;
                nodes.add(new CareerGraphS2CPayload.Node(
                        combo.id().toString(), involved.toString(), involved.toString(),
                        CareerGraphS2CPayload.KIND_COMBO,
                        unlocked ? CareerGraphS2CPayload.STATE_ACQUIRED : CareerGraphS2CPayload.STATE_LOCKED,
                        localized(combo.displayName(), locale),
                        combo.description() == null ? "" : localized(combo.description(), locale),
                        combo.icon() == null ? "" : combo.icon().toString(),
                        // The rank THIS career must reach, so the board can put the mark in the band
                        // that actually gates it. Sending 0 parked every combo in rank I, however
                        // deep the work behind it was.
                        Math.max(0, combo.thresholds().getOrDefault(involved, 0)),
                        0, 0, 0, 0, 0, false, false, false,
                        "", "", List.copyOf(evidence), List.of(),
                        "", 0, "", "", effectLines(combo.grants()),
                        List.of(), CareerGraphS2CPayload.PathTag.NONE));
            }
        }
    }

    private static void appendCareer(List<CareerGraphS2CPayload.Node> nodes, MinecraftServer server,
                                     LivingEntity entity, CareerProfile profile,
                                     ProfessionXpStore store, ProfessionDef def,
                                     ResourceLocation rootId,
                                     Map<String, List<String>> momentsByCareer,
                                     String locale) {
        if (nodes.size() > 512) return;
        ResourceLocation careerId = def.id();
        int xp = ProfessionProgress.getXp(store, careerId);
        int currentTier = ProfessionProgress.getTier(store, careerId);
        boolean primary = careerId.equals(profile.primaryVocation());
        // Work you have ever declared stays yours. Keying this on "primary or has XP" meant that
        // declaring a second career emptied the first one's whole board the instant you switched,
        // before you had earned a single point in the new one, which looks exactly like data loss.
        boolean acquired = def.isRoot()
                ? (xp > 0 || primary || profile.careerHistory().contains(careerId))
                : profile.acquiredCareers().contains(careerId);
        boolean eligible = !acquired && def.eligible(entity);

        // A career you cannot take yet is LOCKED, never masked. Sending unmet careers as nameless
        // silhouettes made the board unreadable: a row of "?" plaques tells a reader neither what
        // exists nor what to do about it, and the requirement hints that would answer both were
        // exactly what the mask withheld. Seeing a career you have not earned is not a spoiler;
        // it is the only way the board can function as a map of where you could go. NOTE this
        // leaves `def.hidden()` inert — decide whether it earns its keep before authoring against it.
        final byte state;
        if (acquired) {
            state = CareerGraphS2CPayload.STATE_ACQUIRED;
        } else if (def.isRoot() || eligible) {
            state = CareerGraphS2CPayload.STATE_READY;
        } else {
            state = CareerGraphS2CPayload.STATE_LOCKED;
        }

        boolean masked = false;
        List<CareerGraphS2CPayload.Evidence> evidence = masked
                ? List.of() : evidenceFor(server, entity, profile, def, acquired);
        String parentId = "";

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

        // Build titles: a completed skill build renames the plaque, "Rotisseur (Cook)".
        String displayName = localized(def.displayName(), locale);
        if (!masked && acquired) {
            var title = com.aetherianartificer.townstead.profession.def.ProfessionTitles.resolve(
                    careerId, skillId -> com.aetherianartificer.townstead.profession.skill.LearnedSkills
                            .has(entity, skillId));
            if (title != null) {
                displayName = localized(title.name(), locale) + " (" + displayName + ")";
            }
        }

        nodes.add(new CareerGraphS2CPayload.Node(
                careerId.toString(), rootId.toString(), parentId,
                def.isRoot() ? CareerGraphS2CPayload.KIND_ROOT : CareerGraphS2CPayload.KIND_ADVANCED,
                state,
                masked ? "" : displayName,
                masked || def.description() == null ? "" : localized(def.description(), locale),
                masked || def.icon() == null ? "" : def.icon().toString(),
                currentTier, maxTier, xp,
                ProfessionProgress.getXpToNextTier(store, careerId), xpToday, dailyCap,
                primary, false, profile.trackedCareers().contains(careerId),
                routesLine, "", evidence, moments,
                masked ? "" : def.levelName(currentTier).getString(),
                SkillPoints.available(entity),
                "",
                masked || currentTier >= maxTier ? "" : def.levelName(currentTier + 1).getString(),
                List.of(), List.of(), CareerGraphS2CPayload.PathTag.NONE,
                // A career carries a mark of its own now: the day you were first admitted to this
                // work. Only skills were sending one, so the screen had no way to tell a career you
                // are returning to from one you have never held.
                stampOf(profile, careerId)));

        for (ResourceLocation choice : def.skills()) {
            SkillDef skill = SkillDefs.byId(choice);
            if (skill == null) continue;
            boolean learned = com.aetherianartificer.townstead.profession.skill.LearnedSkills
                    .has(entity, choice);
            boolean equipped = CareerChoices.isActive(entity, choice);
            boolean learnable = acquired && !learned && SkillPoints.canLearn(entity, def, skill);
            byte skillState = learned ? CareerGraphS2CPayload.STATE_ACQUIRED
                    : learnable ? CareerGraphS2CPayload.STATE_READY
                    : CareerGraphS2CPayload.STATE_LOCKED;
            String fullDescription = skill.description() == null
                    ? "" : localized(skill.description(), locale);
            SkillCopy copy = splitSkillCopy(fullDescription, effectLines(skill));
            nodes.add(new CareerGraphS2CPayload.Node(
                    choice.toString(), rootId.toString(), careerId.toString(),
                    CareerGraphS2CPayload.KIND_SKILL,
                    skillState,
                    localized(skill.displayName(), locale),
                    copy.about(),
                    skill.icon() == null ? "" : skill.icon().toString(),
                    skill.tier(), 0, 0, 0, 0, 0, false, equipped, false,
                    "", equipped || (!learned && !learnable)
                            ? "" : replacedSkillName(entity, def, skill, locale),
                    List.of(), List.of(),
                    def.levelName(skill.tier()).getString(), Math.max(0, skill.cost()),
                    skill.skillGroup() == null ? "" : skill.skillGroup().toString(),
                    "", copy.effects(),
                    prerequisitesWithin(def, skill), pathTag(def, choice, locale),
                    stampOf(profile, choice), abilityOf(skill)));
        }

    }

    /** Flavor leads the first sentence; the mechanical sentence belongs under What it does. */
    record SkillCopy(String about, List<String> effects) {}

    static SkillCopy splitSkillCopy(String description, List<String> authoredEffects) {
        List<String> effects = new ArrayList<>(authoredEffects);
        if (description == null || description.isBlank()) {
            return new SkillCopy("", List.copyOf(effects));
        }
        int sentence = description.indexOf(". ");
        if (sentence < 0) return new SkillCopy(description, List.copyOf(effects));
        String about = description.substring(0, sentence + 1).trim();
        String mechanics = description.substring(sentence + 2).trim();
        if (!mechanics.isEmpty()) effects.add(0, mechanics);
        return new SkillCopy(about, List.copyOf(effects));
    }

    /** The mark this subject pressed when they registered the skill, if they have. */
    private static CareerGraphS2CPayload.Stamp stampOf(CareerProfile profile,
                                                       net.minecraft.resources.ResourceLocation skill) {
        CareerStamp mark = profile == null ? null : profile.stamp(skill);
        return mark == null ? CareerGraphS2CPayload.Stamp.NONE
                : new CareerGraphS2CPayload.Stamp(true, mark.x(), mark.y(), mark.rotation(),
                        mark.authority(), mark.date(), mark.textureId(), mark.sourcePack(),
                        mark.label());
    }

    /**
     * The skill's prerequisites, narrowed to siblings the board will actually draw. A
     * cross-profession prerequisite still gates learning; it just has no node to hang a
     * line from, so the skill reads as attached to its career instead.
     */
    private static List<String> prerequisitesWithin(ProfessionDef def, SkillDef skill) {
        List<String> within = new ArrayList<>();
        for (ResourceLocation required : skill.requires()) {
            if (def.skills().contains(required)) within.add(required.toString());
        }
        return within;
    }

    /** The specialization arm a skill sits on, or {@code NONE} for the career's trunk. */
    private static CareerGraphS2CPayload.PathTag pathTag(ProfessionDef def, ResourceLocation skill,
                                                        String locale) {
        var path = com.aetherianartificer.townstead.profession.def.ProfessionPaths
                .pathOwning(def.id(), skill);
        return path == null ? CareerGraphS2CPayload.PathTag.NONE
                : new CareerGraphS2CPayload.PathTag(path.id(), localized(path.displayName(), locale),
                        path.gateway().equals(skill), path.color(),
                        path.backdrop() == null ? "" : path.backdrop().toString());
    }

    /**
     * Honest mechanical lines derived from a skill's grants. The authored description's first
     * sentence is About copy; its remaining sentence is prepended to this list by
     * {@link #splitSkillCopy(String, List)} so Pheno powers do not hide their behavior in About.
     */
    private static List<String> effectLines(SkillDef skill) {
        return effectLines(skill.grants());
    }

    /**
     * An active ability's mechanics, as fields rather than as two more bullets in the effect list.
     *
     * <p>They used to be rendered into the same list as the grants, so "hold a key, wait thirty
     * seconds, pay five hunger" arrived looking exactly like "+1 XP". They are not the same kind of
     * fact: a grant simply applies, and an ability is a thing you have to operate. The record needs
     * them apart to say so, and to say the part only the CLIENT knows — whether the slot's key has
     * been bound at all.</p>
     */
    private static CareerGraphS2CPayload.Ability abilityOf(SkillDef skill) {
        if (!(skill.power() instanceof com.aetherianartificer.townstead.root.gene.types
                .ActiveAbilityGeneType.Instance active)) {
            return CareerGraphS2CPayload.Ability.NONE;
        }
        String cost = active.costResource() == null || active.costAmount() <= 0 ? ""
                : localizeOr("townstead.resource." + active.costResource().getPath(),
                        prettify(active.costResource().getPath()));
        return new CareerGraphS2CPayload.Ability(true, active.slot(),
                Math.max(0, active.cooldownTicks()), Math.max(0, active.costAmount()), cost);
    }

    private static List<String> effectLines(List<com.aetherianartificer.townstead.profession.def.SkillGrant> grants) {
        List<String> lines = new ArrayList<>();
        for (com.aetherianartificer.townstead.profession.def.SkillGrant grant : grants) {
            String label = capabilityLabel(grant.key().id());
            String value = trimNumber(grant.value());
            String op = grant.op().name();
            String line;
            if ("ADD".equals(op)) {
                line = "+" + value + " " + label;
            } else if ("MULTIPLY".equals(op)) {
                line = "x" + value + " " + label;
            } else if ("DENY".equals(op)) {
                line = localizeOr("townstead.career.screen.effect.deny", "Disables") + " " + label;
            } else if ("OR".equals(op)) {
                line = localizeOr("townstead.career.screen.effect.grant", "Grants") + " " + label;
            } else {
                line = label + " " + op.toLowerCase(java.util.Locale.ROOT) + " " + value;
            }
            lines.add(line);
        }
        return lines;
    }

    private static String capabilityLabel(ResourceLocation id) {
        return localizeOr("townstead.capability." + id.getPath(),
                id.getPath().replace('_', ' '));
    }

    private static String trimNumber(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value)
                ? String.valueOf((long) value) : String.valueOf(value);
    }

    /** The currently equipped sibling this skill would replace within its choice group. */
    private static String replacedSkillName(LivingEntity entity, ProfessionDef career,
                                            SkillDef skill, String locale) {
        if (skill.skillGroup() == null) return "";
        for (ResourceLocation otherId : career.skills()) {
            if (otherId.equals(skill.id())) continue;
            SkillDef other = SkillDefs.byId(otherId);
            if (other == null || !skill.skillGroup().equals(other.skillGroup())) continue;
            if (CareerChoices.isActive(entity, otherId)) {
                return localized(other.displayName(), locale);
            }
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
            for (String counter : CareerActivities.counters(def)) {
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

    /** Engine activities use counter keys; data-driven Jobs use their resource translation key. */
    private static String counterLabel(String key) {
        if (CareerActivities.isJob(key)) {
            ResourceLocation job = ResourceLocation.tryParse(key);
            if (job != null) {
                String translation = "work_job." + job.getNamespace() + "."
                        + job.getPath().replace('/', '.');
                return localizeOr(translation, prettify(job.getPath()));
            }
        }
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

    /** Resolve a data-authored component for the player receiving this server-rendered record. */
    private static String localized(Component component, String locale) {
        String[] pair = com.aetherianartificer.townstead.calendar.ComponentSync
                .extract(component, locale);
        return pair[1].isEmpty() ? component.getString() : pair[1];
    }
}
