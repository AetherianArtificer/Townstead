package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.SelectorType;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Selects living entities across the current host's entire MCA village. */
public final class VillageSelectorType implements SelectorType {

    public static final String KEY = "pheno:village";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Selector parse(JsonObject json) {
        Condition where = json.has("where") ? Conditions.parse(json.get("where")) : null;
        if (json.has("where") && where == null) return null;
        int limit = Math.max(0, GsonHelper.getAsInt(json, "limit", 0));
        Order order = Order.parse(GsonHelper.getAsString(json, "order", "any"));
        return ctx -> select(ctx, where, order, limit);
    }

    private static List<LivingEntity> select(SelectorContext ctx, @Nullable Condition where,
                                             Order order, int limit) {
        Village village = village(ctx);
        if (village == null) return List.of();
        AABB bounds = AABB.of(village.getBox());
        LivingEntity self = ctx.self();
        List<LivingEntity> found = new ArrayList<>(ctx.level().getEntitiesOfClass(
                LivingEntity.class, bounds,
                entity -> entity != self
                        && (where == null || where.test(new ConditionContext(entity)))));
        switch (order) {
            case NEAREST -> found.sort(Comparator.comparingDouble(
                    entity -> entity.distanceToSqr(ctx.pos())));
            case FARTHEST -> found.sort(Comparator.comparingDouble(
                    (LivingEntity entity) -> entity.distanceToSqr(ctx.pos())).reversed());
            case RANDOM -> shuffle(found, ctx);
            case ANY -> { }
        }
        return limit > 0 && found.size() > limit
                ? new ArrayList<>(found.subList(0, limit)) : found;
    }

    private static @Nullable Village village(SelectorContext ctx) {
        if (!(ctx.level() instanceof ServerLevel level)) return null;
        VillageManager manager = VillageManager.get(level);
        if (ctx.villageId() != null) {
            return manager.getOrEmpty(ctx.villageId()).orElse(null);
        }
        return manager.findNearestVillage(ctx.focusBlock(), Village.MERGE_MARGIN).orElse(null);
    }

    private static void shuffle(List<LivingEntity> list, SelectorContext ctx) {
        var random = ctx.self() != null ? ctx.self().getRandom() : ctx.level().getRandom();
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            LivingEntity value = list.get(i);
            list.set(i, list.get(j));
            list.set(j, value);
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
