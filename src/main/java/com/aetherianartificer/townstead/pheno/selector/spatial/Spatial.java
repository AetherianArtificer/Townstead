package com.aetherianartificer.townstead.pheno.selector.spatial;

import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Function;

/**
 * Parses the spatial sources structurally (no {@code type} needed, matching the rest of the
 * authoring): a string is a place ({@code here}, {@code above}/{@code below}, the facing-relative
 * {@code in_front}/{@code behind}/{@code left}/{@code right}, {@code looking_at}); an object with
 * {@code radius} is a region; an object with {@code offset}/{@code at} is a place. Returns
 * {@code null} when the element is not spatial, so a caller can fall through to roles / typed
 * selectors. Kind-agnostic: the same {@link Region} feeds entity and block selection.
 */
public final class Spatial {

    private static final double LOOK_REACH = 6.0;

    private Spatial() {}

    @Nullable
    public static Region parse(JsonElement element) {
        if (element == null) return null;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            Function<SelectorContext, BlockPos> place = place(element.getAsString());
            return place == null ? null : new Region(place, 0, 0, 0, 0);
        }
        if (!element.isJsonObject()) return null;
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("radius")) {
            Function<SelectorContext, BlockPos> around = obj.has("around")
                    ? anchor(obj.get("around")) : Spatial::here;
            if (around == null) return null;
            int[] r = radius(obj.get("radius"));
            boolean sphere = obj.get("radius").isJsonPrimitive();
            return new Region(around, r[0], r[1], r[2], sphere ? r[0] : 0);
        }
        Function<SelectorContext, BlockPos> place = anchor(obj);
        return place == null ? null : new Region(place, 0, 0, 0, 0);
    }

    /** A place anchor (a string place name, or an {@code offset}/{@code at} object). */
    @Nullable
    private static Function<SelectorContext, BlockPos> anchor(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            return place(element.getAsString());
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("offset")) {
                int[] o = triple(obj.get("offset"));
                return ctx -> ctx.focusBlock().offset(o[0], o[1], o[2]);
            }
            if (obj.has("at")) {
                int[] a = triple(obj.get("at"));
                return ctx -> new BlockPos(a[0], a[1], a[2]);
            }
        }
        return null;
    }

    @Nullable
    private static Function<SelectorContext, BlockPos> place(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "here" -> Spatial::here;
            case "above" -> ctx -> ctx.focusBlock().above();
            case "below" -> ctx -> ctx.focusBlock().below();
            case "in_front", "front" -> facing(ctx -> ctx.self().getDirection());
            case "behind", "back" -> facing(ctx -> ctx.self().getDirection().getOpposite());
            case "left" -> facing(ctx -> ctx.self().getDirection().getCounterClockWise());
            case "right" -> facing(ctx -> ctx.self().getDirection().getClockWise());
            case "looking_at", "aim" -> Spatial::lookingAt;
            default -> null;
        };
    }

    private static BlockPos here(SelectorContext ctx) {
        return ctx.focusBlock();
    }

    /** A facing-relative neighbour: yields null when there is no entity to take a facing from. */
    private static Function<SelectorContext, BlockPos> facing(Function<SelectorContext, Direction> dir) {
        return ctx -> ctx.self() == null ? null : ctx.focusBlock().relative(dir.apply(ctx));
    }

    @Nullable
    private static BlockPos lookingAt(SelectorContext ctx) {
        LivingEntity self = ctx.self();
        if (self == null) return null;
        Vec3 eyes = self.getEyePosition();
        Vec3 end = eyes.add(self.getViewVector(1.0f).scale(LOOK_REACH));
        HitResult hit = self.level().clip(new ClipContext(eyes, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, self));
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                ? block.getBlockPos() : null;
    }

    private static int[] radius(JsonElement element) {
        if (element.isJsonArray()) return triple(element);
        int r = Math.max(0, element.getAsInt());
        return new int[]{r, r, r};
    }

    private static int[] triple(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() >= 3) return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt(), arr.get(2).getAsInt()};
        }
        return new int[]{0, 0, 0};
    }
}
