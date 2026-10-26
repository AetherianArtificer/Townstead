package com.aetherianartificer.townstead.pheno.event;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The event a trigger is currently answering: the damage amount and source of a hurt, attack,
 * kill, death or saved death, or the item of an item-use or craft. Pushed by the trigger
 * dispatcher for exactly as long as the trigger's action runs, so values such as
 * {@code pheno:event_damage} can read it. Nested triggers stack; a delayed action runs after the
 * event has ended and reads nothing.
 *
 * <p>Server thread only.</p>
 */
public final class PhenoEvent {

    public record Frame(float amount, @Nullable DamageSource source, ItemStack item) {}

    private static final Deque<Frame> STACK = new ArrayDeque<>();

    private PhenoEvent() {}

    public static @Nullable Frame current() {
        return STACK.peek();
    }

    /** Run {@code body} with {@code frame} as the current event. */
    public static void during(Frame frame, Runnable body) {
        STACK.push(frame);
        try {
            body.run();
        } finally {
            STACK.pop();
        }
    }
}
