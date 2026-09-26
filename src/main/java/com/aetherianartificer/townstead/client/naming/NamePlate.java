package com.aetherianartificer.townstead.client.naming;

import com.aetherianartificer.townstead.switchboard.Switchboard;

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
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            // A reborn player goes by the name of their current life.
            String reborn = com.aetherianartificer.townstead.client.rebirth.CharacterNameClient.get(player.getUUID());
            if (reborn != null) sink.accept(Component.literal(reborn));
            return;
        }
        if (!(entity instanceof VillagerEntityMCA villager)) return;

        // Capitals draws the nameplate when it is installed, and composes it itself from a title,
        // the name, a court office and a guard order. It appends its surname to whatever it is
        // handed and only recognises one already there when it sits at the end, so handing it a
        // family-first name would give it "Sato Hiroshi Sato". Hand it the given name and let it
        // compose: the surname it adds is the one Townstead already wrote into its identity.
        if (ModCompat.isLoaded(CAPITALS)) {
            String given = NameClientStore.styled(villager.getId(),
                    content == null ? "" : content.getString(),
                    com.aetherianartificer.townstead.naming.NameStyle.GIVEN);
            if (!given.isEmpty() && content != null && !given.equals(content.getString())) {
                sink.accept(Component.literal(given));
            }
            return;
        }

        // The name reaching here is already composed, because getDisplayName composes for every
        // surface. This only applies the nameplate own style, which exists because a world full of
        // family names reads as clutter to some players and as the whole point to others.
        String drawn = content == null ? "" : content.getString();
        String styled = NameClientStore.styled(villager.getId(), drawn, style());
        if (styled.isEmpty() || styled.equals(drawn)) return;
        sink.accept(Component.literal(styled));
    }

    private static com.aetherianartificer.townstead.naming.NameStyle style() {
        try {
            return Switchboard.get(com.aetherianartificer.townstead.TownsteadConfig.NAMEPLATE_NAME_STYLE);
        } catch (Throwable ignored) {
            return com.aetherianartificer.townstead.naming.NameStyle.FULL;
        }
    }
}
