package com.aetherianartificer.townstead.work.recipe;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Bounded, stock-aware planning for a demanded output across multiple recipe stages. */
public final class RecursiveDemandPlanner {
    public record Limits(int maxDepth, int maxNodes) {
        public Limits {
            if (maxDepth < 1 || maxNodes < 1) {
                throw new IllegalArgumentException("planner limits must be positive");
            }
        }

        public static Limits defaults() { return new Limits(8, 512); }
    }

    public enum FailureKind {
        NO_RECIPE, CYCLE, DEPTH_LIMIT, NODE_LIMIT, RECIPE_NOT_READY, EXACT_PRODUCT_UNSUPPORTED
    }

    public record Failure(FailureKind kind, ResourceLocation item,
                          List<ResourceLocation> path, String detail) {
        public Failure {
            path = List.copyOf(path);
            detail = detail == null ? "" : detail;
        }
    }

    public record Step(DiscoveredRecipe recipe, int batches) {}

    public record Plan(boolean succeeded, List<Step> steps, Map<ResourceLocation, Integer> stock,
                       double cost, int visitedNodes, Failure failure) {
        public Plan {
            steps = List.copyOf(steps);
            stock = Map.copyOf(stock);
        }
    }

    private record State(Map<ResourceLocation, Integer> stock,
                         List<Step> steps, double cost) {
        State copy() { return new State(new HashMap<>(stock), new ArrayList<>(steps), cost); }
    }

    private record Search(State state, Failure failure) {
        static Search success(State state) { return new Search(state, null); }
        boolean succeeded() { return failure == null; }
    }

    private static final class Context {
        final Map<ResourceLocation, List<DiscoveredRecipe>> byOutput;
        final Predicate<DiscoveredRecipe> ready;
        final Limits limits;
        int visited;

        Context(List<DiscoveredRecipe> recipes, Predicate<DiscoveredRecipe> ready, Limits limits) {
            this.ready = ready;
            this.limits = limits;
            Map<ResourceLocation, List<DiscoveredRecipe>> indexed = new HashMap<>();
            for (DiscoveredRecipe recipe : recipes) {
                if (recipe == null || recipe.output() == null || recipe.outputCount() < 1) continue;
                indexed.computeIfAbsent(recipe.output(), unused -> new ArrayList<>()).add(recipe);
            }
            indexed.values().forEach(values -> values.sort(
                    Comparator.comparing(recipe -> recipe.id().toString())));
            byOutput = Map.copyOf(indexed);
        }
    }

    private RecursiveDemandPlanner() {}

    public static Plan plan(List<DiscoveredRecipe> recipes,
                            Map<ResourceLocation, Integer> available,
                            ResourceLocation demandedItem, int demandedCount) {
        return plan(recipes, available, demandedItem, demandedCount,
                recipe -> true, Limits.defaults());
    }

    public static Plan plan(List<DiscoveredRecipe> recipes,
                            Map<ResourceLocation, Integer> available,
                            ResourceLocation demandedItem, int demandedCount,
                            Predicate<DiscoveredRecipe> ready, Limits limits) {
        if (demandedItem == null || demandedCount < 1) {
            throw new IllegalArgumentException("demanded item and positive count are required");
        }
        Context context = new Context(recipes == null ? List.of() : recipes,
                ready == null ? recipe -> true : ready,
                limits == null ? Limits.defaults() : limits);
        Map<ResourceLocation, Integer> startingStock = new HashMap<>();
        if (available != null) available.forEach((item, count) -> {
            if (item != null && count != null && count > 0) startingStock.put(item, count);
        });
        Search search = ensure(context, new State(startingStock, new ArrayList<>(), 0.0d),
                demandedItem, demandedCount, 0, new LinkedHashSet<>());
        if (!search.succeeded()) {
            return new Plan(false, List.of(), startingStock, Double.POSITIVE_INFINITY,
                    context.visited, search.failure());
        }
        return new Plan(true, aggregate(search.state().steps()), search.state().stock(),
                search.state().cost(), context.visited, null);
    }

    private static Search ensure(Context context, State state, ResourceLocation item, int count,
                                 int depth, LinkedHashSet<ResourceLocation> path) {
        int present = state.stock().getOrDefault(item, 0);
        if (present >= count) return Search.success(state);
        if (++context.visited > context.limits.maxNodes()) {
            return failure(FailureKind.NODE_LIMIT, item, path, "visited-node limit exceeded");
        }
        if (depth >= context.limits.maxDepth()) {
            return failure(FailureKind.DEPTH_LIMIT, item, path, "recipe depth limit exceeded");
        }
        if (!path.add(item)) {
            return failure(FailureKind.CYCLE, item, path, "recipe dependency cycle");
        }

        List<DiscoveredRecipe> candidates = context.byOutput.getOrDefault(item, List.of());
        if (candidates.isEmpty()) {
            path.remove(item);
            return failure(FailureKind.NO_RECIPE, item, path, "no recipe produces missing item");
        }

        State best = null;
        Failure bestFailure = null;
        boolean sawReady = false;
        int shortfall = count - present;
        for (DiscoveredRecipe recipe : candidates) {
            if (!context.ready.test(recipe)) continue;
            sawReady = true;
            int batches = ceilDiv(shortfall, Math.max(1, recipe.outputCount()));
            State branch = state.copy();
            Search inputs = satisfyInputs(context, branch, recipe, batches, depth + 1, path);
            if (!inputs.succeeded()) {
                bestFailure = prefer(bestFailure, inputs.failure());
                continue;
            }
            State produced = inputs.state();
            produced.stock().merge(item, recipe.outputCount() * batches, Integer::sum);
            produced.steps().add(new Step(recipe, batches));
            State priced = new State(produced.stock(), produced.steps(),
                    produced.cost() + recipeCost(recipe, batches));
            if (best == null || priced.cost() < best.cost()) best = priced;
        }
        path.remove(item);
        if (best != null) return Search.success(best);
        if (!sawReady) {
            return failure(FailureKind.RECIPE_NOT_READY, item, path,
                    "all producing recipes failed readiness checks");
        }
        return new Search(null, bestFailure != null ? bestFailure
                : new Failure(FailureKind.NO_RECIPE, item, List.copyOf(path),
                "no producing recipe could satisfy its inputs"));
    }

