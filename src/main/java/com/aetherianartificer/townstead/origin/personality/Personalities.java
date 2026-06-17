package com.aetherianartificer.townstead.origin.personality;

import java.util.List;
import java.util.Map;

/**
 * A personality allow/deny policy declared on an origin-tree node (species, ancestry, lineage or
 * origin). {@code allow} maps a personality reference (a custom {@link PersonalityDef} id like
 * {@code townstead_skeleton:cryptic}, or a bare base-enum name like {@code odd}) to its roll weight.
 * {@code deny} removes references when a more-specific node {@code inherit}s its parent's pool.
 *
 * <p>Resolution ({@link PersonalityResolver}) walks the tree most-specific first: the nearest node
 * with a non-empty policy defines the pool; {@code inherit:true} merges the parent's beneath it
 * (more-specific weights win), and {@code deny} subtracts. No policy anywhere = vanilla MCA.</p>
 */
public record Personalities(Map<String, Integer> allow, List<String> deny, boolean inherit) {

    public static final Personalities EMPTY = new Personalities(Map.of(), List.of(), false);

    public Personalities {
        allow = Map.copyOf(allow);
        deny = List.copyOf(deny);
    }

    public boolean isEmpty() {
        return allow.isEmpty() && deny.isEmpty();
    }
}
