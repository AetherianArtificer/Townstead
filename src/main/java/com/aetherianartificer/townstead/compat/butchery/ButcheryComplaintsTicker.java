package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Surfaces the why-am-I-not-butchering reasons to nearby players via the
 * villager chat bubble. Runs server-side from
 * {@code VillagerServerTickDispatcher}, throttled per villager.
 *
 * <p>Only fires when the villager has a Tier 2+ butcher building in the
 * village — players who haven't upgraded yet shouldn't be nagged about
 * infrastructure they haven't opted into. Checks run in priority order;
 * the first matching reason wins and resets the throttle.
 */
public final class ButcheryComplaintsTicker {
    static final long COMPLAINT_INTERVAL_TICKS = 1200L; // 1 in-game minute
    private static final double PLAYER_RANGE = 24.0;
    static final String LAST_COMPLAINT_KEY = "townstead_lastButcheryComplaint";
    private static final String LAST_SEEN_TIER_KEY = "townstead_lastSeenShopTier";

    //? if >=1.21 {
    private static final ResourceLocation HOOK_ID = ResourceLocation.parse("butchery:hook");
    private static final ResourceLocation SKIN_RACK_ID = ResourceLocation.parse("butchery:skin_rack");
    private static final ResourceLocation MEAT_GRINDER_ID = ResourceLocation.parse("butchery:meat_grinder");
    private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:skins"));
    private static final TagKey<Item> RAW_MEATS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:raw_meats"));
    //?} else {
    /*private static final ResourceLocation HOOK_ID = new ResourceLocation("butchery", "hook");
    private static final ResourceLocation SKIN_RACK_ID = new ResourceLocation("butchery", "skin_rack");
    private static final ResourceLocation MEAT_GRINDER_ID = new ResourceLocation("butchery", "meat_grinder");
    private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "skins"));
    private static final TagKey<Item> RAW_MEATS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "raw_meats"));
    *///?}

