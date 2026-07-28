package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.ability.AbilityLoadoutS2CPayload;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The local player's prepared ability slots, as last sent by the server.
 *
 * <p>Purely a mirror. Nothing here decides anything: the wheel reads it to know what to draw, and a
 * press still travels to the server, which resolves the slot again and enforces cooldown, cost and
 * condition. A client that lies about its loadout changes only what its own wheel looks like.</p>
 */
public final class ClientAbilityLoadout {

    private ClientAbilityLoadout() {}

    private static final Map<Integer, AbilityLoadoutS2CPayload.Entry> BY_SLOT = new LinkedHashMap<>();
    private static java.util.List<AbilityLoadoutS2CPayload.Option> available = java.util.List.of();

    public static void accept(AbilityLoadoutS2CPayload payload) {
        BY_SLOT.clear();
        for (AbilityLoadoutS2CPayload.Entry entry : payload.entries()) {
            BY_SLOT.put(entry.slot(), entry);
        }
        available = payload.available();
    }

    private static int lastUsedSlot = 0;

    /**
     * The last slot fired, so a tap of the wheel key can repeat it without drawing anything.
     *
     * <p>Client-side on purpose: it is a convenience over an intent the server validates anyway, and
     * the worst a wrong value can do is send a slot the server then refuses.</p>
     */
    public static void rememberUsed(int slot) {
        lastUsedSlot = slot;
    }

    /** The last slot fired, or 0 when nothing has been fired or it is no longer filled. */
    public static int lastUsedSlot() {
        return BY_SLOT.containsKey(lastUsedSlot) ? lastUsedSlot : 0;
    }

    /** Everything the player could prepare, in the order the server listed it. */
    public static java.util.List<AbilityLoadoutS2CPayload.Option> available() {
        return available;
    }

    /**
     * The current arrangement as plain ids, ready to be edited and sent back.
     *
     * <p>The whole arrangement goes to the server on every edit, so this is what a change is
     * applied ON TOP of rather than a delta the server would have to reconcile.</p>
     */
    public static Map<Integer, net.minecraft.resources.ResourceLocation> arrangement() {
        Map<Integer, net.minecraft.resources.ResourceLocation> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, AbilityLoadoutS2CPayload.Entry> entry : BY_SLOT.entrySet()) {
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.resources.ResourceLocation.tryParse(entry.getValue().id());
            if (id != null) out.put(entry.getKey(), id);
        }
        return out;
    }

    public static AbilityLoadoutS2CPayload.Entry slot(int slot) {
        return BY_SLOT.get(slot);
    }

    public static boolean isEmpty() {
        return BY_SLOT.isEmpty();
    }

    /** Dropped on disconnect so a second world never opens showing the first one's abilities. */
    public static void clear() {
        BY_SLOT.clear();
        available = java.util.List.of();
        lastUsedSlot = 0;
    }
}
