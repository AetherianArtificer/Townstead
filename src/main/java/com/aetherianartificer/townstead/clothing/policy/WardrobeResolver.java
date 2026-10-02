package com.aetherianartificer.townstead.clothing.policy;

import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.Weather;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeAssignments;
import com.aetherianartificer.townstead.clothing.wardrobe.WardrobeServer;
import com.aetherianartificer.townstead.culture.Culture;
import com.aetherianartificer.townstead.culture.CultureAssignment;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.types.ShiftStateConditionType;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Which rule holds each layer for one villager right now.
 *
 * <p>Every matching policy offers its rules; per layer the highest priority wins, ties go to the
 * narrower scope (a worksite on shift, then the villager's own policy, then culture, then
 * village), and document order settles the rest. Scopes that no data can yet address, villager
 * and worksite, are skipped here and land with the Wardrobe screen and the Tailor.</p>
 *
 * <p>Any layer no policy names falls to the weather default ({@link WeatherPolicy}), read from
 * the outdoors at the villager, so every villager dresses for the day without a single policy
 * document loaded.</p>
 *
 * <p>The Wardrobe screen's assignments come first: the villager's own cell for today at the
 * villager scope, then the Village row at the village scope, both lifted above every data
 * policy so a player's choice wins.</p>
 */
public final class WardrobeResolver {

    /** Added to an assigned template's priority so it beats pack policies at any scope. */
    public static final int ASSIGNED_PRIORITY = 1000;

    /** The chosen rule per layer and the policy it came from. */
    public record Plan(Map<ClothingLayer, WardrobePolicy.LayerRule> rules,
                       Map<ClothingLayer, WardrobePolicy> sources) {
        public static final Plan EMPTY = new Plan(Map.of(), Map.of());

        public Plan {
            rules = rules == null ? Map.of() : Map.copyOf(rules);
            sources = sources == null ? Map.of() : Map.copyOf(sources);
        }

        public @Nullable WardrobePolicy.LayerRule rule(ClothingLayer layer) {
            return rules.get(layer);
        }

        public boolean isEmpty() {
            return rules.isEmpty();
        }
    }

    private WardrobeResolver() {}

    public static Plan resolve(VillagerEntityMCA villager) {
        if (villager == null) return Plan.EMPTY;
        Culture culture = CultureAssignment.recorded(villager);
        ConditionContext ctx = new ConditionContext(villager);
        boolean onShift = ShiftStateConditionType.stateOf(villager) == ShiftStateConditionType.State.ON_SHIFT;

        Map<ClothingLayer, WardrobePolicy.LayerRule> rules = new EnumMap<>(ClothingLayer.class);
        Map<ClothingLayer, WardrobePolicy> sources = new EnumMap<>(ClothingLayer.class);
        if (villager.level() instanceof ServerLevel serverLevel && serverLevel.getServer() != null) {
            MinecraftServer server = serverLevel.getServer();
            int day = WardrobeServer.today(server);
            WardrobeAssignments assignments = WardrobeAssignments.get(server);
            offer(rules, sources, ctx, WardrobeServer.templateOf(assignments.villager(villager.getUUID(), day)),
                    WardrobePolicy.Scope.VILLAGER);
            offer(rules, sources, ctx, WardrobeServer.templateOf(assignments.village(day)),
                    WardrobePolicy.Scope.VILLAGE);
        }
        for (WardrobePolicy policy : WardrobePolicies.all()) {
            if (!applies(policy, culture, onShift)) continue;
            if (policy.when() != null && !policy.when().test(ctx)) continue;
            for (Map.Entry<ClothingLayer, WardrobePolicy.LayerRule> e : policy.layers().entrySet()) {
                WardrobePolicy current = sources.get(e.getKey());
                if (current == null || beats(policy, current)) {
                    rules.put(e.getKey(), e.getValue());
                    sources.put(e.getKey(), policy);
                }
            }
        }
        if (villager.level() instanceof ServerLevel level) {
            fill(rules, sources, WeatherPolicy.of(Weather.outdoorKind(level, villager.blockPosition())));
        }
        return rules.isEmpty() ? Plan.EMPTY : new Plan(rules, sources);
    }

    /** Offers an assigned template's rules at the assignment's scope, lifted above data policies. */
    static void offer(Map<ClothingLayer, WardrobePolicy.LayerRule> rules, Map<ClothingLayer, WardrobePolicy> sources,
                      ConditionContext ctx, @Nullable WardrobePolicy template, WardrobePolicy.Scope scope) {
        if (template == null) return;
        if (template.when() != null && !template.when().test(ctx)) return;
        WardrobePolicy assigned = new WardrobePolicy(template.id(), scope, template.cultures(),
                ASSIGNED_PRIORITY + template.priority(), template.when(), template.layers());
        for (Map.Entry<ClothingLayer, WardrobePolicy.LayerRule> e : assigned.layers().entrySet()) {
            WardrobePolicy current = sources.get(e.getKey());
            if (current == null || beats(assigned, current)) {
                rules.put(e.getKey(), e.getValue());
                sources.put(e.getKey(), assigned);
            }
        }
    }

    /** Adds the default's rules for every layer still open. */
    static void fill(Map<ClothingLayer, WardrobePolicy.LayerRule> rules, Map<ClothingLayer, WardrobePolicy> sources,
                     WardrobePolicy fallback) {
        if (fallback == null) return;
        for (Map.Entry<ClothingLayer, WardrobePolicy.LayerRule> e : fallback.layers().entrySet()) {
            if (sources.containsKey(e.getKey())) continue;
            rules.put(e.getKey(), e.getValue());
            sources.put(e.getKey(), fallback);
        }
    }

    static boolean applies(WardrobePolicy policy, @Nullable Culture culture, boolean onShift) {
        switch (policy.scope()) {
            case VILLAGE: return true;
            case CULTURE: return culture != null && policy.cultures().contains(culture.id());
            case WORKSITE: return false;
            case VILLAGER: return false;
            default: return false;
        }
    }

    /** Strictly better: higher priority, or the same priority from a narrower scope. */
    static boolean beats(WardrobePolicy candidate, WardrobePolicy current) {
        if (candidate.priority() != current.priority()) return candidate.priority() > current.priority();
        return candidate.scope().rank < current.scope().rank;
    }
}
