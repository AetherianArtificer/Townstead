package com.aetherianartificer.townstead.pheno.selector;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.selector.spatial.Region;
import com.aetherianartificer.townstead.pheno.selector.spatial.Spatial;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Parses the {@code on} value into a {@link Selector} of entities. A string is a place (the
 * entities there) or a role; an object with {@code radius}/{@code offset}/{@code at} is a spatial
 * region (the entities in it, optionally {@code where}-filtered, {@code limit}/{@code order}); an
 * array is the union; an object with {@code type} dispatches to a registered {@link SelectorType}
 * ({@code collection}, {@code command}). The shared {@link Spatial} vocabulary also drives block
 * selection, so a region means entities here and blocks in a block action.
 */
public final class Selectors {

    private Selectors() {}

    @Nullable
    public static Selector parse(@Nullable JsonElement element) {
        if (element == null) return null;
        Region region = Spatial.parse(element);
        if (region != null) return entitiesIn(region, element);
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String role = element.getAsString();
            return ctx -> Roles.resolve(role, ctx);
        }
        if (element.isJsonArray()) {
            List<Selector> parts = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                Selector part = parse(child);
                if (part == null) return null;
                parts.add(part);
            }
            return ctx -> {
                LinkedHashSet<LivingEntity> union = new LinkedHashSet<>();
                for (Selector part : parts) union.addAll(part.select(ctx));
                return new ArrayList<>(union);
            };
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            return SelectorTypes.get(GsonHelper.getAsString(obj, "type", ""))
                    .map(t -> t.parse(obj)).orElse(null);
        }
        return null;
    }

    @Nullable
    private static Selector entitiesIn(Region region, JsonElement element) {
        Condition where = null;
        int limit = 0;
        Order order = Order.ANY;
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("where")) {
                where = Conditions.parse(obj.get("where"));
                if (where == null) return null;
            }
            limit = Math.max(0, GsonHelper.getAsInt(obj, "limit", 0));
            order = Order.parse(GsonHelper.getAsString(obj, "order", "any"));
        }
        Condition filter = where;
        int lim = limit;
        Order ord = order;
        return ctx -> {
            AABB bounds = region.bounds(ctx);
            if (bounds == null) return List.of();
            LivingEntity self = ctx.self();
            List<LivingEntity> found = ctx.level().getEntitiesOfClass(LivingEntity.class, bounds,
                    e -> e != self && region.contains(ctx, e.position())
                            && (filter == null || filter.test(new ConditionContext(e))));
            Vec3 from = ctx.pos();
            switch (ord) {
                case NEAREST -> found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
                case FARTHEST -> found.sort(Comparator.comparingDouble((LivingEntity e) -> e.distanceToSqr(from)).reversed());
                case RANDOM -> shuffle(found, ctx);
                case ANY -> { }
            }
            if (lim > 0 && found.size() > lim) return new ArrayList<>(found.subList(0, lim));
            return found;
        };
    }

    private static void shuffle(List<LivingEntity> list, SelectorContext ctx) {
        var random = ctx.self() != null ? ctx.self().getRandom() : ctx.level().getRandom();
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            LivingEntity tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    private enum Order {
        ANY, NEAREST, FARTHEST, RANDOM;

        static Order parse(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "nearest", "closest" -> NEAREST;
                case "farthest", "furthest" -> FARTHEST;
                case "random" -> RANDOM;
                default -> ANY;
            };
        }
    }
}
