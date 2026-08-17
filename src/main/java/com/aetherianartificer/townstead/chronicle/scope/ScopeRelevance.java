package com.aetherianartificer.townstead.chronicle.scope;

import com.aetherianartificer.townstead.chronicle.model.Participation;
import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * How much an event matters to a scope. Deliberately not {@code NewsScore}:
 * news answers "is this worth telling today" and decays with age, while
 * relevance answers "is this worth keeping at all" and does not. A death stays
 * significant forever; it just stops being news. Forgetting is the scope's job,
 * through decay and capacity.
 */
public final class ScopeRelevance {

    private static final float ROLE_PRIMARY = 3.0f;
    private static final float ROLE_OTHER = 2.5f;
    private static final float ROLE_WITNESS = 1.5f;
    private static final float ROLE_NONE = 1.0f;
    private static final float FOREIGN_VILLAGE = 0.4f;
    private static final float MIN_MAGNITUDE = 0.2f;

    private ScopeRelevance() {}

    /**
     * The event's own weight, before anyone's point of view: {@code news_value}
     * scaled by how big this occurrence was, and nothing else.
     *
     * <p>Rarity deliberately does not enter here. It decides how often a template
     * is picked, and when it also multiplied significance an author could not make
     * something happen less often without making it matter more, which is how the
     * shipped set drifted out of order.</p>
     */
    public static float significance(ChronicleEventTemplate template, float magnitude) {
        return template.newsValue() * Math.max(MIN_MAGNITUDE, magnitude);
    }

    /**
     * What it is worth to one person. {@code role} is their part in it: the
     * template's primary role, another named role, {@link Participation#ROLE_WITNESS},
     * or null for someone who only heard about it.
     */
    public static float forPerson(ChronicleEventTemplate template, float magnitude,
                                  @Nullable String role, boolean sameVillage) {
        return significance(template, magnitude) * involvement(template, role)
                * (sameVillage ? 1f : FOREIGN_VILLAGE);
    }

    /** What it is worth to a village's public record. */
    public static float forVillage(ChronicleEventTemplate template, float magnitude,
                                   boolean ownVillage) {
        return significance(template, magnitude) * (ownVillage ? 1f : FOREIGN_VILLAGE);
    }

    public static float involvement(ChronicleEventTemplate template, @Nullable String role) {
        if (role == null) return ROLE_NONE;
        if (Participation.ROLE_WITNESS.equals(role)) return ROLE_WITNESS;
        for (ChronicleEventTemplate.RoleSpec spec : template.roles()) {
            if (!spec.id().equals(role) || spec.involvement() == null) continue;
            return declared(spec.involvement());
        }
        return template.primaryRole().id().equals(role) ? ROLE_PRIMARY : ROLE_OTHER;
    }

    /**
     * A role may say what its part was rather than inheriting it from position.
     * Being the first role in an event that merely happened near you is not the
     * same as being the reason it happened.
     */
    private static float declared(String involvement) {
        return switch (involvement) {
            case "witness", "bystander" -> ROLE_WITNESS;
            case "participant" -> ROLE_OTHER;
            case "hearsay" -> ROLE_NONE;
            default -> ROLE_PRIMARY;
        };
    }
}
