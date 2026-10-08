package com.aetherianartificer.townstead.client.persona;

import com.aetherianartificer.townstead.persona.PersonaMenuC2SPayload;
import net.conczin.mca.client.gui.AbstractDynamicScreen;
import net.minecraft.client.Minecraft;

import java.util.HashSet;
import java.util.Set;

/**
 * Which villagers are Personas, as far as their interaction screen needs to know: the More page
 * offers "Ask to Leave" only on them. Asked of the server each time the screen opens.
 */
public final class PersonaMenuClient {
    private static final Set<Integer> PERSONAS = new HashSet<>();

    private PersonaMenuClient() {}

    /** The interaction screen of a villager, which knows whose it is. */
    public interface VillagerScreen {
        int townstead$villagerEntityId();
    }

    public static void ask(int entityId) {
        send(new PersonaMenuC2SPayload(entityId, false));
    }

    public static boolean isPersona(int entityId) {
        return PERSONAS.contains(entityId);
    }

    /** The player confirmed: ask this Persona to leave town. */
    public static void leave(int entityId) {
        PERSONAS.remove(entityId);
        send(new PersonaMenuC2SPayload(entityId, true));
    }

    /** The server's answer. Rebuilds the open page when it changes what the page shows. */
    public static void accept(int entityId, boolean persona) {
        boolean changed = persona ? PERSONAS.add(entityId) : PERSONAS.remove(entityId);
        if (!changed) return;
        if (Minecraft.getInstance().screen instanceof AbstractDynamicScreen screen
                && screen instanceof VillagerScreen villager && villager.townstead$villagerEntityId() == entityId) {
            String page = screen.getActiveScreen();
            if ("main".equals(page) || "townstead_more".equals(page)) screen.setLayout(page);
        }
    }

    private static void send(PersonaMenuC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
