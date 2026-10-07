package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.spirit.SpiritBaseline;
import com.aetherianartificer.townstead.spirit.SpiritTotals;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How Community Spirit maps onto a culture's subcultures. Spirit is the same in every town; each
 * culture reads it through its own subcultures, so a commercial Rosaguardan town grows merchant houses
 * where a commercial Boglander one grows something else.
 */
public final class Subcultures {
    /** A subculture that founds a form is chosen even in a town with no spirit yet. */
    private static final double BASE_FIT = 0.25;
    /** How strongly a village's spirit favors the forms its fitting subcultures found. */
    private static final double FOUNDING_SPIRIT = 2.0;

    private Subcultures() {}

    /**
     * How well a village's spirit suits this subculture: for each listed axis, how far the village's
     * share stands above an ordinary village's ({@link SpiritBaseline}), times the axis weight. An
     * ordinary village suits nothing in particular, so it pulls nobody.
     */
    public static double spiritFit(Culture subculture, @Nullable SpiritTotals spirit) {
        if (spirit == null || spirit.total() <= 0) return 0;
        double fit = 0;
        for (Map.Entry<String, Float> axis : subculture.spirit().entrySet()) {
            fit += SpiritBaseline.excess(spirit, axis.getKey()) * axis.getValue();
        }
        return fit;
    }

    /** Whether this culture's towns use the founding profile. */
    public static boolean founds(Culture culture, ResourceLocation profile) {
        for (Culture.Form form : culture.forms()) if (form.profile().equals(profile)) return true;
        return false;
    }

    private static float formWeight(Culture culture, ResourceLocation profile) {
        for (Culture.Form form : culture.forms()) if (form.profile().equals(profile)) return form.weight();
        return 0;
    }

    /**
     * How much a village's spirit favors this founding profile: one when nothing fits or no
     * subculture founds it, more when the subcultures that found it suit what was built.
     */
    public static float foundingFactor(@Nullable ResourceLocation culture, ResourceLocation profile,
                                       @Nullable SpiritTotals spirit) {
        double best = 0;
        for (Culture sub : Cultures.subculturesOf(culture)) {
            if (founds(sub, profile)) best = Math.max(best, spiritFit(sub, spirit));
        }
        return (float) (1.0 + FOUNDING_SPIRIT * best);
    }

    /**
     * The subculture a village founded with this profile belongs to: among the culture's subcultures
     * that found the profile, weighted by their form weight and how well the village's spirit suits
     * them. The culture itself when none of its subcultures founds the profile.
     */
    public static @Nullable ResourceLocation forFounding(@Nullable ResourceLocation culture, ResourceLocation profile,
                                                         @Nullable SpiritTotals spirit, RandomSource random) {
        if (culture == null) return null;
        List<Culture> candidates = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (Culture sub : Cultures.subculturesOf(culture)) {
            float form = formWeight(sub, profile);
            if (form <= 0) continue;
            double weight = form * (BASE_FIT + spiritFit(sub, spirit));
            candidates.add(sub);
            weights.add(weight);
            total += weight;
        }
        if (total <= 0) return culture;
        double roll = random.nextDouble() * total;
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return candidates.get(i).id();
        }
        return candidates.get(candidates.size() - 1).id();
    }
}
