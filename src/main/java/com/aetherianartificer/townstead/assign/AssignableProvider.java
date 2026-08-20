package com.aetherianartificer.townstead.assign;

import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * A source of assignable actions.
 *
 * <p>Two methods, and the split matters. {@link #collect} answers "what may this player put on a
 * slot", which is a browsing question; {@link #invoke} answers "do the thing", which is an authority
 * question. A provider that offers something must also be the one that performs it, because only it
 * knows what performing means and only it can refuse.</p>
 *
 * <p>Registration is a plain list rather than a Forge/NeoForge registry, since providers are ours or
 * a datapack's and both are known at load time. A mod wanting in adds itself here through the
 * compat layer, which is the same door the seasonal and Emotecraft bridges use.</p>
 */
public interface AssignableProvider {

    /** Everything this player could assign from this source, in a stable order. */
    void collect(ServerPlayer player, List<Assignable> out);

    /**
     * Performs the action, or returns false if this provider does not own the id.
     *
     * <p>Returning false is how dispatch works, so a provider must not throw for an unfamiliar id
     * and must not act on one it only half-recognises.</p>
     */
    boolean invoke(ServerPlayer player, net.minecraft.resources.ResourceLocation id);
}
