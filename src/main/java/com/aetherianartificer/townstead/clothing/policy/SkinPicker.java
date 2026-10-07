package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.BodyClothingResolver;
import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingSet;
import com.aetherianartificer.townstead.clothing.ClothingThermal;
import com.aetherianartificer.townstead.clothing.VillageSpirits;
import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.culture.CultureClothing;
import com.aetherianartificer.townstead.root.Heritage;
import com.aetherianartificer.townstead.root.Rig;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.Species;
import com.aetherianartificer.townstead.root.SpeciesRegistry;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.SkinSelection;
import net.conczin.mca.resources.data.skin.Clothing;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Chooses a base skin for a villager from a policy's selector, weighted by culture, spirit, and
 * set weight, and seeded by villager and day so the choice holds for the day and differs between
 * neighbours.
 *
 * <p>Every weight is a rate, never a rule. The one exclusive step is the body: on a rig that is
 * not MCA's villager, only skins that fit the body are candidates at all.</p>
 */
public final class SkinPicker {

    private SkinPicker() {}

    /** One candidate with the weight the engine gives it. */
    public record Candidate(String skin, @Nullable ClothingEntry entry, double weight) {}

    /** Skins a player starred or picked for this villager; each one is much more likely to be drawn. */
    public record Favourites(Set<String> starred, Map<String, Integer> picks) {
        public static final Favourites NONE = new Favourites(Set.of(), Map.of());
        static final double STAR_WEIGHT = 40.0;
        static final double PICK_WEIGHT = 10.0;
        static final int PICK_CAP = 4;

        public Favourites {
            starred = starred == null ? Set.of() : Set.copyOf(starred);
            picks = picks == null ? Map.of() : Map.copyOf(picks);
        }

        public boolean isEmpty() {
            return starred.isEmpty() && picks.isEmpty();
        }

        public double score(String skin) {
            return (starred.contains(skin) ? STAR_WEIGHT : 0.0)
                    + PICK_WEIGHT * Math.min(PICK_CAP, picks.getOrDefault(skin, 0));
        }

        public Set<String> skins() {
            Set<String> out = new LinkedHashSet<>(starred);
            out.addAll(picks.keySet());
            return out;
        }
    }

    public static Optional<String> pick(VillagerEntityMCA villager, WardrobePolicy.Selector selector, long day) {
        return pick(villager, selector, day, Favourites.NONE);
    }

    public static Optional<String> pick(VillagerEntityMCA villager, WardrobePolicy.Selector selector, long day,
                                        Favourites favourites) {
        List<Candidate> candidates = candidates(villager, selector);
        if (candidates.isEmpty()) return Optional.empty();
        if (!favourites.isEmpty()) {
            List<Candidate> boosted = new ArrayList<>(candidates.size());
            for (Candidate c : candidates) {
                boosted.add(new Candidate(c.skin(), c.entry(), c.weight() * (1.0 + favourites.score(c.skin()))));
            }
            candidates = boosted;
        }
        return Optional.ofNullable(choose(candidates, new Random(seed(villager, day))));
    }

    /** A daily draw among the villager's favourites alone, for days no rule names a base. */
    public static Optional<String> pickFavourite(VillagerEntityMCA villager, Favourites favourites, long day,
                                                 Predicate<String> exists) {
        List<Candidate> candidates = new ArrayList<>();
        for (String skin : favourites.skins()) {
            if (exists.test(skin)) candidates.add(new Candidate(skin, null, favourites.score(skin)));
        }
        if (candidates.isEmpty()) return Optional.empty();
        return Optional.ofNullable(choose(candidates, new Random(seed(villager, day))));
    }

    private static long seed(VillagerEntityMCA villager, long day) {
        return villager.getUUID().getLeastSignificantBits() * 31L + day;
    }

