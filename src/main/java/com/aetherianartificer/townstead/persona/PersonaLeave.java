package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.journey.Journeys;
import com.aetherianartificer.townstead.story.StoryService;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A player asking a Persona to leave town, from the More page of their interaction screen. They
 * say their {@code farewell()} line if their story has one, walk out of town and go once nobody
 * can see them. They never come back to this town, though they may still arrive in others. The
 * player's story and bond with them are cleared.
 */
public final class PersonaLeave {
    private static final Logger LOGGER = LoggerFactory.getLogger("Townstead/Personas");
    private static final double REACH = 10;

    private PersonaLeave() {}

    public static boolean isPersona(ServerPlayer player, int entityId) {
        return persona(player, entityId) != null;
    }

    public static void ask(ServerPlayer player, int entityId) {
        VillagerEntityMCA villager = persona(player, entityId);
        if (villager == null) return;
        PersonaInstances instances = PersonaInstances.get(player.server);
        PersonaInstances.Instance instance = instances.of(villager.getUUID());
        if (instance == null) return;
        // Bar the town they are standing in, which is the one the player means, even if MCA renumbered it.
        Village here = VillageManager.get(player.serverLevel())
                .findNearestVillage(villager.blockPosition(), Village.MERGE_MARGIN).orElse(null);
        int village = here != null ? here.getId() : instance.village();
        instances.dismiss(instance.persona(), instance.dimension(), village);
        if (village != instance.village()) instances.dismiss(instance.persona(), instance.dimension(), instance.village());

        String farewell = StoryService.farewellLine(player, villager);
        if (farewell != null) villager.sendChatMessage(Component.literal(farewell), player);
        for (ServerPlayer near : player.serverLevel().players()) {
            if (near.distanceToSqr(villager) > 128 * 128) continue;
            near.sendSystemMessage(Component.translatable("message.townstead.persona.leaving", villager.getName()));
        }

        PersonaBonds.remove(player, instance.persona());
        PersonaBonds.forgetGifts(player, instance.persona());
        StoryService.reset(player, Personas.storyId(instance.persona()));
        // No longer a Persona from here on: they walk out as an ordinary villager and are gone.
        instances.remove(villager.getUUID());
        Journeys.leave(villager);
        LOGGER.info("Persona {} was asked to leave village {} by {}", instance.persona(), village, player.getGameProfile().getName());
    }

    private static @Nullable VillagerEntityMCA persona(ServerPlayer player, int entityId) {
        if (!(player.serverLevel().getEntity(entityId) instanceof VillagerEntityMCA villager) || !villager.isAlive()) return null;
        if (villager.distanceToSqr(player) > REACH * REACH) return null;
        return PersonaInstances.get(player.server).isPersona(villager.getUUID()) ? villager : null;
    }
}
