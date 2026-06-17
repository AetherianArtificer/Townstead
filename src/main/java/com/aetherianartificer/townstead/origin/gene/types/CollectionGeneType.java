package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Declares a per-entity collection store keyed by this gene's id (Apoli's {@code entity_set}). Any
 * gene or profession references it by id through {@code change_collection},
 * {@code collection_contains}, {@code collection_size}, and {@code for_each}, the same shared-by-id
 * model as {@code resource}. Transient (reset on reload, like resources and cooldowns); Apoli
 * persists its sets, a delta we can lift to all stores together later.
 *
 * <p>{@code of} is the element type ({@code entity}/{@code block}/{@code item}/{@code key}).
 * {@code max} + {@code on_full} bound it ({@code reject} or {@code evict_oldest}); {@code distinct}
 * is reserved for non-entity multisets. {@code on_add}/{@code on_remove} run an action when an
 * element enters/leaves: an entity action (holder as {@code entity()}, member as {@code other()})
 * for {@code entity}; a block action at the position for {@code block}; an entity action on the
 * holder for {@code key}. {@code item} has no hook (its element is an id, not a live target).</p>
 *
 * <p>Members carry an integer tally (a plain set is the tally-is-one case); {@code change_collection}
 * with an {@code amount} adjusts it. {@code forget_after} (ticks) is a default per-member TTL that
 * refreshes whenever the tally changes (an idle window). {@code on_reach} runs an entity action when
 * a member's tally crosses a threshold upward (holder as {@code entity()}, the member as
 * {@code other()}); entity/key collections only.</p>
 */
public final class CollectionGeneType implements GeneType {

    public static final String KEY = "pheno:collection";

    public enum Of { ENTITY, BLOCK, ITEM, KEY }

    public enum OnFull { REJECT, EVICT_OLDEST }

    public record Instance(Of of, boolean distinct, int max, OnFull onFull, int forgetAfter,
                           @Nullable Action onAdd, @Nullable Action onRemove,
                           @Nullable BlockAction onAddBlock, @Nullable BlockAction onRemoveBlock,
                           List<ReachHook> onReach)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }

        public boolean bounded() { return max > 0; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Of of = switch (GsonHelper.getAsString(json, "of", "entity").toLowerCase(java.util.Locale.ROOT)) {
            case "block" -> Of.BLOCK;
            case "item" -> Of.ITEM;
            case "key" -> Of.KEY;
            case "entity" -> Of.ENTITY;
            default -> null;
        };
        if (of == null) return null;
        boolean distinct = GsonHelper.getAsBoolean(json, "distinct", true);
        int max = Math.max(0, GsonHelper.getAsInt(json, "max", 0));
        int forgetAfter = Math.max(0, GsonHelper.getAsInt(json, "forget_after", 0));
        OnFull onFull = "evict_oldest".equalsIgnoreCase(GsonHelper.getAsString(json, "on_full", "reject"))
                ? OnFull.EVICT_OLDEST : OnFull.REJECT;
        // Hooks parse as the kind-appropriate action: entity/key run an entity action, block runs a
        // block action at the position, item has none.
        Action onAdd = null;
        Action onRemove = null;
        BlockAction onAddBlock = null;
        BlockAction onRemoveBlock = null;
        List<ReachHook> onReach = List.of();
        if (of == Of.BLOCK) {
            onAddBlock = json.has("on_add") ? BlockActions.parse(json.get("on_add")) : null;
            onRemoveBlock = json.has("on_remove") ? BlockActions.parse(json.get("on_remove")) : null;
        } else if (of == Of.ENTITY || of == Of.KEY) {
            onAdd = json.has("on_add") ? Actions.parse(json.get("on_add")) : null;
            onRemove = json.has("on_remove") ? Actions.parse(json.get("on_remove")) : null;
            if (json.has("on_reach")) onReach = ReachHook.parseList(json.get("on_reach"));
        }
        return new Instance(of, distinct, max, onFull, forgetAfter, onAdd, onRemove,
                onAddBlock, onRemoveBlock, onReach);
    }
}
