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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Vampirism's converted villagers are vanilla villagers who became vampires. Townstead's people
 * are MCA villagers, so each one is replaced the way MCA replaces a vanilla villager (name, age,
 * place, biome, job and trades), as someone whose Root may be a vampire, and then turned: a real
 * villager with a home and memories who happens to be a vampire. The plain villagers a totem
 * spawns into its village are replaced the same way, without turning.
 */
public final class ConvertedVillagers {
    private static final ResourceLocation TYPE = ResourceLocation.tryParse("vampirism:villager_converted");
    private static final String SETTING = "vampirism.replaceSpawns";
    private static final Set<Villager> TOTEM_SPAWNS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private ConvertedVillagers() {}

    /** Marks a villager a totem is about to spawn. Totem spawns are events, which MCA leaves vanilla. */
    public static void markTotemSpawn(Villager villager) {
        if (villager != null && !(villager instanceof VillagerEntityMCA)) TOTEM_SPAWNS.add(villager);
    }

    public static void onJoin(Entity entity) {
        if (!(entity instanceof Villager villager) || entity instanceof VillagerEntityMCA) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        boolean converted = TYPE.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        boolean totem = TOTEM_SPAWNS.remove(villager);
        // A totem counts only the villagers inside its area, so residents out at work leave it
        // short; a village with every bed taken turns its newcomer away.
        if (totem && net.conczin.mca.server.world.data.Village.findNearest(villager)
                .filter(village -> !village.hasSpace()).isPresent()) {
            level.getServer().execute(villager::discard);
            return;
        }
        if (converted) {
            if (!MobReplacer.settingOn(SETTING)) return;
        } else if (!totem || villager.getClass() != Villager.class
                || !net.conczin.mca.Config.getInstance().overwriteOriginalVillagers) {
            return;
        }
        // Not while the level is still adding it: swap on the next tick.
        level.getServer().execute(() -> swap(level, villager, converted));
    }

    private static void swap(ServerLevel level, Villager converted, boolean vampire) {
        if (converted.isRemoved()) return;
        VillagerEntityMCA person = RootSpawnHandler.withRootConstraint(
                vampire ? MobReplacer.rootsAllowing(List.of(VampireVillagers.STATE)) : root -> true,
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
        if (vampire) VampireVillagers.turn(person);
    }
}
