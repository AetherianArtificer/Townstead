package com.aetherianartificer.townstead.naming;

import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.Cultures;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.WeightedPool;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
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
        TownsteadVillager state = TownsteadVillagers.get(villager);
        String recorded = state.life().familyName();
        if (!recorded.isEmpty()) return recorded;

        NamingTradition tradition = traditionOf(villager);
        if (tradition == null) return "";

        String resolved = buildFamilyName(villager, tradition);
        if (resolved.isEmpty()) return "";
        state.life().setFamilyName(resolved);
        TownsteadVillagers.flush(villager);
        return resolved;
    }

    /**
     * Whether this villager's family name is finished, either because one is recorded or because
     * their tradition gives none. Lets the retry stop asking.
     */
    public static boolean settled(VillagerEntityMCA villager) {
        if (!TownsteadVillagers.get(villager).life().familyName().isEmpty()) return true;
        NamingTradition tradition = traditionOf(villager);
        return tradition == null
                || tradition.family().type() == NamingTradition.FamilyType.NONE;
    }

    /** The tradition this villager's culture uses, or null when neither resolves. */
    public static @Nullable NamingTradition traditionOf(VillagerEntityMCA villager) {
        Culture culture = Cultures.get(TownsteadVillagers.get(villager).life().culture());
        if (culture == null || !culture.hasNamingTradition()) return null;
        return NamingTraditions.get(culture.namingTradition());
    }

    /**
     * Builds a family name from a tradition's rule.
     *
     * <p>An inherited name comes from the household first, so siblings match and a line stays
     * together, and only from a pool when there is no parent to take it from. A derived name is
     * built from the named parent's given name and the child's own gendered affix, which is what
     * makes {@code -sson}/{@code -sdottir} and {@code -ovich}/{@code -ovna} the same mechanism.</p>
     */
    private static String buildFamilyName(VillagerEntityMCA villager, NamingTradition tradition) {
        NamingTradition.Family family = tradition.family();
        return switch (family.type()) {
            case NONE -> "";
            case INHERITED -> inherited(villager, family);
            case PATRONYMIC -> derived(villager, family, Gender.MALE);
            case MATRONYMIC -> derived(villager, family, Gender.FEMALE);
        };
    }

    private static String inherited(VillagerEntityMCA villager, NamingTradition.Family family) {
        String fromParents = parentFamilyName(villager, family.descent());
        if (!fromParents.isEmpty()) return fromParents;

        // An explicit list wins; otherwise the family names follow the given names, so a culture
        // built on another mod's lists gets that mod's surnames without ever naming the mod. When
        // neither is loaded there is simply no family name, which is how a pack that references
        // something absent degrades instead of failing.
        WeightedPool<String> pool = family.list().isEmpty() ? null : NameLists.family(family.list());
        if (pool == null) pool = NameLists.family(TownsteadVillagers.get(villager).life().nameList());
        return pool == null ? "" : pool.pickOne();
    }

    private static String derived(VillagerEntityMCA villager, NamingTradition.Family family, Gender from) {
        String stem = parentGivenName(villager, from);
        if (stem.isEmpty()) return "";
        return family.affix(genderOf(villager)).apply(stem);
    }

    /** A parent's family name, following the tradition's descent rule. */
    private static String parentFamilyName(VillagerEntityMCA villager, NamingTradition.Descent descent) {
        for (Entity parent : parents(villager)) {
            if (!(parent instanceof VillagerEntityMCA person)) continue;
            if (descent == NamingTradition.Descent.FATHER && genderOf(person) == Gender.FEMALE) continue;
            if (descent == NamingTradition.Descent.MOTHER && genderOf(person) != Gender.FEMALE) continue;
            String name = TownsteadVillagers.get(person).life().familyName();
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    /** The given name of the parent a derived family name is built from. */
    private static String parentGivenName(VillagerEntityMCA villager, Gender from) {
        for (Entity parent : parents(villager)) {
            if (!(parent instanceof VillagerEntityMCA person)) continue;
            boolean female = genderOf(person) == Gender.FEMALE;
            if (from == Gender.FEMALE != female) continue;
            String name = person.getName().getString().trim();
            if (!name.isEmpty()) return name;
        }
        return "";
    }

    private static Iterable<Entity> parents(VillagerEntityMCA villager) {
        try {
            return villager.getRelationships().getParents().toList();
        } catch (Throwable ignored) {
            return java.util.List.of();
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

    /** The culture recorded on a villager, or empty. */
    public static String cultureOf(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager).life().culture();
    }

    /** A parent's culture, which is how culture is normally passed on at all. */
    public static Optional<String> cultureFromParents(VillagerEntityMCA villager) {
        for (Entity parent : parents(villager)) {
            if (!(parent instanceof VillagerEntityMCA person)) continue;
            String culture = TownsteadVillagers.get(person).life().culture();
            if (Cultures.exists(culture)) return Optional.of(culture);
        }
        return Optional.empty();
    }
}
