package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import java.util.UUID;

/** Semantic identity filters also work when the counterpart has no loaded entity. */
public record SocialTarget(String name, UUID id) {
    public static SocialTarget parse(String text, boolean allowAny) {
        if (text.equals("other") || text.equals("self") || allowAny && text.equals("any")) return new SocialTarget(text, null);
        try { return new SocialTarget("id", UUID.fromString(text)); }
        catch (IllegalArgumentException ex) { return null; }
    }
    public boolean any() { return name.equals("any"); }
    public UUID resolve(SelectorContext context) {
        return switch (name) {
            case "self" -> context.subject() != null ? context.subject().uuid()
                    : context.self() == null ? null : context.self().getUUID();
            case "other" -> context.otherId();
            default -> id;
        };
    }
}
