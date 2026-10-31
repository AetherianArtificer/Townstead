package com.aetherianartificer.townstead.persona;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * A Persona's voice sits above every other voice a villager has. Its id is the Persona's story
 * id, such as {@code townstead:persona/village_builder}: villager conversation lines name it as
 * their {@code voice}, and MCA phrases live under {@code townstead_voice.townstead.persona.village_builder.<phrase>}.
 */
public final class PersonaVoice {
    private PersonaVoice() {}

    public static @Nullable ResourceLocation of(Entity villager) {
        MinecraftServer server = villager.getServer();
        if (server == null) return null;
        PersonaInstances.Instance instance = PersonaInstances.get(server).of(villager.getUUID());
        return instance == null ? null : Personas.storyId(instance.persona());
    }
}
