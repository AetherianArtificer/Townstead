package com.aetherianartificer.townstead.root.attachment;

/**
 * The rules a part's health follows. Plain numbers, so they can be tested. A part is wounded once its
 * pool falls to {@code woundedAt}, and lost at zero if it can be cut off by what hit it. Otherwise it
 * stops at a sliver and stays wounded.
 */
public final class PartWounds {

    /** The pool floor for damage that can't cut the part off. */
    private static final float SLIVER = 0.01f;

    public record State(float health, int stage) {}

    private PartWounds() {}

    public static State hit(State before, AttachmentBody.Part part, float amount, boolean severing) {
        if (before.stage() == AttachmentBody.Part.LOST) return before;
        float health = before.health() - amount;
        if (health <= 0f) {
            if (part.severable() && severing) return new State(0f, AttachmentBody.Part.LOST);
            health = part.health() * SLIVER;
        }
        boolean wounded = health <= part.health() * part.woundedAt();
        return new State(health, wounded ? AttachmentBody.Part.WOUNDED : before.stage());
    }

    /** A wounded part healing: it mends once the pool is back above the wounded line. */
    public static State heal(State before, AttachmentBody.Part part, float amount) {
        if (before.stage() == AttachmentBody.Part.LOST || before.health() >= part.health()) return before;
        float health = Math.min(part.health(), before.health() + amount);
        boolean mended = before.stage() == AttachmentBody.Part.WOUNDED && health > part.health() * part.woundedAt();
        return new State(health, mended ? AttachmentBody.Part.INTACT : before.stage());
    }

    /** A lost part growing back comes back hurt, at the wounded line, and mends from there. */
    public static State regrown(AttachmentBody.Part part) {
        return new State(part.health() * part.woundedAt(), AttachmentBody.Part.WOUNDED);
    }
}