    private ButcheryComplaintsTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!onWorkShift(villager, level)) return;
        if (level.getNearestPlayer(villager, PLAYER_RANGE) == null) return;

        // Shop-promotion celebration takes priority over complaints: a
        // successful upgrade is more interesting than a grievance.
        if (checkShopPromotion(villager, level)) return;

        long gameTime = level.getGameTime();
        if (onThrottle(villager, gameTime)) return;

        List<ButcheryShopScanner.ShopRef> shops = ButcheryShopScanner.carcassCapableShops(level, villager);
        if (shops.isEmpty()) return; // No Tier 2+ building: silent.

        String reasonKey = pickReason(level, villager, shops);
        if (reasonKey == null) return;

        villager.sendChatToAllAround(reasonKey);
        markComplained(villager, gameTime);
    }

    /**
     * Detect and announce a jump in the villager's accessible shop tier.
     * Returns true if a celebration fired so the caller can skip complaint
     * emission this tick.
     */
    private static boolean checkShopPromotion(VillagerEntityMCA villager, ServerLevel level) {
        int currentTier = ButcheryShopScanner.tierFor(level, villager);
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        int lastTier = data.contains(LAST_SEEN_TIER_KEY) ? data.getInt(LAST_SEEN_TIER_KEY) : -1;

        // First observation seeds the cache silently so a fresh villager
        // doesn't celebrate a tier they were hired into.
        if (lastTier < 0) {
            data.putInt(LAST_SEEN_TIER_KEY, currentTier);
            //? if neoforge {
            villager.setData(Townstead.HUNGER_DATA, data);
            //?} else {
            /*villager.getPersistentData().put("townstead_hunger", data);
            *///?}
            return false;
        }

        if (currentTier <= lastTier) {
            // Downgrade or no change: record but don't announce.
            if (currentTier != lastTier) {
                data.putInt(LAST_SEEN_TIER_KEY, currentTier);
                //? if neoforge {
                villager.setData(Townstead.HUNGER_DATA, data);
                //?} else {
                /*villager.getPersistentData().put("townstead_hunger", data);
                *///?}
            }
            return false;
        }

        data.putInt(LAST_SEEN_TIER_KEY, currentTier);
        //? if neoforge {
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*villager.getPersistentData().put("townstead_hunger", data);
        *///?}
        String key = "dialogue.chat.butcher_flavor.shop_promoted_to_tier_" + currentTier;
        villager.sendChatToAllAround(key);
        return true;
    }

    @Nullable
    private static String pickReason(ServerLevel level, VillagerEntityMCA villager,
            List<ButcheryShopScanner.ShopRef> shops) {
        // Priority: player-facing toggle first (global or per-villager), then
        // stage-specific carcass tools, then infrastructure.
        if (!SlaughterPolicy.slaughterEnabledFor(villager)) {
            return variant("dialogue.chat.butcher_request.slaughter_disabled", 3, level);
        }
        if (CarcassWorkTask.hasPendingSkinningWithoutKnife(level, villager)) {
            return variant("dialogue.chat.butcher_request.no_skinning_knife", 3, level);
        }
        if (!ButcherToolDamage.hasCleaver(villager)) {
            return variant("dialogue.chat.butcher_request.no_cleaver", 3, level);
        }

        boolean anyHook = false;
        for (ButcheryShopScanner.ShopRef ref : shops) {
            Building b = ref.building();
            List<BlockPos> hookPositions = b.getBlocks().get(HOOK_ID);
            if (hookPositions != null && !hookPositions.isEmpty()) {
                anyHook = true;
            }
            if (freshCarcassWithoutBasin(level, b)) {
                return variant("dialogue.chat.butcher_request.no_basin", 4, level);
            }
        }

        if (!anyHook) return variant("dialogue.chat.butcher_request.no_hook", 4, level);

        // Livestock: count animals across shops and pens, since butchers now
        // pull targets from either.
        boolean anyLivestock = false;
        for (ButcheryShopScanner.HuntRef ref : ButcheryShopScanner.huntableBuildings(level, villager)) {
            if (hasValidTarget(level, villager, ref.building())) {
                anyLivestock = true;
                break;
            }
        }
        if (!anyLivestock) return variant("dialogue.chat.butcher_request.no_livestock", 4, level);

        // Output piling up without the station that normally consumes it.
        // Skin rack first (most common high-volume output), then grinder
        // (raw meats → mince/sausage).
        String overflow = pickMissingStation(level, villager);
        if (overflow != null) return overflow;

        return null;
    }

    @Nullable
    private static String pickMissingStation(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> village = resolveVillage(villager);
        if (village.isEmpty()) return null;
        Village v = village.get();
        if (hasTaggedItemInInventory(villager, SKINS_TAG)
                && !villageHasBlock(v, SKIN_RACK_ID)) {
            return variant("dialogue.chat.butcher_request.no_skin_rack", 3, level);
        }
        // No organs complaint: Butchery's freezer is just a storage block
        // (no spoilage, no expiry, no tick behavior), so a "needs cold
        // storage" complaint would be a lie. Revisit if a spoilage
        // mechanic ever lands.
        if (hasTaggedItemInInventory(villager, RAW_MEATS_TAG)
                && !villageHasBlock(v, MEAT_GRINDER_ID)) {
            return variant("dialogue.chat.butcher_request.no_grinder", 3, level);
        }
        return null;
    }

    private static boolean hasTaggedItemInInventory(VillagerEntityMCA villager, TagKey<Item> tag) {
        int size = villager.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(tag)) return true;
        }
        return false;
    }

    private static boolean villageHasBlock(Village village, ResourceLocation blockId) {
        for (Building b : village.getBuildings().values()) {
            if (!b.isComplete()) continue;
            List<BlockPos> positions = b.getBlocks().get(blockId);
            if (positions != null && !positions.isEmpty()) return true;
        }
        return false;
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }

    private static boolean hasValidTarget(ServerLevel level, VillagerEntityMCA villager, Building building) {
        BlockPos origin = villager.blockPosition();
        AABB search = AABB.ofSize(
                new Vec3(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5),
                24, 8, 24);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, search,
                a -> building.containsPos(a.blockPosition())
                        && SlaughterPolicy.canSlaughter(villager, a));
        return !animals.isEmpty();
    }

    private static boolean freshCarcassWithoutBasin(ServerLevel level, Building building) {
        // Carcass blocks aren't in any building type's block requirements, so
        // they aren't indexed in building.getBlocks(). Check hook.below() for
        // each tracked hook, which is where SlaughterWorkTask places them.
        // Draining needs a blood grate in the column directly below the hook,
        // matching Butchery's own FillbloodgrateProcedure scan.
        List<BlockPos> hooks = building.getBlocks().get(HOOK_ID);
        if (hooks == null) return false;
        for (BlockPos hook : hooks) {
            BlockPos pos = hook.below();
            BlockState state = level.getBlockState(pos);
            if (!CarcassStateMachine.isFreshCarcass(state)) continue;
            if (!CarcassStateMachine.hasBloodGrateBelow(level, pos)) return true;
        }
        return false;
    }

    private static boolean onWorkShift(VillagerEntityMCA villager, ServerLevel level) {
        Brain<?> brain = villager.getBrain();
        long dayTime = level.getDayTime() % 24000L;
        return brain.getSchedule().getActivityAt((int) dayTime) == Activity.WORK;
    }

    private static boolean onThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(LAST_COMPLAINT_KEY);
        return gameTime - last < COMPLAINT_INTERVAL_TICKS;
    }

    private static void markComplained(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        data.putLong(LAST_COMPLAINT_KEY, gameTime);
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        data.putLong(LAST_COMPLAINT_KEY, gameTime);
        villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static String variant(String baseKey, int max, ServerLevel level) {
        return baseKey + "/" + (1 + level.random.nextInt(max));
    }
}
