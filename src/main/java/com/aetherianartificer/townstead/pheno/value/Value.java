package com.aetherianartificer.townstead.pheno.value;

import com.aetherianartificer.townstead.pheno.selector.SelectorContext;

/**
 * A number that may be a literal or computed from a selection (the minimal expression hook). A
 * numeric field like {@code amount} parses to a {@link Value}, so {@code "amount": 4} and
 * {@code "amount": { "type": "pheno:count", "on": {...} }} are both valid. Resolved against the
 * current focus, so a count inside an {@code on} block anchors to that target.
 */
@FunctionalInterface
public interface Value {

    double get(SelectorContext ctx);
}
