package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import com.aetherianartificer.townstead.compat.thirst.ThirstWasTakenBridge;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.hunger.VillagerEatingManager;
import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.thirst.VillagerDrinkingManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ThirstVillagerTicker {
    private static final ResourceLocation TOWNSTEAD_SPEED_PENALTY =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thirst_speed_penalty");
    private static final long BIOME_MODIFIER_RESAMPLE_TICKS = 100L;
    private static final TagKey<Block> FD_KITCHEN_STORAGE_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_UPGRADED_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_upgraded"));
    private static final TagKey<Block> FD_KITCHEN_STORAGE_NETHER_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("townstead:compat/farmersdelight/kitchen_storage_nether"));

    private static final Map<Integer, TickState> STATE = new ConcurrentHashMap<>();

    private ThirstVillagerTicker() {}

    public static void tick(VillagerEntityMCA self) {
        if (!(self.level() instanceof ServerLevel level)) return;
        ThirstCompatBridge bridge = ThirstWasTakenBridge.INSTANCE;
        if (!TownsteadConfig.isVillagerThirstEnabled() || !bridge.isActive()) {
            removeSpeedModifier(self);
            return;
        }

        TickState state = STATE.computeIfAbsent(self.getId(), id -> new TickState());
        CompoundTag thirst = self.getData(Townstead.THIRST_DATA);
        long gameTime = level.getGameTime();

        if (gameTime >= state.nextBiomeModifierSampleTick) {
            state.biomeModifier = bridge.exhaustionBiomeModifier(level, self.blockPosition());
            state.nextBiomeModifierSampleTick = gameTime + BIOME_MODIFIER_RESAMPLE_TICKS;
        }
        float biomeModifier = state.biomeModifier;

        boolean thirstChanged = VillagerDrinkingManager.tickAndFinalize(self, thirst);

        int currentThirstLevel = ThirstData.getThirst(thirst);
        if (ThirstData.isDrinkingMode(thirst)) {
            if (currentThirstLevel >= ThirstData.ADEQUATE_THRESHOLD) ThirstData.setDrinkingMode(thirst, false);
        } else if (currentThirstLevel <= ThirstData.EMERGENCY_THRESHOLD) {
            ThirstData.setDrinkingMode(thirst, true);
        }

        double dx = self.getX() - state.prevX;
        double dz = self.getZ() - state.prevZ;
        double distSq = dx * dx + dz * dz;
        state.prevX = self.getX();
        state.prevZ = self.getZ();
        if (distSq > 0.0025) {
            float dist = (float) Math.sqrt(distSq);
            ThirstData.setExhaustion(thirst, ThirstData.getExhaustion(thirst) + (dist * ThirstData.EXHAUSTION_MOVEMENT_PER_BLOCK * biomeModifier));
        }

        VillagerBrain<?> brain = self.getVillagerBrain();
        Chore currentJob = brain.getCurrentJob();
        if (brain.isPanicking() || self.getLastHurtByMob() != null) {
            ThirstData.setExhaustion(thirst, ThirstData.getExhaustion(thirst) + (ThirstData.EXHAUSTION_COMBAT * biomeModifier));
        } else if (currentJob != Chore.NONE) {
            ThirstData.setExhaustion(thirst, ThirstData.getExhaustion(thirst) + (ThirstData.EXHAUSTION_CHORE * biomeModifier));
        } else if (isGuardPatrolling(self)) {
            ThirstData.setExhaustion(thirst, ThirstData.getExhaustion(thirst) + (ThirstData.EXHAUSTION_GUARD_PATROL * biomeModifier));
        } else if (!isResting(self)) {
            ThirstData.setExhaustion(thirst, ThirstData.getExhaustion(thirst) + (ThirstData.EXHAUSTION_AWAKE_BASELINE * biomeModifier));
        }

        thirstChanged |= ThirstData.processExhaustion(thirst);
        if (!isResting(self) && self.tickCount % ThirstData.PASSIVE_DRAIN_INTERVAL == 0) {
            thirstChanged |= ThirstData.passiveDrain(thirst);
        }

        Activity currentActivity = currentScheduleActivity(self);
        if (state.lastActivity != null && currentActivity != state.lastActivity) {
            int t = ThirstData.getThirst(thirst);
            long lastDrank = ThirstData.getLastDrankTime(thirst);
            boolean canDrink = (gameTime - lastDrank) >= ThirstData.MIN_DRINK_INTERVAL
                    && !VillagerDrinkingManager.isDrinking(self)
                    && !VillagerEatingManager.isEating(self);
            if (canDrink) {
                boolean shouldDrink = false;
                if (state.lastActivity == Activity.REST && t < ThirstData.BREAKFAST_THRESHOLD) {
                    shouldDrink = true;
                } else if (state.lastActivity == Activity.WORK && t < ThirstData.LUNCH_THRESHOLD) {
                    shouldDrink = true;
                } else if (currentActivity == Activity.REST && t < ThirstData.DINNER_THRESHOLD) {
                    shouldDrink = true;
                }
                if (shouldDrink) thirstChanged |= tryDrinkFromInventory(self, bridge);
            }
        }
        state.lastActivity = currentActivity;

        if (ThirstData.getThirst(thirst) < ThirstData.ADEQUATE_THRESHOLD) {
            long lastDrank = ThirstData.getLastDrankTime(thirst);
            long minDrinkInterval = ThirstData.isDrinkingMode(thirst)
                    ? 20L
                    : (ThirstData.getThirst(thirst) <= ThirstData.EMERGENCY_THRESHOLD ? 20L : ThirstData.MIN_DRINK_INTERVAL);
            if ((gameTime - lastDrank) >= minDrinkInterval
                    && !VillagerDrinkingManager.isDrinking(self)
                    && !VillagerEatingManager.isEating(self)) {
                thirstChanged |= tryDrinkFromInventory(self, bridge);
            }
        }

        if (self.tickCount % ThirstData.MOOD_CHECK_INTERVAL == 0) {
            int t = ThirstData.getThirst(thirst);
            ThirstData.ThirstState moodState = ThirstData.getState(t);
            float pressure = ThirstData.getMoodPressure(moodState);
            float drift = ThirstData.getMoodDrift(thirst) + pressure;
            int moodDelta = 0;
            if (drift >= 1f) moodDelta = (int) Math.floor(drift);
            else if (drift <= -1f) moodDelta = (int) Math.ceil(drift);

            if (moodDelta != 0) {
                brain.modifyMoodValue(moodDelta);
                drift -= moodDelta;
            }
            ThirstData.setMoodDrift(thirst, drift);
        }

        if (ThirstData.getThirst(thirst) <= 0) {
            int damageTimer = ThirstData.getDamageTimer(thirst) + 1;
            if (damageTimer >= ThirstData.DAMAGE_INTERVAL) {
                if (shouldApplyDehydrationDamage(level) && self.isAlive()) {
                    self.hurt(level.damageSources().starve(), 1.0f);
                }
                damageTimer = 0;
            }
            ThirstData.setDamageTimer(thirst, damageTimer);
        } else {
            ThirstData.setDamageTimer(thirst, 0);
        }

        if (self.tickCount % 100 == 0) {
            storeEmptyBottles(level, self);
        }

        updateSpeedModifier(self, ThirstData.getThirst(thirst));
        self.setData(Townstead.THIRST_DATA, thirst);

        int thirstLevel = ThirstData.getThirst(thirst);
        int quenchedLevel = ThirstData.getQuenched(thirst);
        if (thirstLevel != state.lastSyncedThirst || quenchedLevel != state.lastSyncedQuenched || thirstChanged) {
            state.lastSyncedThirst = thirstLevel;
            state.lastSyncedQuenched = quenchedLevel;
            PacketDistributor.sendToPlayersTrackingEntity(self, Townstead.townstead$thirstSync(self, thirst));
        }

        if (!self.isAlive() || self.isRemoved()) {
            STATE.remove(self.getId());
        }
    }

    private static boolean tryDrinkFromInventory(VillagerEntityMCA self, ThirstCompatBridge bridge) {
        if (!TownsteadConfig.isSelfInventoryDrinkingEnabled()) return false;
        ItemStack drink = findBestDrink(self.getInventory(), bridge);
        if (drink.isEmpty()) return false;
        if (!VillagerDrinkingManager.startDrinking(self, drink)) return false;
        drink.shrink(1);
        return true;
    }

    private static ItemStack findBestDrink(SimpleContainer inventory, ThirstCompatBridge bridge) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !bridge.itemRestoresThirst(stack)) continue;
            int purity = bridge.isPurityWaterContainer(stack) ? Math.max(0, bridge.purity(stack)) : 0;
            int score = purity * 10_000
                    + Math.max(0, bridge.quenched(stack)) * 100
                    + Math.max(0, bridge.hydration(stack)) * 10
                    + (bridge.isDrink(stack) ? 1 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = stack;
            }
        }
        return best;
    }

    private static boolean shouldApplyDehydrationDamage(ServerLevel level) {
        if (level.getServer().isHardcore()) return true;
        return TownsteadConfig.isThirstLethalFallbackEnabled();
    }

    private static void storeEmptyBottles(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.isPreferKitchenStorageForEmptyBottlesEnabled()) return;
        SimpleContainer inventory = villager.getInventory();
        int moved = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (moved >= 4) break;
            ItemStack stack = inventory.getItem(i);
            if (!stack.is(Items.GLASS_BOTTLE)) continue;
            ItemStack bottle = stack.split(1);
            if (!storeBottle(level, villager, bottle)) {
                ItemStack remainder = inventory.addItem(bottle);
                if (!remainder.isEmpty()) {
                    // If inventory is unexpectedly full, stop to avoid loop churn.
                    break;
                }
                break;
            }
            moved++;
        }
    }

    private static boolean storeBottle(ServerLevel level, VillagerEntityMCA villager, ItemStack bottle) {
        if (bottle.isEmpty()) return true;
        if (TownsteadConfig.isPreferKitchenStorageForEmptyBottlesEnabled()
                && ModCompat.isLoaded("farmersdelight")
                && insertIntoTaggedStorage(level, villager, bottle)) {
            return bottle.isEmpty();
        }
        return NearbyItemSources.insertIntoNearbyStorage(level, villager, bottle, 16, 4);
    }

    private static boolean insertIntoTaggedStorage(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        BlockPos center = villager.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-16, -4, -16),
                center.offset(16, 4, 16))) {
            if (stack.isEmpty()) return true;
            if (TownsteadConfig.isProtectedStorage(level.getBlockState(pos))) continue;
            BlockState state = level.getBlockState(pos);
            if (!(state.is(FD_KITCHEN_STORAGE_TAG) || state.is(FD_KITCHEN_STORAGE_UPGRADED_TAG) || state.is(FD_KITCHEN_STORAGE_NETHER_TAG))) {
                continue;
            }
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                insertIntoContainer(container, stack);
                if (stack.isEmpty()) return true;
            }
            if (be != null) {
                IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                if (handler != null) {
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        stack = handler.insertItem(slot, stack, false);
                        if (stack.isEmpty()) return true;
                    }
                }
            }
        }
        return stack.isEmpty();
    }

    private static void insertIntoContainer(Container container, ItemStack stack) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (slot.isEmpty()) continue;
            if (!ItemStack.isSameItemSameComponents(slot, stack)) continue;
            if (!container.canPlaceItem(i, stack)) continue;
            int limit = Math.min(container.getMaxStackSize(), slot.getMaxStackSize());
            if (slot.getCount() >= limit) continue;
            int move = Math.min(stack.getCount(), limit - slot.getCount());
            slot.grow(move);
            stack.shrink(move);
            container.setChanged();
        }
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (stack.isEmpty()) return;
            ItemStack slot = container.getItem(i);
            if (!slot.isEmpty()) continue;
            if (!container.canPlaceItem(i, stack)) continue;
            int move = Math.min(stack.getCount(), Math.min(container.getMaxStackSize(), stack.getMaxStackSize()));
            container.setItem(i, stack.copyWithCount(move));
            stack.shrink(move);
            container.setChanged();
        }
    }

    private static boolean isGuardPatrolling(VillagerEntityMCA self) {
        var profession = self.getVillagerData().getProfession();
        return (profession == ProfessionsMCA.GUARD || profession == ProfessionsMCA.ARCHER)
                && currentScheduleActivity(self) == Activity.WORK;
    }

    private static boolean isResting(VillagerEntityMCA self) {
        return currentScheduleActivity(self) == Activity.REST;
    }

    private static Activity currentScheduleActivity(VillagerEntityMCA self) {
        long dayTime = self.level().getDayTime() % 24000L;
        return self.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private static void updateSpeedModifier(VillagerEntityMCA self, int currentThirst) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY);
        if (currentThirst <= ThirstData.SPEED_PENALTY_THRESHOLD) {
            if (existing == null) {
                speedAttr.addTransientModifier(new AttributeModifier(
                        TOWNSTEAD_SPEED_PENALTY,
                        ThirstData.SPEED_PENALTY_AMOUNT,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
            return;
        }
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
    }

    private static void removeSpeedModifier(VillagerEntityMCA self) {
        AttributeInstance speedAttr = self.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr == null) return;
        AttributeModifier existing = speedAttr.getModifier(TOWNSTEAD_SPEED_PENALTY);
        if (existing != null) speedAttr.removeModifier(TOWNSTEAD_SPEED_PENALTY);
    }

    private static final class TickState {
        private double prevX;
        private double prevZ;
        private float biomeModifier = 1.0f;
        private long nextBiomeModifierSampleTick;
        private Activity lastActivity;
        private int lastSyncedThirst = -1;
        private int lastSyncedQuenched = -1;
    }
}
