package com.aetherianartificer.townstead.pheno.action;

import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses an action JSON element into an {@link Action}. An array runs every action
 * in order; an object dispatches by {@code "type"} to a registered {@link ActionType}, then, if
 * the object carries an {@code on}, the action is wrapped to run once per selected target with
 * that target as the focus ({@code entity()}), the previous focus as {@code other()}, and the
 * power-bearer preserved as {@code origin()}. Returns {@code null} for an unknown or malformed
 * action (or selector) so a gene with no usable action is rejected.
 */
public final class Actions {

    private Actions() {}

    @Nullable
    public static Action parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            List<Action> actions = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                Action action = parse(child);
                if (action == null) return null;
                actions.add(action);
            }
            if (actions.isEmpty()) return null;
            return ctx -> actions.forEach(a -> a.run(ctx));
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        Action inner = ActionTypes.get(GsonHelper.getAsString(json, "type", ""))
                .map(t -> t.parse(json)).orElse(null);
        if (inner == null) return null;
        if (!json.has("on")) return inner;
        Selector selector = Selectors.parse(json.get("on"));
        if (selector == null) return null;
        Action core = inner;
        return ctx -> {
            for (LivingEntity target : selector.select(SelectorContext.of(ctx))) {
                core.run(new ActionContext(target, ctx.entity(), ctx.origin()));
            }
        };
    }
}
