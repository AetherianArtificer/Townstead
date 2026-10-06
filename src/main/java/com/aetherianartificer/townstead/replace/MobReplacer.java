package com.aetherianartificer.townstead.replace;

import com.aetherianartificer.townstead.pheno.state.EntityStates;
import com.aetherianartificer.townstead.root.RootSelector;
import com.aetherianartificer.townstead.root.RootSpawnHandler;
import com.aetherianartificer.townstead.root.RootStates;
import com.aetherianartificer.townstead.switchboard.Switchboard;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Turns a replaceable mob's spawn into a person: a wild villager of a regional Root that can hold
 * the replacement's states, standing in for the mob in kill counts and loot, and leaving the world
 * the way the mob would. When no Root in the region can take the states, the mob spawns as usual.
 */
public final class MobReplacer {
    /** Marks a wild villager with the replacement it came from. */
    public static final String WILD = "townstead:wild";
    static final String REPLACED = "townstead:replaced";

    private MobReplacer() {}

    /**
     * Called from the spawn-finalize event. The mob still spawns and keeps its own mind; it wears
     * the person it stands for (see {@link WildCostume}). Always false: the spawn goes ahead.
     */
    public static boolean onFinalizeSpawn(Mob mob, ServerLevelAccessor accessor, MobSpawnType spawnType) {
        if (!MobReplacements.any() || !(accessor instanceof ServerLevel level) || mob instanceof VillagerEntityMCA
                || WildCostume.has(mob)) return false;
        MobReplacement replacement = MobReplacements.forType(mob.getType());
        if (replacement == null || !enabled(replacement)) return false;
        if (!replacement.spawnTypes().contains(spawnType.name().toLowerCase(Locale.ROOT))) return false;
        if (mob.getRandom().nextFloat() >= replacement.chance()) return false;

        BlockPos pos = mob.blockPosition();
        Predicate<ResourceLocation> eligible = root -> replacement.states().keySet().stream()
                .noneMatch(state -> Boolean.FALSE.equals(RootStates.declared(root, state))
                        || needsVillagerBody(state) && !com.aetherianartificer.townstead.root.rig.ServerRig.hasVillagerBody(root));
        RootSelector.Selection probe = RootSelector.select(level, pos, mob.getRandom(), eligible);
        if (probe.single() == null && probe.mix() == null) return false;

        VillagerEntityMCA villager = RootSpawnHandler.withRootConstraint(eligible, () -> {
            // Always grown: the person a wild mob wears fights and moves as that mob does.
            VillagerEntityMCA spawned = VillagerFactory.newVillager(level).withAge(0).withPosition(mob.position()).build();
            spawned.setYRot(mob.getYRot());
            spawned.getPersistentData().putString(WILD, replacement.id().toString());
            spawned.getPersistentData().putString(REPLACED, BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
            // Built before the tag, the brain still expects a home; rebuild it as a wanderer's.
            spawned.refreshBrain(level);
            //? if >=1.21 {
            spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), spawnType, null);
            //?} else {
            /*spawned.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), spawnType, null, null);
            *///?}
            if (spawned.isBaby()) spawned.setAge(0);
            return spawned;
        });
        // The region's fallback can still land on a Root that refuses; keep the mob then.
        for (ResourceLocation state : replacement.states().keySet()) {
            if (!EntityStates.eligible(villager, state)) return false;
        }
        dress(villager, replacement);
        for (Map.Entry<ResourceLocation, double[]> state : replacement.states().entrySet()) {
            double[] range = state.getValue();
            double amount = range[0] + villager.getRandom().nextDouble() * (range[1] - range[0]);
            EntityStates.set(villager, state.getKey(), Math.round(amount), 0, null);
        }
        WildCostume.dress(mob, villager);
        return false;
    }

    /** Worn, not carried: MCA drops a villager's inventory on death, and the loot is the mob's. */
    private static void dress(VillagerEntityMCA villager, MobReplacement replacement) {
        for (Map.Entry<String, java.util.List<ResourceLocation>> entry : replacement.outfit().entrySet()) {
            java.util.List<ResourceLocation> pool = entry.getValue();
            ResourceLocation pick = pool.get(villager.getRandom().nextInt(pool.size()));
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(pick).orElse(null);
            if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
            net.minecraft.world.entity.EquipmentSlot slot = switch (entry.getKey()) {
                case "head" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                case "chest" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                case "legs" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                default -> net.minecraft.world.entity.EquipmentSlot.FEET;
            };
            villager.setItemSlot(slot, new net.minecraft.world.item.ItemStack(item));
            villager.setDropChance(slot, 0f);
        }
    }

    /** Past the cap, each one checks now and then and leaves with the excess's share of chance. */
    private static boolean crowded(VillagerEntityMCA villager, MobReplacement replacement) {
        if ((villager.tickCount / 20 + villager.getId()) % 10 != 0) return false;
        String id = replacement.id().toString();
        int count = villager.level().getEntitiesOfClass(VillagerEntityMCA.class,
                villager.getBoundingBox().inflate(replacement.capRadius()),
                other -> id.equals(other.getPersistentData().getString(WILD))).size();
        return count > replacement.cap() && villager.getRandom().nextFloat() < (count - replacement.cap()) / (float) count;
    }

    /** A wild villager's death counts as the mob's: kill statistic and loot. */
    public static void onDeath(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof VillagerEntityMCA villager) || !(villager.level() instanceof ServerLevel level)) return;
        MobReplacement replacement = replacementOf(villager);
        EntityType<?> replaced = replacedType(villager);
        if (replacement == null || replaced == null) return;
        ServerPlayer killer = source.getEntity() instanceof ServerPlayer player ? player
                : villager.getLastHurtByMob() instanceof ServerPlayer player ? player : null;
        if (replacement.creditKills() && killer != null) killer.awardStat(Stats.ENTITY_KILLED.get(replaced));
        if (replacement.replacedLoot()) dropLoot(level, villager, replaced, source, killer);
    }

    /**
     * While its states hold, a wild villager leaves as the mob would: gone with nobody near, or on
     * Peaceful. Once cured it stays and wanders. Either way, enough hearts from a player settle it.
     */
    public static void tick(VillagerEntityMCA villager) {
        if (villager.tickCount % 20 != 0 || !villager.getPersistentData().contains(WILD)) return;
        MobReplacement replacement = replacementOf(villager);
        if (replacement != null && trySettle(villager, replacement)) {
            updateWildName(villager, false);
            return;
        }
        boolean active = activeReplacement(villager) != null;
        // Wild villagers from before replaced mobs kept their own minds go back to being the mob.
        EntityType<?> replaced = active ? replacedType(villager) : null;
        if (replaced != null && WildCostume.revert(villager, replaced)) return;
        updateWildName(villager, active);
        if (!active) return;
        if (villager.level().getDifficulty() == Difficulty.PEACEFUL || crowded(villager, replacement)) {
            villager.discard();
            return;
        }
        Player nearest = villager.level().getNearestPlayer(villager, -1);
        if (nearest == null) return;
        double distance = nearest.distanceToSqr(villager);
        if (distance > 128 * 128 || distance > 32 * 32 && villager.getRandom().nextInt(40) == 0) villager.discard();
    }

    public static boolean isWild(LivingEntity entity) {
        return entity.getPersistentData().contains(WILD);
    }

    /** The replacement a wild villager came from, while every state it arrived with still holds. */
    public static @Nullable MobReplacement activeReplacement(LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA) || !isWild(entity)) return null;
        MobReplacement replacement = replacementOf(entity);
        if (replacement == null) return null;
        for (ResourceLocation state : replacement.states().keySet()) {
            if (!EntityStates.resolve(entity, state).active()) return null;
        }
        return replacement;
    }

    /** The disposition group a wild villager belongs to while it is still what it replaced. */
    public static @Nullable String groupOf(LivingEntity entity) {
        MobReplacement replacement = activeReplacement(entity);
        return replacement == null ? null : replacement.group();
    }

    private static boolean trySettle(VillagerEntityMCA villager, MobReplacement replacement) {
        for (Player player : villager.level().getEntitiesOfClass(Player.class, villager.getBoundingBox().inflate(8))) {
            if (villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() < replacement.settleHearts()) continue;
            settle(villager);
            return true;
        }
        return false;
    }

    /** A befriended wild villager gives up the wilds and seeks a village like anyone else. */
    public static void settle(VillagerEntityMCA villager) {
        if (!(villager.level() instanceof ServerLevel level)) return;
        villager.getPersistentData().remove(WILD);
        villager.getPersistentData().remove(REPLACED);
        villager.refreshBrain(level);
        villager.getResidency().seekHome();
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.survival(villager,
                com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys.WILD_SETTLED, Map.of());
    }

    public static @Nullable MobReplacement replacementOf(LivingEntity entity) {
        ResourceLocation id = ResourceLocation.tryParse(entity.getPersistentData().getString(WILD));
        return id == null ? null : MobReplacements.byId(id);
    }

    public static @Nullable EntityType<?> replacedType(LivingEntity entity) {
        ResourceLocation id = ResourceLocation.tryParse(entity.getPersistentData().getString(REPLACED));
        return id == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
    }

    private static boolean enabled(MobReplacement replacement) {
        if (replacement.setting() == null) return true;
        Object value = Switchboard.valueAt(Arrays.asList(replacement.setting().split("\\.")));
        return !Boolean.FALSE.equals(value);
    }

    private static void dropLoot(ServerLevel level, VillagerEntityMCA villager, EntityType<?> replaced,
                                 DamageSource source, @Nullable ServerPlayer killer) {
        LootParams.Builder params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, villager)
                .withParameter(LootContextParams.ORIGIN, villager.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                //? if >=1.21 {
                .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity());
                //?} else {
                /*.withOptionalParameter(LootContextParams.KILLER_ENTITY, source.getEntity())
                .withOptionalParameter(LootContextParams.DIRECT_KILLER_ENTITY, source.getDirectEntity());
                *///?}
        if (killer != null) params.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer).withLuck(killer.getLuck());
        //? if >=1.21 {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(replaced.getDefaultLootTable());
        //?} else {
        /*LootTable table = level.getServer().getLootData().getLootTable(replaced.getDefaultLootTable());
        *///?}
        if (table == null) return;
        table.getRandomItems(params.create(LootContextParamSets.ENTITY), stack -> villager.spawnAtLocation(stack));
    }

    private static boolean needsVillagerBody(ResourceLocation state) {
        var definition = com.aetherianartificer.townstead.pheno.state.EntityStates.definition(state);
        return definition != null && definition.villagerBody();
    }

    static final String NAME_HIDDEN = "townstead:wild_name_hidden";

    /** Tells a player who starts seeing this villager whether its name is hidden (also clearing a reused id). */
    public static void syncWildName(net.minecraft.server.level.ServerPlayer player, VillagerEntityMCA villager) {
        boolean hidden = villager.getPersistentData().getBoolean(NAME_HIDDEN);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new WildNameS2CPayload(villager.getId(), hidden));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, new WildNameS2CPayload(villager.getId(), hidden));
        *///?}
    }

    /** A wild villager's name stays hidden while it acts as the mob it replaced. */
    private static void updateWildName(VillagerEntityMCA villager, boolean hidden) {
        if (villager.getPersistentData().getBoolean(NAME_HIDDEN) == hidden) return;
        villager.getPersistentData().putBoolean(NAME_HIDDEN, hidden);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, new WildNameS2CPayload(villager.getId(), hidden));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, new WildNameS2CPayload(villager.getId(), hidden));
        *///?}
    }
}
