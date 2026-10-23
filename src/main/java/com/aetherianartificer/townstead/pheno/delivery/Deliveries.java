package com.aetherianartificer.townstead.pheno.delivery;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Items that are deliveries: a letter, a parcel, a quest hand-in. A system that owns such an item
 * registers a handler here; {@code pheno:deliver} offers the item to each handler in turn until one
 * takes it. A handler reads the item's own data, so the delivery logic stays with its system.
 */
public final class Deliveries {
    private Deliveries() {}

    @FunctionalInterface
    public interface Handler {
        /**
         * Delivers {@code item} from {@code sender} to {@code recipient}. Returns false when the item
         * is not this handler's kind; true when it handled the delivery, even if the recipient
         * turned it down. The handler takes the item from the stack itself when it keeps it.
         */
        boolean deliver(@Nullable LivingEntity sender, LivingEntity recipient, ItemStack item);
    }

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

    public static void register(Handler handler) {
        HANDLERS.add(handler);
    }

    /** True when a handler took the delivery. */
    public static boolean deliver(@Nullable LivingEntity sender, LivingEntity recipient, ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        for (Handler handler : HANDLERS) {
            if (handler.deliver(sender, recipient, item)) return true;
        }
        return false;
    }
}
