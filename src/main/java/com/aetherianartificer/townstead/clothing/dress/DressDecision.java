package com.aetherianartificer.townstead.clothing.dress;

import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.clothing.WornPiece;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import com.aetherianartificer.townstead.clothing.policy.WardrobeResolver;
import com.aetherianartificer.townstead.culture.CultureClothing;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a villager should do about their clothes right now, decided from the wardrobe plan, what
 * they wear, and how they feel. Pure, so it is testable without a world.
 *
 * <p>Two actions exist. Fetch: a layer's rule is unmet, go and get something that meets it.
 * Stow: a worn piece is one the plan says should not be there, take it off and shelve it. A
 * {@code none} rule with a selector stows only the pieces it admits. With no plan at all the
 * temperature tiers decide: cold wants any warm outerwear, hot sheds warm pieces that do not
 * also cool.</p>
 *
 * <p>Pieces carried in the villager's pockets (source {@code townstead:carried}) are passed in
 * with the worn ones: they meet a rule, since the coat taken off indoors is still the villager's
 * coat, and they are stowed like a worn piece when a rule says none.</p>
 */
public final class DressDecision {

    public static final String ARMOR_SOURCE = "townstead:armor_slots";

    public enum Kind { FETCH, STOW }

    /** One thing to do, in priority order. A fetch names the layer and selector; a stow names the piece. */
    public record Action(Kind kind, ClothingLayer layer, @Nullable WardrobePolicy.Selector selector,
                         @Nullable WornPiece piece, boolean required) {
        public static Action fetch(ClothingLayer layer, WardrobePolicy.Selector selector, boolean required) {
            return new Action(Kind.FETCH, layer, selector, null, required);
        }

        public static Action stow(WornPiece piece) {
            return new Action(Kind.STOW, piece.layer(), null, piece, true);
        }
    }

    private static final WardrobePolicy.Selector ANY_WARM = new WardrobePolicy.Selector(null, false, false, null,
            new ClothingQuery(null, null, Set.of(), Set.of(), Set.of(), ClothingQuery.Thermal.WARM, Set.of()));
    private static final WardrobePolicy.Selector ANY_COOL = new WardrobePolicy.Selector(null, false, false, null,
            new ClothingQuery(null, null, Set.of(), Set.of(), Set.of(), ClothingQuery.Thermal.COOL, Set.of()));

    private DressDecision() {}

    /**
     * @param worn every worn piece, armour and base included; only outerwear and accessories
     *             not managed by MCA's equipment task are acted on
     * @param armourManaged true when MCA's equipment task owns this villager's armor slots
     * @param tier the body tier when the plan is empty, else ignored
     */
    public static List<Action> decide(WardrobeResolver.Plan plan, List<WornPiece> worn, boolean armourManaged,
                                      @Nullable TemperatureData.Tier tier, boolean onShift,
                                      CultureClothing culture, List<ClothingEntry> fitted) {
        List<Action> actions = new ArrayList<>();
        List<WornPiece> movable = new ArrayList<>();
        for (WornPiece piece : worn) {
            if (piece.layer() != ClothingLayer.OUTERWEAR && piece.layer() != ClothingLayer.ACCESSORY) continue;
            if (piece.isSkin()) continue;
            if (armourManaged && ARMOR_SOURCE.equals(piece.source())) continue;
            movable.add(piece);
        }

        if (plan == null || plan.isEmpty()) {
            fallback(actions, movable, tier);
            return actions;
        }

        for (ClothingLayer layer : new ClothingLayer[] {ClothingLayer.OUTERWEAR, ClothingLayer.ACCESSORY}) {
            WardrobePolicy.LayerRule rule = plan.rule(layer);
            if (rule == null) continue;
            if (rule.requirement() == WardrobePolicy.Requirement.NONE) {
                for (WornPiece piece : movable) {
                    if (piece.layer() != layer) continue;
                    if (ClothingSelectors.admits(rule.selector(), piece.entry(), culture, fitted)) {
                        actions.add(Action.stow(piece));
                    }
                }
                continue;
            }
            boolean met = false;
            for (WornPiece piece : movable) {
                if (piece.layer() == layer && ClothingSelectors.admits(rule.selector(), piece.entry(), culture, fitted)) {
                    met = true;
                    break;
                }
            }
            if (met) continue;
            boolean required = rule.requirement() == WardrobePolicy.Requirement.REQUIRED;
            // Off shift a preference is acted on; on shift only a requirement is.
            if (required || !onShift) actions.add(Action.fetch(layer, rule.selector(), required));
        }
        actions.sort((a, b) -> Boolean.compare(b.required(), a.required()));
        return actions;
    }

    private static void fallback(List<Action> actions, List<WornPiece> movable, @Nullable TemperatureData.Tier tier) {
        if (tier == null || !tier.wantsRelief()) return;
        if (tier.isCold()) {
            for (WornPiece piece : movable) {
                if (piece.layer() == ClothingLayer.OUTERWEAR && piece.entry() != null && piece.entry().isWarm()) return;
            }
            actions.add(Action.fetch(ClothingLayer.OUTERWEAR, ANY_WARM, true));
            return;
        }
        for (WornPiece piece : movable) {
            ClothingEntry entry = piece.entry();
            if (entry != null && entry.isWarm() && !entry.isCool()) actions.add(Action.stow(piece));
        }
        if (actions.isEmpty()) actions.add(Action.fetch(ClothingLayer.ACCESSORY, ANY_COOL, false));
    }
}
