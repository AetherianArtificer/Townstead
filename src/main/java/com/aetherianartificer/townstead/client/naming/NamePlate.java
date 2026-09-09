package com.aetherianartificer.townstead.client.naming;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.naming.NameClientStore;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;

/**
 * Puts a villager's family name on their nameplate, when nothing else is already doing it.
 *
 * <p>Deliberately stands down when MCA Capitals is installed. Capitals draws its own nameplate,
 * composed from a title, the name, a court office and a guard order, and Townstead's family name is
 * already written into the identity it composes from, so it appears there without a second plate
 * being drawn over the top. Two mods rendering the same nameplate is how you get a doubled name.</p>
 *
 * <p>Only ever appends: the given name in the event is whatever MCA and every other mod already
 * decided to call this villager, and the family name goes on the end of it. Nothing here takes a
 * rendered name apart, which is the mistake that costs a villager their original name.</p>
 */
public final class NamePlate {

    private static final String CAPITALS = "mcacapitals";

    private NamePlate() {}

    /**
     * @param content what would otherwise be drawn
     * @param sink    where to put the replacement, when there is one
     */
    public static void render(Entity entity, Component content, Consumer<Component> sink) {
        if (!(entity instanceof VillagerEntityMCA villager)) return;
        if (ModCompat.isLoaded(CAPITALS)) return;

        String given = content == null ? "" : content.getString();
        String full = NameClientStore.fullName(villager.getId(), given);
        if (full.isEmpty() || full.equals(given)) return;
        sink.accept(Component.literal(full));
    }
}
