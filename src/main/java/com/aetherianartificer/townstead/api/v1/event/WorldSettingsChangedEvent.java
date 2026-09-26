package com.aetherianartificer.townstead.api.v1.event;

import net.minecraft.server.MinecraftServer;

import java.util.Set;

/**
 * The world's Townstead settings changed: World Setup was saved, an operator ran the switchboard
 * command, or the modpack file was reloaded. {@code changedKeys} are the settings whose value in
 * effect changed; {@code systemsOn} and {@code systemsOff} are the systems that switched. Not posted
 * when a world loads.
 *
 * <p>Added in API revision 2.</p>
 */
public record WorldSettingsChangedEvent(
        MinecraftServer server,
        Set<String> changedKeys,
        Set<String> systemsOn,
        Set<String> systemsOff
) implements TownsteadEvent {
}
