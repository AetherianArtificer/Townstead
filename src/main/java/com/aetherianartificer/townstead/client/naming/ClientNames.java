package com.aetherianartificer.townstead.client.naming;

import com.aetherianartificer.townstead.naming.NameClientStore;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/**
 * A villager's full name for Townstead's own screens.
 *
 * <p>Always appends to whatever the entity is already called, rather than composing from scratch,
 * so a name another mod has already decorated keeps its decoration and simply gains the family name
 * on the end. Unlike the nameplate this is not gated on any other mod: these are Townstead's screens
 * and nothing else is drawing over them.</p>
 */
public final class ClientNames {

    private ClientNames() {}

    /** The entity's display name with its family name appended, when it has one. */
    public static Component displayName(Entity entity) {
        Component display = entity.getDisplayName();
        String full = NameClientStore.fullName(entity.getId(), display.getString());
        return full.equals(display.getString()) ? display : Component.literal(full);
    }

    /** The family name alone, for surfaces that show it as its own field. */
    public static String family(Entity entity) {
        return NameClientStore.family(entity.getId());
    }

    /** The culture id, for surfaces that name it. */
    public static String culture(Entity entity) {
        return NameClientStore.culture(entity.getId());
    }
}
