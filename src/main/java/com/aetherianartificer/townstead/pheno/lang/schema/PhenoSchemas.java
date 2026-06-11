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
        NodeSchemas.register(NodeSchema.of("townstead_origins:trigger", NodeDomain.GENE)
                .doc("Runs an action when a life-cycle event fires (attack, hurt, kill, land, ...).")
                .field(required("trigger", PhenoType.STRING).doc("Event: when_attack, when_hurt, when_kill, ..."))
                .field(of("target", PhenoType.STRING).doc("self or other (the counterpart entity)."))
                .field(of("action", PhenoType.ACTION))
                .field(of("condition", PhenoType.CONDITION))
                .primaryChild("action").build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:active_ability", NodeDomain.GENE)
                .doc("An action the holder triggers from an Origin Ability key slot.")
                .field(of("action", PhenoType.ACTION))
                .field(of("condition", PhenoType.CONDITION))
                .field(of("cooldown", PhenoType.DURATION))
                .field(of("slot", PhenoType.INT))
                .field(of("ai_trigger", PhenoType.STRING))
                .field(of("resource_cost", PhenoType.OBJECT))
                .primaryChild("action").build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:ability", NodeDomain.GENE)
                .doc("Grants an innate ability (climbing, night vision, fire immunity, ...).")
                .field(required("ability", PhenoType.STRING))
                .field(of("mode", PhenoType.STRING).doc("passive or toggle."))
                .field(of("slot", PhenoType.INT))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:attribute", NodeDomain.GENE)
                .doc("Adds a vanilla attribute modifier, optionally gated by a condition.")
                .field(required("attribute", PhenoType.ID))
                .field(required("amount", PhenoType.FLOAT))
                .field(of("operation", PhenoType.STRING))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:modifier", NodeDomain.GENE)
                .doc("Scales a server mechanic: healing, damage dealt, break speed, jump, exhaustion.")
                .field(required("modifier", PhenoType.STRING))
                .field(of("value", PhenoType.FLOAT))
                .field(of("operation", PhenoType.STRING).doc("multiply (default) or add."))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:damage_modifier", NodeDomain.GENE)
                .doc("Scales incoming damage of a tag/type; 0 is immunity.")
                .field(required("modifier", PhenoType.FLOAT))
                .field(of("damage_tag", PhenoType.TAG_OR_ID))
                .field(of("damage_type", PhenoType.ID))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:glow", NodeDomain.GENE)
                .doc("Makes the holder glow, optionally only while a condition holds.")
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:particle", NodeDomain.GENE)
                .doc("Emits a particle on an interval, optionally gated by a condition.")
                .field(required("particle", PhenoType.ID))
                .field(of("count", PhenoType.INT))
                .field(of("interval", PhenoType.DURATION))
                .field(of("condition", PhenoType.CONDITION)).build());

        NodeSchemas.register(NodeSchema.of("townstead_origins:action_over_time", NodeDomain.GENE)
                .doc("Runs an action on a fixed interval.")
                .field(of("action", PhenoType.ACTION))
                .field(of("interval", PhenoType.DURATION))
                .field(of("condition", PhenoType.CONDITION))
                .primaryChild("action").build());

        // --- Action wrappers (the context transitions and meta combinators) ---
        NodeSchemas.register(NodeSchema.of("townstead_origins:actor_action", NodeDomain.ACTION)
                .doc("Runs the inner action on the actor (self).")
                .field(of("action", PhenoType.ACTION)).primaryChild("action").build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:target_action", NodeDomain.ACTION)
                .doc("Runs the inner action on the other entity (target).")
                .field(of("action", PhenoType.ACTION)).primaryChild("action").build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:invert", NodeDomain.ACTION)
                .doc("Swaps actor and target for the inner action.")
                .field(of("action", PhenoType.ACTION)).primaryChild("action").build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:block_action", NodeDomain.ACTION)
                .doc("Runs a block action at the actor's position.")
                .field(of("block_action", PhenoType.BLOCK_ACTION)).primaryChild("block_action").build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:equipped_item_action", NodeDomain.ACTION)
                .doc("Runs an item action on an equipped slot.")
                .field(of("slot", PhenoType.STRING))
                .field(of("item_action", PhenoType.ITEM_ACTION)).primaryChild("item_action").build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:and", NodeDomain.ACTION)
                .doc("Runs all listed actions in order.")
                .field(of("actions", PhenoType.ACTION).asList()).primaryChild("actions").build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:chance", NodeDomain.ACTION)
                .doc("Runs the inner action with a probability.")
                .field(of("chance", PhenoType.PERCENT))
                .field(of("action", PhenoType.ACTION)).primaryChild("action").build());

        // --- Leaf actions with normalizable units ---
        NodeSchemas.register(NodeSchema.of("townstead_origins:apply_effect", NodeDomain.ACTION)
                .doc("Applies a status effect.")
                .field(required("effect", PhenoType.ID))
                .field(of("duration", PhenoType.DURATION))
                .field(of("amplifier", PhenoType.INT)).build());
        NodeSchemas.register(NodeSchema.of("townstead_origins:item_cooldown", NodeDomain.ACTION)
                .doc("Puts an item on cooldown.")
                .field(of("item", PhenoType.ID))
                .field(of("cooldown", PhenoType.DURATION)).build());

        // --- Block actions ---
        NodeSchemas.register(NodeSchema.of("townstead_origins:offset", NodeDomain.BLOCK_ACTION)
                .doc("Shifts the target position before running the inner block action.")
                .field(of("x", PhenoType.INT))
                .field(of("y", PhenoType.INT))
                .field(of("z", PhenoType.INT))
                .field(of("block_action", PhenoType.BLOCK_ACTION)).primaryChild("block_action").build());

        // --- Consolidated condition ---
        NodeSchemas.register(NodeSchema.of("townstead_origins:environment", NodeDomain.CONDITION)
                .doc("One block of weather/exposure/time/biome/dimension/effects (AND across, OR within).")
                .field(of("weather", PhenoType.STRING).asList())
                .field(of("exposure", PhenoType.STRING).asList())
                .field(of("time", PhenoType.STRING).asList())
                .field(of("biome", PhenoType.TAG_OR_ID).asList())
                .field(of("dimension", PhenoType.ID).asList())
                .field(of("effects", PhenoType.OBJECT)).build());
    }
}
