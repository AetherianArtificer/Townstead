package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A replaced mob keeps its own body and mind and wears a person's look: the villager it would have
 * been, saved on the mob. The client draws that villager in the mob's place; a cure turns the mob
 * into it for good.
 */
public final class WildCostume {
    private static final String COSTUME = "townstead:costume";
    private static final String TYPE = "type";
    private static final String DATA = "data";
    // The person a costumed mob shows, rebuilt on demand for gene expression and conversion.
    private static final Map<Entity, VillagerEntityMCA> PEOPLE = new WeakHashMap<>();

    private WildCostume() {}

    public static boolean has(Entity entity) {
        return !(entity instanceof VillagerEntityMCA) && entity.getPersistentData().contains(COSTUME);
    }

    /** Saves {@code person} (never added to the world) as the look {@code mob} wears. */
    public static void dress(Mob mob, VillagerEntityMCA person) {
        TownsteadVillagers.flush(person);
        CompoundTag costume = new CompoundTag();
        costume.putString(TYPE, BuiltInRegistries.ENTITY_TYPE.getKey(person.getType()).toString());
        costume.put(DATA, person.saveWithoutId(new CompoundTag()));
        mob.getPersistentData().put(COSTUME, costume);
        synchronized (PEOPLE) {
            PEOPLE.remove(mob);
        }
    }

    /** The person {@code mob} shows, or null when it wears none or the save no longer loads. */
    public static @Nullable VillagerEntityMCA person(Mob mob) {
        if (!has(mob) || !(mob.level() instanceof ServerLevel level)) return null;
        synchronized (PEOPLE) {
            VillagerEntityMCA cached = PEOPLE.get(mob);
            if (cached != null) {
                cached.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
                return cached;
            }
        }
        VillagerEntityMCA person = load(level, mob.getPersistentData().getCompound(COSTUME));
        if (person == null) return null;
        person.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        synchronized (PEOPLE) {
            PEOPLE.put(mob, person);
        }
        return person;
    }

    private static @Nullable VillagerEntityMCA load(ServerLevel level, CompoundTag costume) {
        ResourceLocation typeId = ResourceLocation.tryParse(costume.getString(TYPE));
        EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
        if (type == null || !(type.create(level) instanceof VillagerEntityMCA person)) return null;
        person.load(costume.getCompound(DATA));
        return person;
    }

    /** Tells a player who starts seeing {@code mob} which person to draw, with that person's Root look. */
    public static void syncTo(ServerPlayer player, Mob mob) {
        VillagerEntityMCA person = person(mob);
        if (person == null) return;
        CompoundTag costume = mob.getPersistentData().getCompound(COSTUME);
        // The client only draws the person; its mind and trades stay behind.
        CompoundTag data = costume.getCompound(DATA).copy();
        data.remove("Brain");
        data.remove("Gossips");
        data.remove("Offers");
        WildCostumeS2CPayload look = new WildCostumeS2CPayload(mob.getId(), costume.getString(TYPE), data);
        String rootId = TownsteadVillagers.get(person).life().rootId();
        var root = new com.aetherianartificer.townstead.root.RootSyncS2CPayload(mob.getId(), rootId == null ? "" : rootId);
        var genes = com.aetherianartificer.townstead.root.ExpressedGenesS2CPayload.forEntity(mob.getId(), person);
        var name = new WildNameS2CPayload(mob.getId(), true);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, look);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, root);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, genes);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, name);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, look);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, root);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, genes);
        com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, name);
        *///?}
    }

    /**
     * Replaces {@code mob} with the person it shows: same look, health share, effects and place.
     * The person is no longer wild; it seeks a home like anyone else. Null when there is none.
     */
    public static @Nullable VillagerEntityMCA convert(Mob mob) {
        if (!has(mob) || !(mob.level() instanceof ServerLevel level)) return null;
        VillagerEntityMCA person = load(level, mob.getPersistentData().getCompound(COSTUME));
        if (person == null) return null;
        person.getPersistentData().remove(MobReplacer.WILD);
        person.getPersistentData().remove(MobReplacer.REPLACED);
        person.getPersistentData().remove(MobReplacer.NAME_HIDDEN);
        person.moveTo(mob.getX(), mob.getY(), mob.getZ(), mob.getYRot(), mob.getXRot());
        person.setYHeadRot(mob.getYHeadRot());
        person.setYBodyRot(mob.yBodyRot);
        person.setHealth(Math.max(1f, person.getMaxHealth() * mob.getHealth() / mob.getMaxHealth()));
        for (MobEffectInstance effect : mob.getActiveEffects()) person.addEffect(new MobEffectInstance(effect));
        if (mob.hasCustomName() && !person.hasCustomName()) person.setCustomName(mob.getCustomName());
        mob.discard();
        synchronized (PEOPLE) {
            PEOPLE.remove(mob);
        }
        level.addFreshEntityWithPassengers(person);
        person.refreshBrain(level);
        person.getResidency().seekHome();
        return person;
    }

    /**
     * Turns a wild villager from before mobs kept their own minds back into the mob it replaced,
     * wearing its look. False when the mob type is gone (its mod was removed).
     */
    public static boolean revert(VillagerEntityMCA villager, EntityType<?> replaced) {
        if (!(villager.level() instanceof ServerLevel level) || !(replaced.create(level) instanceof Mob mob)) return false;
        mob.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), villager.getXRot());
        //? if >=1.21 {
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null);
        //?} else {
        /*mob.finalizeSpawn(level, level.getCurrentDifficultyAt(villager.blockPosition()), MobSpawnType.CONVERSION, null, null);
        *///?}
        mob.setHealth(Math.max(1f, mob.getMaxHealth() * villager.getHealth() / villager.getMaxHealth()));
        dress(mob, villager);
        villager.discard();
        level.addFreshEntityWithPassengers(mob);
        return true;
    }
}