    public static List<Candidate> candidates(VillagerEntityMCA villager, WardrobePolicy.Selector selector) {
        ClothingList list = ClothingList.getInstance();
        if (villager == null || selector == null || list == null || list.clothing == null) return List.of();

        Gender gender = villager.getGenetics().getGender();
        var life = TownsteadVillagers.get(villager).life();
        ResourceLocation rootId = com.aetherianartificer.townstead.data.DataPackLang.parseId(life.rootId());
        Heritage heritage = life.hasHeritage() ? life.heritage() : null;
        List<ClothingEntry> fitted = BodyClothingResolver.fitted(rootId, heritage);
        boolean bodyOnly = !onMcaRig(rootId);

        Culture culture = CultureAssignment.recorded(villager);
        CultureClothing cultureClothing = culture == null ? CultureClothing.NONE
                : com.aetherianartificer.townstead.culture.CultureBlends.clothing(villager);
        ToDoubleFunction<String> shares = VillageSpirits.sharesOf(villager);

        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, Clothing> e : list.clothing.entrySet()) {
            String skin = e.getKey();
            Clothing clothing = e.getValue();
            if (skin == null || clothing == null) continue;
            // Rig skins are excluded from MCA's own random picks (they don't fit its body); a body they
            // fit may still wear them.
            if (clothing.exclude && !(bodyOnly && fits(fitted, skin))) continue;
            if (!SkinSelection.matchesGender(clothing, gender)) continue;
            ClothingEntry entry = ClothingDefs.forSkin(skin);
            if (entry == null) entry = ClothingThermal.syntheticSkinEntry(skin);
            if (bodyOnly && !fits(fitted, skin)) continue;
            double setWeight = selectorWeight(selector, skin, entry, cultureClothing, fitted);
            if (setWeight <= 0) continue;
            double weight = setWeight
                    * cultureClothing.rateForSkin(skin, shares)
                    * (entry == null ? 1.0 : cultureClothing.rateFor(entry, shares) * spiritAffinity(entry, shares));
            out.add(new Candidate(skin, entry, weight));
        }
        return out;
    }

    /** Zero when the selector rejects the skin; otherwise the set weight that admitted it. */
    static double selectorWeight(WardrobePolicy.Selector selector, String skin, @Nullable ClothingEntry entry,
                                 CultureClothing culture, List<ClothingEntry> fitted) {
        if (selector.skin() != null) return selector.skinMatches(skin) ? 1.0 : 0.0;
        if (selector.set() != null) return setAdmits(selector.set(), skin);
        if (selector.cultureSets()) {
            double best = 0.0;
            for (ResourceLocation setId : culture.sets()) best = Math.max(best, setAdmits(setId, skin));
            for (CultureClothing.SkinBias bias : culture.skins()) {
                if (bias.matches(skin)) best = Math.max(best, 1.0);
            }
            return best;
        }
        if (selector.bodySets()) return fits(fitted, skin) ? 1.0 : 0.0;
        if (selector.query() == null || selector.query().isEmpty()) return 1.0;
        return entry != null && selector.query().test(entry) ? 1.0 : 0.0;
    }

    static double setAdmits(ResourceLocation setId, String skin) {
        ClothingSet set = ClothingDefs.set(setId);
        if (set == null) return 0.0;
        for (ClothingEntry member : ClothingDefs.members(setId)) {
            if (member.matchesSkin(skin)) return set.weight();
        }
        return 0.0;
    }

    static boolean fits(List<ClothingEntry> fitted, String skin) {
        for (ClothingEntry entry : fitted) {
            if (entry.matchesSkin(skin)) return true;
        }
        return false;
    }

    static double spiritAffinity(ClothingEntry entry, ToDoubleFunction<String> shares) {
        double weight = 1.0;
        for (String axis : entry.spirits()) weight *= 1.0 + Math.max(0.0, shares.applyAsDouble(axis));
        return weight;
    }

    /** Whether the skin fits the villager's body: any skin on MCA's villager, else only a fitted one. */
    public static boolean fitsBody(VillagerEntityMCA villager, String skin) {
        var life = TownsteadVillagers.get(villager).life();
        ResourceLocation rootId = com.aetherianartificer.townstead.data.DataPackLang.parseId(life.rootId());
        if (onMcaRig(rootId)) return true;
        return fits(BodyClothingResolver.fitted(rootId, life.hasHeritage() ? life.heritage() : null), skin);
    }

    static boolean onMcaRig(@Nullable ResourceLocation rootId) {
        ResourceLocation speciesId = RootRegistry.effectiveSpecies(rootId);
        Species species = speciesId == null ? null : SpeciesRegistry.byId(speciesId);
        return species == null || species.rig() == null || Rig.VILLAGER.base().equals(species.rig().base());
    }

    /** Weighted draw; the seed decides, so the same villager on the same day draws the same skin. */
    static @Nullable String choose(List<Candidate> candidates, Random random) {
        double total = 0;
        for (Candidate c : candidates) total += Math.max(0, c.weight());
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        for (Candidate c : candidates) {
            roll -= Math.max(0, c.weight());
            if (roll <= 0) return c.skin();
        }
        return candidates.get(candidates.size() - 1).skin();
    }
}