    private static Search satisfyInputs(Context context, State state, DiscoveredRecipe recipe,
                                        int batches, int depth,
                                        LinkedHashSet<ResourceLocation> path) {
        State current = state;
        if (recipe.containerItemId() != null && recipe.containerCount() > 0) {
            Search container = satisfyAlternatives(context, current,
                    List.of(recipe.containerItemId()), recipe.containerCount() * batches,
                    depth, path);
            if (!container.succeeded()) return container;
            current = container.state();
        }
        for (RecipeIngredient ingredient : RecipeIngredient.merge(recipe.inputs())) {
            if (ingredient.exactProduct() != null) {
                return failure(FailureKind.EXACT_PRODUCT_UNSUPPORTED, ingredient.exactProduct(), path,
                        "component-sensitive inputs require an exact-product stock view");
            }
            Search input = satisfyAlternatives(context, current, ingredient.itemIds(),
                    ingredient.count() * batches, depth, path);
            if (!input.succeeded()) return input;
            current = input.state();
        }
        return Search.success(current);
    }

    private static Search satisfyAlternatives(Context context, State state,
                                              List<ResourceLocation> alternatives, int count,
                                              int depth, LinkedHashSet<ResourceLocation> path) {
        if (alternatives == null || alternatives.isEmpty()) {
            return failure(FailureKind.NO_RECIPE, null, path, "ingredient has no alternatives");
        }
        State base = state.copy();
        int remaining = count;
        for (ResourceLocation alternative : alternatives) {
            int held = base.stock().getOrDefault(alternative, 0);
            int used = Math.min(held, remaining);
            if (used > 0) {
                setCount(base.stock(), alternative, held - used);
                remaining -= used;
            }
        }
        if (remaining == 0) return Search.success(base);

        State best = null;
        Failure bestFailure = null;
        for (ResourceLocation alternative : alternatives) {
            State branch = base.copy();
            Search ensured = ensure(context, branch, alternative, remaining, depth, path);
            if (!ensured.succeeded()) {
                bestFailure = prefer(bestFailure, ensured.failure());
                continue;
            }
            State supplied = ensured.state();
            int held = supplied.stock().getOrDefault(alternative, 0);
            if (held < remaining) continue;
            setCount(supplied.stock(), alternative, held - remaining);
            if (best == null || supplied.cost() < best.cost()) best = supplied;
        }
        return best == null ? new Search(null, bestFailure) : Search.success(best);
    }

    private static Failure prefer(Failure current, Failure candidate) {
        if (current == null) return candidate;
        if (candidate == null) return current;
        return candidate.path().size() > current.path().size() ? candidate : current;
    }

    private static Search failure(FailureKind kind, ResourceLocation item,
                                  Set<ResourceLocation> path, String detail) {
        List<ResourceLocation> trace = new ArrayList<>(path);
        if (item != null && (trace.isEmpty() || !item.equals(trace.get(trace.size() - 1)))) {
            trace.add(item);
        }
        return new Search(null, new Failure(kind, item, trace, detail));
    }

    private static List<Step> aggregate(List<Step> ordered) {
        List<Step> out = new ArrayList<>();
        for (Step step : ordered) {
            if (!out.isEmpty()) {
                Step previous = out.get(out.size() - 1);
                if (previous.recipe().id().equals(step.recipe().id())) {
                    out.set(out.size() - 1,
                            new Step(step.recipe(), previous.batches() + step.batches()));
                    continue;
                }
            }
            out.add(step);
        }
        return List.copyOf(out);
    }

    private static double recipeCost(DiscoveredRecipe recipe, int batches) {
        double perBatch = 1.0d + Math.max(0, recipe.tier()) * 0.5d
                + Math.max(0, recipe.cookTimeTicks()) / 200.0d
                + recipe.inputs().size() * 0.1d;
        if (recipe.requiresTool()) perBatch += 0.25d;
        return perBatch * batches;
    }

    private static int ceilDiv(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static void setCount(Map<ResourceLocation, Integer> stock,
                                 ResourceLocation item, int count) {
        if (count <= 0) stock.remove(item);
        else stock.put(item, count);
    }
}
