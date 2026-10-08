package com.aetherianartificer.townstead.compat.emi;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.switchboard.ContentGates;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.Set;

/**
 * Keeps items of switched-off systems out of EMI. EMI only reads hidden stacks when it reloads, so a
 * change to the gated set asks EMI to reload.
 */
@EmiEntrypoint
public class TownsteadEmiPlugin implements EmiPlugin {
    private static volatile Set<Item> applied = Set.of();
    private static boolean listening;

    @Override
    public void register(EmiRegistry registry) {
        Set<Item> gated = Set.copyOf(ContentGates.gatedItems());
        applied = gated;
        if (!gated.isEmpty()) registry.removeEmiStacks(stack -> gated.contains(stack.getItemStack().getItem()));
        if (!listening) {
            listening = true;
            Switchboard.onChange(() -> Minecraft.getInstance().execute(TownsteadEmiPlugin::refresh));
        }
    }

    private static void refresh() {
        if (Set.copyOf(ContentGates.gatedItems()).equals(applied)) return;
        try {
            // EMI has no public reload call; its runtime one is stable across the versions we support.
            Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("reload").invoke(null);
        } catch (ReflectiveOperationException | LinkageError e) {
            Townstead.LOGGER.debug("[Switchboard] Could not reload EMI after a settings change", e);
        }
    }
}
