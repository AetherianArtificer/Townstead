package com.aetherianartificer.townstead.api.v1.event;

import com.aetherianartificer.townstead.api.v1.model.BondSnapshot;
import net.minecraft.server.MinecraftServer;

import java.util.Optional;

/**
 * A political bond formed or ended: citizenship, an office, vassalage. {@code before} is empty when
 * the bond formed; an ended bond has {@code after.active()} false.
 */
public record BondChangedEvent(
        MinecraftServer server,
        Optional<BondSnapshot> before,
        BondSnapshot after
) implements TownsteadEvent {
}
