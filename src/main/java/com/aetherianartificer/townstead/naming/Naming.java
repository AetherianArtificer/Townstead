package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.root.PlayerRoot;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.EntityRelationship;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Turns a villager's culture into their names.
 *
 * <p>Culture decides the naming tradition, the tradition decides which list a villager is named
 * from and what happens about family names, and both answers are recorded the first time they are
 * asked for. A person belongs to one tradition for life even when their culture draws on several,
 * and a family name is a fact about their birth rather than a live view of their parents: renaming
 * a father does not rename his grown children.</p>
 *
 * <p>Nothing here consults a Root. A villager's species biases only which culture a founder is born
 * into, and never touches anyone who has parents or a home.</p>
 */
public final class Naming {

    private Naming() {}

    /**
     * The name list this villager is named from, recorded on first use, or empty when their culture
     * has no usable tradition. This is what MCA's own naming is answered with.
     */
    public static String nameList(VillagerEntityMCA villager) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return "";
        TownsteadVillager state = TownsteadVillagers.get(villager);
        String recorded = state.life().nameList();
        if (NameLists.hasGiven(recorded)) return recorded;

        NamingTradition tradition = traditionOf(villager);
        if (tradition == null) return "";

        String rolled = NamingTraditions.rollGivenList(tradition, villager.getRandom());
        if (rolled.isEmpty()) return "";
        state.life().setNameList(rolled);
        TownsteadVillagers.flush(villager);
        return rolled;
    }

    /**
     * This villager's family name, recorded on first use. Empty when their tradition gives none,
     * which is a real answer rather than a failure: plenty of peoples have no family names.
     */
    public static String familyName(VillagerEntityMCA villager) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return "";
        TownsteadVillager state = TownsteadVillagers.get(villager);
        String recorded = state.life().familyName();
        if (!recorded.isEmpty() || state.life().familyNameFixed()) return recorded;
        if (SpawnNaming.inBirth()) return "";

        NamingTradition tradition = traditionOf(villager);
        if (tradition == null) return "";

        String resolved = buildFamilyName(villager, tradition);
        if (resolved.isEmpty()) return "";
        state.life().setFamilyName(resolved);
        TownsteadVillagers.flush(villager);
        return resolved;
    }

    /**
     * A player's tradition, which they pick themselves. As with a villager, a culture that declares
     * one wins. Null until they pick.
     */
    public static @Nullable NamingTradition traditionOf(Player player) {
        String fromCulture = TraditionAssignment.ofCulture(PlayerRoot.getCulture(player));
        return NamingTraditions.get(ResourceLocation.tryParse(
                fromCulture.isEmpty() ? PlayerRoot.getNamingTradition(player) : fromCulture));
    }

    /** The name list a player is named from, recorded on first use, or empty with no tradition. */
    public static String nameList(Player player) {
        String recorded = PlayerRoot.getNameList(player);
        if (NameLists.hasGiven(recorded)) return recorded;
        NamingTradition tradition = traditionOf(player);
        if (tradition == null) return "";
        String rolled = NamingTraditions.rollGivenList(tradition, player.getRandom());
        if (!rolled.isEmpty()) PlayerRoot.setNameList(player, rolled);
        return rolled;
    }

    /** A player's family name, settled from their tradition by the rules a villager follows. */
    public static String familyName(Player player) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return "";
        String held = PlayerNames.authoritative(player);
        if (!held.isEmpty()) return held;
        String recorded = PlayerRoot.getFamilyName(player);
        if (!recorded.isEmpty() || PlayerRoot.isFamilyNameFixed(player)) return recorded;
        NamingTradition tradition = traditionOf(player);
        if (tradition == null) return "";
        nameList(player);
        String resolved = buildFamilyName(player, tradition);
        if (resolved.isEmpty()) resolved = PlayerNames.fallback(player, tradition);
        if (!resolved.isEmpty()) PlayerRoot.setFamilyName(player, resolved);
        return resolved;
    }

    /**
     * Whether this villager's family name is finished, either because one is recorded or because
     * their tradition gives none. Lets the retry stop asking.
     */
    public static boolean settled(VillagerEntityMCA villager) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (!life.familyName().isEmpty() || life.familyNameFixed()) return true;
        NamingTradition tradition = traditionOf(villager);
        return tradition == null
                || tradition.family().type() == NamingTradition.FamilyType.NONE;
    }

    /**
     * The tradition this villager is named by, or null when none resolves.
     *
     * <p>A culture that declares one wins, so a Highhold family keeps its naming wherever it lives.
     * Otherwise it is whatever was recorded for them, which for almost every villager is their
     * region's. Nothing here consults a Root.</p>
     */
    public static @Nullable NamingTradition traditionOf(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);

        Culture culture = Cultures.get(state.life().culture());
        if (culture != null && culture.hasNamingTradition()) {
            NamingTradition declared = NamingTraditions.get(culture.namingTradition());
            if (declared != null) return declared;
        }
        return NamingTraditions.get(ResourceLocation.tryParse(state.life().namingTradition()));
    }

    /**
     * Builds a family name from a tradition's rule.
     *
     * <p>An inherited name comes from the household first, so siblings match and a line stays
     * together, and only from a pool when there is no parent to take it from. A derived name is
     * built from the named parent's given name and the child's own gendered affix, which is what
     * makes {@code -sson}/{@code -sdottir} and {@code -ovich}/{@code -ovna} the same mechanism.</p>
     */
    private static String buildFamilyName(Entity person, NamingTradition tradition) {
        NamingTradition.Family family = tradition.family();
        return switch (family.type()) {
            case NONE -> "";
            case INHERITED -> inherited(person, family);
            case PATRONYMIC -> derived(person, family, Gender.MALE);
            case MATRONYMIC -> derived(person, family, Gender.FEMALE);
        };
    }

    private static String inherited(Entity person, NamingTradition.Family family) {
        String fromParents = parentFamilyName(person, family.descent());
        if (!fromParents.isEmpty()) return fromParents;

        // An explicit list wins, rolled among the rule's weighted sources so a culture can draw
        // surnames from more than one people; otherwise the family names follow the given names, so
        // a culture built on another mod's lists gets that mod's surnames without ever naming the
        // mod. When neither is loaded there is simply no family name, which is how a pack that
        // references something absent degrades instead of failing.
        String rolled = NamingTraditions.rollFamilyList(family, randomOf(person));
        WeightedPool<String> pool = rolled.isEmpty() ? null : NameLists.family(rolled);
        if (pool == null) pool = NameLists.family(person instanceof Player player
                ? PlayerRoot.getNameList(player)
                : TownsteadVillagers.get((VillagerEntityMCA) person).life().nameList());
        return pool == null ? "" : pool.pickOne();
    }

    private static String derived(Entity person, NamingTradition.Family family, Gender from) {
        String stem = parentGivenName(person, from);
        if (stem.isEmpty()) return "";
        return family.affix(parentGender(person)).apply(stem);
    }

    private static RandomSource randomOf(Entity person) {
        return person instanceof net.minecraft.world.entity.LivingEntity living ? living.getRandom() : RandomSource.create();
    }

    /**
     * A parent's family name, following the tradition's descent rule. When that side has none to
     * give, such as a player who never set one, the child takes the other parent's. A name another
     * mod holds, such as a House, comes before descent. A parent's name is the one they show.
     */
    private static String parentFamilyName(Entity person, NamingTradition.Descent descent) {
        String held = heldParentFamily(person);
        if (!held.isEmpty()) return held;
        String other = "";
        for (Entity parent : parents(person)) {
            String name = parent instanceof VillagerEntityMCA villager ? VillagerNames.parts(villager).family()
                    : parent instanceof Player player ? familyName(player) : "";
            if (name.isEmpty()) continue;
            boolean skipped = descent == NamingTradition.Descent.FATHER && parentGender(parent) == Gender.FEMALE
                    || descent == NamingTradition.Descent.MOTHER && parentGender(parent) != Gender.FEMALE;
            if (!skipped) return name;
            if (other.isEmpty()) other = name;
        }
        return other;
    }

    /**
     * Settles a baby item's child once MCA has assigned its parents. The factory that built the
     * child, or the dummy saved in the item, may have recorded naming facts from where it stood, so
     * those are cleared and worked out again from the family.
     */
    public static void settleBirth(VillagerEntityMCA child) {
        if (!com.aetherianartificer.townstead.switchboard.Systems.on(com.aetherianartificer.townstead.switchboard.Systems.NAMING)) return;
        if (!(child.level() instanceof ServerLevel level)) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(child).life();
        life.setCulture("");
        life.setNamingTradition("");
        life.setNameList("");
        life.setFamilyName("");
        life.setFamilyNameFixed(false);
        TownsteadVillagers.flush(child);
        CultureAssignment.ensure(level, child);
        TraditionAssignment.ensure(level, child);
        nameList(child);
        settleFromParents(child);
    }

    /**
     * Passes on a family name the child's tradition would not build, such as one typed into the
     * editor for a villager whose tradition gives none. Only a chosen or held name passes this way:
     * where a tradition gives no names, another mod may own them and decide the child's itself.
     */
    public static void settleFromParents(VillagerEntityMCA child) {
        TownsteadVillager.Life life = TownsteadVillagers.get(child).life();
        if (!life.familyName().isEmpty() || life.familyNameFixed()) return;
        NamingTradition tradition = traditionOf(child);
        if (tradition != null && tradition.family().type() != NamingTradition.FamilyType.NONE) return;
        String name = heldParentFamily(child);
        if (name.isEmpty()) name = chosenParentFamily(child);
        if (name.isEmpty()) return;
        life.setFamilyName(name);
        TownsteadVillagers.flush(child);
    }

    /** The family name another mod holds for one of this person's parents, or empty. */
    public static String heldParentFamily(Entity person) {
        for (Entity parent : parents(person)) {
            if (parent instanceof Player player) {
                String held = PlayerNames.authoritative(player);
                if (!held.isEmpty()) return held;
            } else if (parent instanceof VillagerEntityMCA villager) {
                NameParts parts = VillagerNames.parts(villager);
                if (parts.familyLocked() && parts.hasFamily()) return parts.family();
            }
        }
        return "";
    }

    private static String chosenParentFamily(Entity person) {
        for (Entity parent : parents(person)) {
            String name = "";
            if (parent instanceof Player player) {
                name = familyName(player);
            } else if (parent instanceof VillagerEntityMCA villager) {
                TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
                if (life.familyNameFixed()) name = life.familyName();
            }
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    /** The given name of the parent a derived family name is built from. */
    private static String parentGivenName(Entity person, Gender from) {
        for (Entity parent : parents(person)) {
            boolean female = parentGender(parent) == Gender.FEMALE;
            if (from == Gender.FEMALE != female) continue;
            String name = parent instanceof VillagerEntityMCA villager ? villager.getName().getString().trim()
                    : parent instanceof Player player ? PlayerNames.given(player) : "";
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    public static Iterable<Entity> parents(Entity person) {
        try {
            if (person instanceof VillagerEntityMCA villager) return villager.getRelationships().getParents().toList();
            return EntityRelationship.of(person).map(relationship -> relationship.getParents().toList())
                    .orElse(java.util.List.of());
        } catch (Throwable ignored) {
            return java.util.List.of();
        }
    }

    private static Gender parentGender(Entity parent) {
        if (parent instanceof VillagerEntityMCA villager) return genderOf(villager);
        try {
            return EntityRelationship.of(parent).map(EntityRelationship::getGender).orElse(Gender.MALE);
        } catch (Throwable ignored) {
            return Gender.MALE;
        }
    }

    private static Gender genderOf(VillagerEntityMCA villager) {
        try {
            return villager.getGenetics().getGender();
        } catch (Throwable ignored) {
            return Gender.MALE;
        }
    }

    /** Records a culture on a villager, leaving one already recorded alone. */
    public static void recordCulture(VillagerEntityMCA villager, @Nullable ResourceLocation culture) {
        if (culture == null || Cultures.get(culture) == null) return;
        TownsteadVillager state = TownsteadVillagers.get(villager);
        if (!state.life().culture().isEmpty()) return;
        state.life().setCulture(culture.toString());
        TownsteadVillagers.flush(villager);
    }

    /**
     * Gives a newly constructed founder its authored culture before its real name is chosen.
     *
     * <p>{@link net.conczin.mca.entity.VillagerFactory} may provisionally name an entity while it
     * is being built. That geographic choice is valuable for ordinary unprofiled villagers, but
     * it is not a birth fact yet: a debug/founding profile already knows the founder's culture.
     * This method is intentionally for brand-new, unadded entities only. It clears the provisional
     * naming facts so {@link SpawnNaming#complete(VillagerEntityMCA)} can settle them from the
     * culture during {@code finalizeSpawn}; it must never be used to rewrite an existing person.</p>
     */
    public static void prepareFounder(VillagerEntityMCA villager, @Nullable ResourceLocation culture) {
        if (villager == null || culture == null || Cultures.get(culture) == null) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        life.setCulture(culture.toString());
        life.setNamingTradition("");
        life.setNameList("");
        life.setFamilyName("");
        life.setFamilyNameFixed(false);
        villager.setCustomName(null);
        TownsteadVillagers.flush(villager);
    }

    /**
     * Gives a villager a name they already carry from elsewhere, such as the mob whose costume they
     * are. The name is kept exactly: given-first traditions split off the last word as the family
     * name, and any other keeps it whole as the given name. Either way the family name is fixed, so
     * no rule adds or replaces one.
     */
    public static void adopt(VillagerEntityMCA villager, String full) {
        String name = full == null ? "" : full.trim();
        if (name.isEmpty()) return;
        NamingTradition tradition = traditionOf(villager);
        int split = name.lastIndexOf(' ');
        boolean splits = split > 0 && (tradition == null || tradition.order() == NamingTradition.Order.GIVEN_FIRST);
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        villager.setCustomName(net.minecraft.network.chat.Component.literal(splits ? name.substring(0, split) : name));
        life.setFamilyName(splits ? name.substring(split + 1) : "");
        life.setFamilyNameFixed(true);
        TownsteadVillagers.flush(villager);
        VillagerNames.publish(villager);
    }

    /**
     * Moves a villager to a culture, or to a tradition with no culture, from the editor. Picking the
     * root of the culture they already hold changes nothing, so a subculture survives. A chosen
     * family name stays, as it does with {@code /townstead naming}.
     */
    public static void reassign(ServerLevel level, VillagerEntityMCA villager, String cultureId, String traditionId) {
        String culture = cultureId == null ? "" : cultureId.trim();
        String tradition = traditionId == null ? "" : traditionId.trim();
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (!culture.isEmpty()) {
            Culture chosen = Cultures.get(culture);
            if (chosen == null || chosen.isSubculture() || !NamingTraditions.culturePickable(chosen)) return;
            if (chosen.id().toString().equals(Cultures.rootOf(life.culture()))) return;
            life.setCulture(chosen.id().toString());
            life.setNamingTradition("");
        } else {
            if (NamingTraditions.get(ResourceLocation.tryParse(tradition)) == null) return;
            life.setCulture("");
            life.setNamingTradition(tradition);
        }
        life.setNameList("");
        if (!life.familyNameFixed()) life.setFamilyName("");
        TownsteadVillagers.flush(villager);
        TraditionAssignment.ensure(level, villager);
    }

    /** The culture recorded on a villager, or empty. */
    public static String cultureOf(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager).life().culture();
    }

    /** A parent's culture, which is how culture is normally passed on at all. */
    public static Optional<String> cultureFromParents(VillagerEntityMCA villager) {
        for (Entity parent : parents(villager)) {
            String culture = parent instanceof VillagerEntityMCA person ? TownsteadVillagers.get(person).life().culture()
                    : parent instanceof Player player ? PlayerRoot.getCulture(player) : "";
            if (Cultures.exists(culture)) return Optional.of(culture);
        }
        return Optional.empty();
    }
}
