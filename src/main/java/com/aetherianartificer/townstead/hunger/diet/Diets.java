package com.aetherianartificer.townstead.hunger.diet;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.gene.types.DietGeneType;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//? if >=1.21 {
import net.minecraft.core.component.DataComponents;
//?}

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loaded diets and the one question every food path asks: what does this item give this
 * mouth? An eater without a resolvable diet gets vanilla behavior, so a Root that never names a
 * diet eats exactly as before.
 */
public final class Diets {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Diets");
    private static final String DEFAULT_NAMESPACE = "townstead";
    private static volatile Map<ResourceLocation, List<Diet.Food>> resolved = Map.of();
    private static volatile Map<ResourceLocation, Diet> diets = Map.of();

    private Diets() {}

    /** What eating one item yields. {@code nutrition} null means the item's own food values. */
    public record Nourishment(@Nullable Integer nutrition, float saturation,
                              @Nullable ResourceLocation remainder, Diet.Food food) {
        public boolean nativeValues() {
            return nutrition == null;
        }
    }

    public static @Nullable Diet diet(ResourceLocation id) {
        return diets.get(id);
    }

    /** The diet an eater's expressed diet gene names, or null for vanilla eating. */
    public static @Nullable Diet dietOf(@Nullable LivingEntity eater) {
        if (eater == null || diets.isEmpty() || !ExpressedGenes.canCarry(eater)) return null;
        for (DietGeneType.Instance gene : ExpressedGenes.instancesOf(eater, DietGeneType.Instance.class)) {
            if (gene.disablesHunger()) return null;
            ResourceLocation id = idOf(gene.diet());
            return id == null ? null : diets.get(id);
        }
        return null;
    }

    /** A bare diet word ({@code "carnivore"}) names the built-in file of that name. */
    static @Nullable ResourceLocation idOf(String diet) {
        if (diet == null || diet.isBlank()) return null;
        String raw = diet.trim().toLowerCase(java.util.Locale.ROOT);
        return ResourceLocation.tryParse(raw.contains(":") ? raw : DEFAULT_NAMESPACE + ":" + raw);
    }

    /**
     * What {@code stack} gives {@code eater}, or null when their diet refuses it. With no diet,
     * any native food is accepted at its own values.
     */
    public static @Nullable Nourishment nourishment(@Nullable LivingEntity eater, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Diet diet = dietOf(eater);
        boolean nativeFood = foodProperties(stack) != null;
        if (diet == null) return nativeFood ? NATIVE : null;
        ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return nourishment(resolved.getOrDefault(diet.id(), List.of()), item,
                tag -> stack.is(TagKey.create(Registries.ITEM, tag)), nativeFood);
    }

    private static final Nourishment NATIVE = new Nourishment(null, 0f, null,
            new Diet.Food(true, Set.of(), Set.of(), Set.of(), Set.of(), null, 0f, null, null));

    static @Nullable Nourishment nourishment(List<Diet.Food> foods, ResourceLocation item,
                                             java.util.function.Predicate<ResourceLocation> inTag,
                                             boolean nativeFood) {
        for (Diet.Food food : foods) {
            if (food.matches(item, inTag, nativeFood)) {
                return new Nourishment(food.nutrition(), food.saturation(), food.remainder(), food);
            }
        }
        return null;
    }

    /** The authored leftover of a diet food, or empty (native foods keep their own remainders). */
    public static ItemStack remainder(Nourishment nourishment) {
        if (nourishment.remainder() == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.getOptional(nourishment.remainder());
        return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /** Whether a player of {@code eater}'s diet must refuse this native food. */
    public static boolean refusesNativeFood(LivingEntity eater, ItemStack stack) {
        Diet diet = dietOf(eater);
        return diet != null && diet.players() && foodProperties(stack) != null
                && nourishment(eater, stack) == null;
    }

    static @Nullable FoodProperties foodProperties(ItemStack stack) {
        //? if >=1.21 {
        return stack.get(DataComponents.FOOD);
        //?} else {
        /*return stack.getFoodProperties(null);
        *///?}
    }

    /** Flattens {@code includes} depth-first after each diet's own foods; cycles are cut. */
    static Map<ResourceLocation, List<Diet.Food>> flatten(Map<ResourceLocation, Diet> loaded) {
        Map<ResourceLocation, List<Diet.Food>> out = new LinkedHashMap<>();
        for (Diet diet : loaded.values()) {
            List<Diet.Food> foods = new ArrayList<>();
            collect(diet, loaded, new HashSet<>(), foods);
            out.put(diet.id(), List.copyOf(foods));
        }
        return out;
    }

    private static void collect(Diet diet, Map<ResourceLocation, Diet> loaded, Set<ResourceLocation> seen,
                                List<Diet.Food> out) {
        if (!seen.add(diet.id())) return;
        out.addAll(diet.foods());
        for (ResourceLocation include : diet.includes()) {
            Diet next = loaded.get(include);
            if (next == null) {
                LOGGER.warn("Diet {} includes unknown diet {}", diet.id(), include);
                continue;
            }
            collect(next, loaded, seen, out);
        }
    }

    static void replace(Map<ResourceLocation, Diet> loaded) {
        diets = Map.copyOf(loaded);
        resolved = Map.copyOf(flatten(loaded));
    }

    public static final class Loader extends SimpleJsonResourceReloadListener {
        public Loader() { super(new Gson(), "diet"); }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, Diet> loaded = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    if (json.has("mods") && ModGate.evaluate(json.get("mods")) == null) {
                        throw new IllegalArgumentException("'mods' is malformed");
                    }
                    if (!ModGate.allows(json)) continue;
                    loaded.put(entry.getKey(), Diet.parse(entry.getKey(), json));
                } catch (Exception exception) {
                    LOGGER.warn("Diet {} rejected: {}", entry.getKey(), exception.getMessage());
                }
            }
            replace(loaded);
            LOGGER.info("Loaded {} diets", loaded.size());
        }
    }
}
