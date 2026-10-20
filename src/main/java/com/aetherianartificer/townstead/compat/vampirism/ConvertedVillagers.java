package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.replace.MobReplacer;
import com.aetherianartificer.townstead.root.RootSpawnHandler;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;

import java.util.List;

/**
 * Vampirism's converted villagers are vanilla villagers who became vampires. Townstead's people
 * are MCA villagers, so each one is replaced the way MCA replaces a vanilla villager (name, age,
 * place, biome, job and trades), as someone whose Root may be a vampire, and then turned: a real
 * villager with a home and memories who happens to be a vampire.
 */
public final class ConvertedVillagers {
    private static final ResourceLocation TYPE = ResourceLocation.tryParse("vampirism:villager_converted");
    private static final String SETTING = "vampirism.replaceSpawns";

    private ConvertedVillagers() {}

    public static void onJoin(Entity entity) {
        if (!(entity instanceof Villager villager) || entity instanceof VillagerEntityMCA) return;
        if (!(entity.level() instanceof ServerLevel level) || !TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))) return;
        if (!MobReplacer.settingOn(SETTING)) return;
        // Not while the level is still adding it: swap on the next tick.
        level.getServer().execute(() -> swap(level, villager));
    }

    private static void swap(ServerLevel level, Villager converted) {
        if (converted.isRemoved()) return;
        VillagerEntityMCA person = RootSpawnHandler.withRootConstraint(
                MobReplacer.rootsAllowing(List.of(VampireVillagers.STATE)),
                () -> VillagerFactory.newVillager(level)
                        .withName(converted.hasCustomName() ? converted.getName().getString() : null)
                        .withAge(converted.getAge())
                        .withPosition(converted)
                        .withType(converted.getVillagerData().getType())
                        .withProfession(converted.getVillagerData().getProfession(), converted.getVillagerData().getLevel(),
                                converted.getOffers())
                        .build());
        if (person == null) return;
        if (converted.isPersistenceRequired()) person.setPersistenceRequired();
        for (String tag : converted.getTags()) person.addTag(tag);
        converted.discard();
        level.addFreshEntityWithPassengers(person);
        VampireVillagers.turn(person);
    }
}
