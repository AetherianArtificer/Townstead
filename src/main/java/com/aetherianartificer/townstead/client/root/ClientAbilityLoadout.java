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

    /** The last payload, kept so local detail can be re-resolved without asking the server again. */
    private static AbilityLoadoutS2CPayload last;

    /**
     * Re-runs the client-side half: pack overrides and live mod lookups.
     *
     * <p>Live detail is a snapshot of the moment it was read, and what is in an Iron's quick-cast
     * slot changes when the player swaps spellbooks, which the server has no reason to tell us
     * about. Screens call this when they open, so the wheel never shows the spell you were carrying
     * an hour ago.</p>
     */
    public static void refreshLocal() {
        if (last != null) accept(last);
    }

    public static void accept(AbilityLoadoutS2CPayload payload) {
        last = payload;
        BY_SLOT.clear();
        for (AbilityLoadoutS2CPayload.Entry entry : payload.entries()) {
            // A slot holding a KEYBIND comes back named only by its id: the server stores those
            // without resolving them, because only a client knows what bindings exist. Naming it
            // once here means nothing downstream has to know the difference.
            BY_SLOT.put(entry.slot(),
                    com.aetherianartificer.townstead.client.input.KeybindAssignables.resolve(entry));
        }
        java.util.List<AbilityLoadoutS2CPayload.Option> merged =
                new java.util.ArrayList<>(payload.available());
        merged.addAll(com.aetherianartificer.townstead.client.input.KeybindAssignables.options());
        available = java.util.List.copyOf(merged);
    }

    /**
     * Cooldowns for the actions the CLIENT performs, which the server never hears about.
     *
     * <p>A keybind press is dispatched locally and never round-trips, so the server's table cannot
     * record it and the entry always arrives ready. This is not a rule being enforced client-side:
     * a player who wants to spam that binding can bind the key directly and press it. It is the
     * wheel keeping its own promise, so a declared cooldown means the same thing whichever kind of
     * action is sitting in the slot.</p>
     *
     * <p>Keyed by ID, not by slot, so the same action in two slots cools in both.</p>
     */
    private static final Map<String, Long> LOCAL_READY = new LinkedHashMap<>();

    /** Starts a local cooldown, in game time, after a client-performed press. */
    public static void startLocalCooldown(String id, int ticks) {
        if (id == null || id.isEmpty() || ticks <= 0) return;
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;
        LOCAL_READY.put(id, mc.level.getGameTime() + ticks);
    }

    /**
     * When this entry is next usable, taking the later of what the server said and what we
     * performed ourselves. One number, so the ring and the gate never disagree.
     */
    public static long readyAt(AbilityLoadoutS2CPayload.Entry entry) {
        if (entry == null) return 0L;
        return Math.max(entry.readyAt(), LOCAL_READY.getOrDefault(entry.id(), 0L));
    }

    public static boolean isReady(AbilityLoadoutS2CPayload.Entry entry) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        return mc.level == null || readyAt(entry) <= mc.level.getGameTime();
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
        last = null;
        BY_SLOT.clear();
        available = java.util.List.of();
        LOCAL_READY.clear();
        lastUsedSlot = 0;
    }
}
