package com.aetherianartificer.townstead.work;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Quality read from a produced item itself, not from its recipe. Some outputs carry their own
 * verdict (a pizza's ingredient list yields a taste tier); an appraiser knows how to read one
 * family of outputs, and work-credit sites (villager {@code completeWork} calls and the player
 * hooks) consult the registry so a better product earns more, uniformly, without each engine
 * inventing its own quality math. First matching appraiser wins; no appraiser means the output
 * has no derivable quality and callers fall back to recipe tier.
 */
public final class OutputAppraisal {

    /** {@code quality} scales XP (1 = baseline); {@code label} is a stable lowercase token for counters. */
    public record Appraisal(int quality, String label) {}

    public interface Appraiser {
        @Nullable Appraisal appraise(ItemStack stack);
    }

    private static final List<Appraiser> APPRAISERS = new CopyOnWriteArrayList<>();

    private OutputAppraisal() {}

    public static void register(Appraiser appraiser) {
        if (appraiser != null) APPRAISERS.add(appraiser);
    }

    @Nullable
    public static Appraisal appraise(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        for (Appraiser appraiser : APPRAISERS) {
            Appraisal appraisal = appraiser.appraise(stack);
            if (appraisal != null) return appraisal;
        }
        return null;
    }
}
