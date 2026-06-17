package com.aetherianartificer.townstead.pheno.lang.schema;

import com.aetherianartificer.townstead.pheno.lang.validate.NodeDomain;

import static com.aetherianartificer.townstead.pheno.lang.schema.FieldSchema.of;
import static com.aetherianartificer.townstead.pheno.lang.schema.FieldSchema.required;

/**
 * Registers the {@link NodeSchema}s the normalizer, validator, and doc generator read. Schemas
 * are additive: this covers the sugar-bearing nodes (so {@code do}/{@code with}/units lower
 * correctly) and the common genes. Types without a schema still parse and resolve; they simply
 * miss field-level normalization and generated field docs until a schema is added here.
 */
public final class PhenoSchemas {

    private PhenoSchemas() {}

    public static void registerAll() {
        // --- Gene types (their behavior tree starts here) ---
        NodeSchemas.register(NodeSchema.of("pheno:trigger", NodeDomain.GENE)
                .doc("Runs an action when a life-cycle event fires (attack, hurt, kill, land, ...).")
                .field(required("trigger", PhenoType.STRING).doc("Event: when_attack, when_hurt, when_kill, ..."))
                .field(of("target", PhenoType.STRING).doc("self or other (the counterpart entity)."))
                .field(required("action", PhenoType.ACTION))
                .field(of("condition", PhenoType.CONDITION))
                .primaryChild("action").build());

        NodeSchemas.register(NodeSchema.of("pheno:active_ability", NodeDomain.GENE)
                .doc("An action the holder triggers from an Origin Ability key slot.")
                .field(required("action", PhenoType.ACTION))
                .field(of("condition", PhenoType.CONDITION))
                .field(of("cooldown", PhenoType.DURATION))
                .field(of("slot", PhenoType.INT))
                .field(of("ai_trigger", PhenoType.STRING))
                .field(of("resource_cost", PhenoType.OBJECT))
                .primaryChild("action").build());

        NodeSchemas.register(NodeSchema.of("pheno:ability", NodeDomain.GENE)
                .doc("Grants an innate ability (climbing, night vision, fire immunity, ...).")
                .field(required("ability", PhenoType.STRING))
                .field(of("mode", PhenoType.STRING).doc("passive or toggle."))
                .field(of("slot", PhenoType.INT))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("pheno:attribute", NodeDomain.GENE)
                .doc("Adds a vanilla attribute modifier, optionally gated by a condition.")
                .field(required("attribute", PhenoType.ID))
                .field(of("amount", PhenoType.FLOAT))
                .field(of("operation", PhenoType.STRING))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("pheno:modifier", NodeDomain.GENE)
                .doc("Scales a server mechanic: healing, damage_dealt, break_speed, jump, exhaustion, "
                        + "xp_gain, food, projectile_damage, breeding_cooldown, status_effect_duration/"
                        + "amplifier (with effect), enchantment_level (with enchantment, shape only). "
                        + "Folds onto the live base through the capability layer.")
                .field(of("target", PhenoType.STRING).doc("Intercept point (v1 alias: modifier)."))
                .field(of("modifier", PhenoType.STRING).doc("v1 name for target."))
                .field(of("value", PhenoType.FLOAT).doc("v1 scalar paired with operation."))
                .field(of("operation", PhenoType.STRING).doc("multiply (default), add, set, min, max."))
                .field(of("effect", PhenoType.ID).doc("Discriminator for status_effect_* targets."))
                .field(of("enchantment", PhenoType.ID).doc("Discriminator for enchantment_level."))
                .field(of("condition", PhenoType.CONDITION))
                .field(of("when", PhenoType.CONDITION).doc("v2 alias for condition.")).build());

        NodeSchemas.register(NodeSchema.of("pheno:damage_modifier", NodeDomain.GENE)
                .doc("Scales incoming damage of a tag/type; 0 is immunity. Needs one of "
                        + "damage_tag/damage_type/damage_condition.")
                .field(of("modifier", PhenoType.FLOAT))
                .field(of("damage_tag", PhenoType.TAG_OR_ID))
                .field(of("damage_type", PhenoType.ID))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("pheno:glow", NodeDomain.GENE)
                .doc("Makes the holder glow, optionally only while a condition holds.")
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("pheno:particle", NodeDomain.GENE)
                .doc("Emits a particle on an interval, optionally gated by a condition.")
                .field(required("particle", PhenoType.ID))
                .field(of("count", PhenoType.INT))
                .field(of("interval", PhenoType.DURATION))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("pheno:action_over_time", NodeDomain.GENE)
                .doc("Runs an action on a fixed interval.")
                .field(required("action", PhenoType.ACTION))
                .field(of("interval", PhenoType.DURATION))
                .field(of("condition", PhenoType.CONDITION))
                .primaryChild("action").build());

        NodeSchemas.register(NodeSchema.of("pheno:resource", NodeDomain.GENE)
                .doc("A meter in [min,max] that regenerates regen per regen_interval ticks; spent by "
                        + "abilities and moved by change_resource. Declarable standalone or inline in a "
                        + "gene's root resources section (keyed by name, granted alongside that gene).")
                .field(of("min", PhenoType.INT))
                .field(of("max", PhenoType.INT))
                .field(of("start", PhenoType.INT))
                .field(of("regen", PhenoType.INT))
                .field(of("regen_interval", PhenoType.INT))
                .field(of("color", PhenoType.COLOR))
                .field(of("on_reach", PhenoType.OBJECT).asList()
                        .doc("Edge-triggered { at|every, do, then } when the meter crosses a threshold upward.")).build());

        // --- Action wrappers (the context transitions and meta combinators) ---
        NodeSchemas.register(NodeSchema.of("pheno:actor_action", NodeDomain.ACTION)
                .doc("Runs the inner action on the actor (self).")
                .field(required("action", PhenoType.ACTION)).primaryChild("action").build());
        NodeSchemas.register(NodeSchema.of("pheno:target_action", NodeDomain.ACTION)
                .doc("Runs the inner action on the other entity (target).")
                .field(required("action", PhenoType.ACTION)).primaryChild("action").build());
        NodeSchemas.register(NodeSchema.of("pheno:invert", NodeDomain.ACTION)
                .doc("Swaps actor and target for the inner action.")
                .field(required("action", PhenoType.ACTION)).primaryChild("action").build());
        NodeSchemas.register(NodeSchema.of("pheno:block_action", NodeDomain.ACTION)
                .doc("Runs a block action at the actor's position.")
                .field(required("block_action", PhenoType.BLOCK_ACTION)).primaryChild("block_action").build());
        NodeSchemas.register(NodeSchema.of("pheno:equipped_item_action", NodeDomain.ACTION)
                .doc("Runs an item action on an equipped slot.")
                .field(of("slot", PhenoType.STRING))
                .field(required("item_action", PhenoType.ITEM_ACTION)).primaryChild("item_action").build());
        NodeSchemas.register(NodeSchema.of("pheno:and", NodeDomain.ACTION)
                .doc("Runs all listed actions in order.")
                .field(required("actions", PhenoType.ACTION).asList()).primaryChild("actions").build());
        NodeSchemas.register(NodeSchema.of("pheno:chance", NodeDomain.ACTION)
                .doc("Runs the inner action with a probability.")
                .field(of("chance", PhenoType.PERCENT))
                .field(required("action", PhenoType.ACTION)).primaryChild("action").build());

        // --- Leaf actions with normalizable units ---
        NodeSchemas.register(NodeSchema.of("pheno:apply_effect", NodeDomain.ACTION)
                .doc("Applies a status effect.")
                .field(required("effect", PhenoType.ID))
                .field(of("duration", PhenoType.DURATION))
                .field(of("amplifier", PhenoType.INT)).build());
        NodeSchemas.register(NodeSchema.of("pheno:item_cooldown", NodeDomain.ACTION)
                .doc("Puts an item on cooldown.")
                .field(of("item", PhenoType.ID))
                .field(of("cooldown", PhenoType.DURATION)).build());

        NodeSchemas.register(NodeSchema.of("pheno:jump", NodeDomain.ACTION)
                .doc("Makes the entity jump (the vanilla impulse, respecting Jump Boost), scaled by "
                        + "strength; clears fall distance so a mid-air jump banks no fall damage.")
                .field(of("strength", PhenoType.FLOAT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:emit_game_event", NodeDomain.ACTION)
                .doc("Emits a vanilla game event from the entity (Apoli emit_game_event).")
                .field(required("event", PhenoType.ID)).build());

        NodeSchemas.register(NodeSchema.of("pheno:zombify_villager", NodeDomain.ACTION)
                .doc("Turns the entity (any Mob, incl. MCA villagers) into a zombie villager "
                        + "(Apugli zombify_villager).").build());

        // --- Raycast family (ray is a selector; these are its action companions) ---
        NodeSchemas.register(NodeSchema.of("pheno:at", NodeDomain.ACTION)
                .doc("Runs a block action at blocks chosen from an entity context (a ray's block hit, a "
                        + "place, a region): the bridge from an entity action to a block action.")
                .field(required("blocks", PhenoType.OBJECT).doc("A block selector (e.g. { type: pheno:ray, stop_on: block })."))
                .field(required("do", PhenoType.BLOCK_ACTION)).primaryChild("do").build());

        NodeSchemas.register(NodeSchema.of("pheno:beam", NodeDomain.ACTION)
                .doc("Draws a line of particles from the caster's eyes along a ray to its impact point "
                        + "(the beam half of Apoli/Apugli raycast).")
                .field(required("particle", PhenoType.ID))
                .field(of("spacing", PhenoType.FLOAT))
                .field(of("distance", PhenoType.FLOAT))
                .field(of("direction", PhenoType.FLOAT).asList())
                .field(of("space", PhenoType.STRING).doc("world (default) or local (look-relative)."))
                .field(of("toward", PhenoType.STRING).doc("A role to aim at (e.g. target).")).build());

        NodeSchemas.register(NodeSchema.of("pheno:cloud", NodeDomain.ACTION)
                .doc("Spawns a lingering field that runs do on each entity inside it every cycle, for "
                        + "duration, growing/shrinking over time and on use (Apoli spawn_effect_cloud, "
                        + "Apugli spawn_custom_effect_cloud). do runs with the inside entity as entity() "
                        + "and the owner as other().")
                .field(of("radius", PhenoType.FLOAT))
                .field(of("grow_per_tick", PhenoType.FLOAT))
                .field(of("shrink_on_use", PhenoType.FLOAT))
                .field(of("wait_time", PhenoType.INT))
                .field(of("duration", PhenoType.INT))
                .field(of("reapply_delay", PhenoType.INT))
                .field(of("height", PhenoType.FLOAT))
                .field(of("particle", PhenoType.ID))
                .field(of("where", PhenoType.BIENTITY_CONDITION).doc("A gate on (owner, inside)."))
                .field(required("do", PhenoType.ACTION)).primaryChild("do").build());

        // --- Block actions ---
        NodeSchemas.register(NodeSchema.of("pheno:offset", NodeDomain.BLOCK_ACTION)
                .doc("Shifts the target position before running the inner block action.")
                .field(of("x", PhenoType.INT))
                .field(of("y", PhenoType.INT))
                .field(of("z", PhenoType.INT))
                .field(required("block_action", PhenoType.BLOCK_ACTION)).primaryChild("block_action").build());

        // --- Consolidated condition ---
        NodeSchemas.register(NodeSchema.of("pheno:environment", NodeDomain.CONDITION)
                .doc("One block of weather/exposure/time/biome/dimension/effects (AND across, OR within).")
                .field(of("weather", PhenoType.STRING).asList())
                .field(of("exposure", PhenoType.STRING).asList())
                .field(of("time", PhenoType.STRING).asList())
                .field(of("biome", PhenoType.TAG_OR_ID).asList())
                .field(of("dimension", PhenoType.ID).asList())
                .field(of("effects", PhenoType.OBJECT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:step_height", NodeDomain.GENE)
                .doc("Raises how high the entity steps up by amount (Apugli step_height's upper_height), "
                        + "resolving the per-version step-height attribute.")
                .field(of("amount", PhenoType.FLOAT))
                .field(of("condition", PhenoType.CONDITION)).build());

        // --- Collection store (Apoli entity_set family) ---
        NodeSchemas.register(NodeSchema.of("pheno:collection", NodeDomain.GENE)
                .doc("Declares a persistent per-entity collection store keyed by this gene's id, shared "
                        + "by id (Apoli entity_set). on_add/on_remove run with the holder as entity() and "
                        + "the element as other().")
                .field(of("of", PhenoType.STRING).doc("Element type: entity (block/item/key reserved)."))
                .field(of("distinct", PhenoType.BOOL))
                .field(of("max", PhenoType.INT).doc("Capacity; 0 = unbounded."))
                .field(of("on_full", PhenoType.STRING).doc("reject (default) or evict_oldest."))
                .field(of("forget_after", PhenoType.INT)
                        .doc("Ticks of idle before a member is forgotten; refreshes when its tally changes."))
                .field(of("on_add", PhenoType.ACTION))
                .field(of("on_remove", PhenoType.ACTION))
                .field(of("on_reach", PhenoType.OBJECT).asList()
                        .doc("Edge-triggered { at|every, do } when a member's tally crosses a threshold upward.")).build());

        NodeSchemas.register(NodeSchema.of("pheno:change_collection", NodeDomain.ACTION)
                .doc("Mutates a collection on the actor (add_to_set / remove_from_set / clear; Apugli "
                        + "change_hits_on_target). The element is the contextual other(), aimed with target_action / on.")
                .field(required("collection", PhenoType.ID))
                .field(of("operation", PhenoType.STRING).doc("add (default), set, remove, clear."))
                .field(of("amount", PhenoType.INT).doc("Tally delta (add) or value (set); omit to toggle membership."))
                .field(of("time_limit", PhenoType.INT).doc("Ticks until the entry expires; omit for the gene's forget_after.")).build());

        NodeSchemas.register(NodeSchema.of("pheno:collection_size", NodeDomain.CONDITION)
                .doc("Compares the holder's collection size (Apoli set_size).")
                .field(required("collection", PhenoType.ID))
                .field(of("comparison", PhenoType.STRING))
                .field(of("compare_to", PhenoType.INT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:collection_contains", NodeDomain.BIENTITY_CONDITION)
                .doc("Tests whether the target is in the actor's collection (Apoli in_set).")
                .field(required("collection", PhenoType.ID)).build());

        NodeSchemas.register(NodeSchema.of("pheno:collection_count", NodeDomain.BIENTITY_CONDITION)
                .doc("Compares the target's tally in the actor's collection (Apugli hits_on_target).")
                .field(required("collection", PhenoType.ID))
                .field(of("comparison", PhenoType.STRING))
                .field(of("compare_to", PhenoType.INT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:dimensions", NodeDomain.CONDITION)
                .doc("The entity's live bounding-box size within [min,max] (Apugli dimensions, the "
                        + "physical size, not the world dimension).")
                .field(of("which", PhenoType.STRING).doc("width, height, or both (default)."))
                .field(of("min", PhenoType.FLOAT))
                .field(of("max", PhenoType.FLOAT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:compare_dimensions", NodeDomain.BIENTITY_CONDITION)
                .doc("The actor's live size against the target's, per axis (Apugli compare_dimensions).")
                .field(of("which", PhenoType.STRING).doc("width, height, or both (default)."))
                .field(of("comparison", PhenoType.STRING)).build());

        NodeSchemas.register(NodeSchema.of("pheno:fluid_height", NodeDomain.CONDITION)
                .doc("The height of the given fluid tag the entity stands in, within [min,max] (Apoli fluid_height).")
                .field(required("fluid", PhenoType.ID))
                .field(of("min", PhenoType.FLOAT))
                .field(of("max", PhenoType.FLOAT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:passenger_recursive", NodeDomain.CONDITION)
                .doc("Compares the recursive passenger count against compare_to (Apoli passenger_recursive).")
                .field(of("comparison", PhenoType.STRING))
                .field(of("compare_to", PhenoType.INT))
                .field(of("where", PhenoType.BIENTITY_CONDITION).doc("Optional gate on (holder, passenger).")).build());

        NodeSchemas.register(NodeSchema.of("pheno:scale", NodeDomain.CONDITION)
                .doc("The entity's scale within [min,max] (Apugli/Pehkui scale, read from MCA villager "
                        + "scale factors; 1.0 for non-villagers).")
                .field(of("which", PhenoType.STRING).doc("width (horizontal), height (vertical), or both (default)."))
                .field(of("min", PhenoType.FLOAT))
                .field(of("max", PhenoType.FLOAT)).build());

        NodeSchemas.register(NodeSchema.of("pheno:compare_scales", NodeDomain.BIENTITY_CONDITION)
                .doc("The actor's scale against the target's, per axis (Apugli/Pehkui compare_scales).")
                .field(of("which", PhenoType.STRING).doc("width, height, or both (default)."))
                .field(of("comparison", PhenoType.STRING)).build());

        NodeSchemas.register(NodeSchema.of("pheno:for_each", NodeDomain.ACTION)
                .doc("Runs do once per collection member, holder as entity() and member as other(), gated "
                        + "by where and capped by limit (Apoli action_on_set).")
                .field(required("in", PhenoType.ID))
                .field(of("do", PhenoType.ACTION))
                .field(of("where", PhenoType.BIENTITY_CONDITION))
                .field(of("limit", PhenoType.INT))
                .field(of("order", PhenoType.STRING).doc("oldest_first (default) or newest_first."))
                .primaryChild("do").build());
    }
}
