package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.hunger.NearbyItemSources;
import com.aetherianartificer.townstead.leatherworking.LeatherworkerJob;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives a Butchery {@code skin_rack} through its full cure cycle on behalf
 * of a leatherworker villager. Implements {@link LeatherworkerJob} so the
 * profession-level brain task in {@code leatherworking/} can dispatch to
 * this without depending on Butchery directly.
 *
 * <p>Per-session each call to {@link #execute} performs one transition:
 * place hide → salt → soak → (mod's 1800-tick passive cure) → collect.
 * State 27 (mid-cure) is intentionally never picked as a target so the
 * mod's queued 27 → 30 flip can run unbothered.
 *
 * <p>Performance: {@link #findWork} is called from
 * {@code LeatherworkerWorkTask.checkExtraStartConditions} on every brain
 * re-eval. To keep the cost bounded:
 * <ul>
 *   <li>A per-villager negative cache short-circuits re-scans for
 *       {@value #NO_WORK_BACKOFF_TICKS} ticks after a "no work" verdict.</li>
 *   <li>Inventory contents are read once per call into a small bitmask;
 *       per-rack {@code pickAction} consults the mask instead of scanning
 *       the inventory again.</li>
 *   <li>Buildings without an indexed skin rack are skipped via the
 *       {@code building.getBlocks().get(SKIN_RACK_ID)} null check before
 *       any blockstate read.</li>
 *   <li>Once a state-30 rack is found, the loop short-circuits — collect
 *       outranks place/salt/soak so further iteration can't improve the
 *       choice meaningfully.</li>
 * </ul>
 */
public final class SkinRackJob implements LeatherworkerJob {
    private static final int SUPPLY_RADIUS = 16;
    private static final int SUPPLY_VERTICAL = 3;

    /** Skip re-scanning a villager's racks for this many ticks after we last found nothing. */
    private static final long NO_WORK_BACKOFF_TICKS = 40L;

    /** Per-villager "next tick at which findWork is allowed to scan again". */
    private static final Map<UUID, Long> NEXT_SCAN_TICK = new ConcurrentHashMap<>();

    private static final int MASK_HIDE = 0x1;
    private static final int MASK_SALT = 0x2;
    private static final int MASK_CLOTH_WET = 0x4;
    private static final int MASK_CLOTH_DRY = 0x8;

    @Override
    public boolean isAvailable() {
        return ButcheryCompat.isLoaded();
    }

    /**
     * Collect every skin rack position inside a building's volume. We can't
     * rely on {@link Building#getBlocks} alone because that map only keys
     * blocks the building type explicitly declares — vanilla MCA's
     * leatherworker building only declares cauldrons, so any rack placed
     * inside it would never appear in the index. The volume scan is the
     * authoritative path; the indexed lookup is just a fast prefix that
     * covers building types (tannery, butcher_shop_l3) which already declare
     * skin_rack.
     */
    private static List<BlockPos> rackPositionsIn(ServerLevel level, Building building) {
        List<BlockPos> indexed = building.getBlocks().get(SkinRackStateMachine.SKIN_RACK_ID);
        java.util.ArrayList<BlockPos> out = new java.util.ArrayList<>();
        java.util.HashSet<Long> seen = new java.util.HashSet<>();
        if (indexed != null) {
            for (BlockPos p : indexed) {
                if (SkinRackStateMachine.isSkinRack(level.getBlockState(p))) {
                    if (seen.add(p.asLong())) out.add(p.immutable());
                }
            }
        }
        BlockPos p0 = building.getPos0();
        BlockPos p1 = building.getPos1();
        int minX = Math.min(p0.getX(), p1.getX());
        int minY = Math.min(p0.getY(), p1.getY());
        int minZ = Math.min(p0.getZ(), p1.getZ());
        int maxX = Math.max(p0.getX(), p1.getX());
        int maxY = Math.max(p0.getY(), p1.getY());
        int maxZ = Math.max(p0.getZ(), p1.getZ());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    cursor.set(x, y, z);
                    if (!SkinRackStateMachine.isSkinRack(level.getBlockState(cursor))) continue;
                    if (seen.add(cursor.asLong())) out.add(cursor.immutable());
                }
            }
        }
        return out;
    }

    @Override
    public Optional<LeatherworkerJob.Plan> findWork(ServerLevel level, VillagerEntityMCA villager) {
        long now = level.getGameTime();
        Long gateTick = NEXT_SCAN_TICK.get(villager.getUUID());
        if (gateTick != null && now < gateTick) return Optional.empty();

        Village village = resolveVillage(villager).orElse(null);
        if (village == null) {
            backoff(villager, now);
            return Optional.empty();
        }

        int supplyMask = readSupplyMask(villager);
        BlockPos origin = villager.blockPosition();
        Plan best = null;
        double bestDsq = Double.MAX_VALUE;
        boolean salteRackPendingNoWetCloth = false;

        outer:
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) continue;
            List<BlockPos> racks = rackPositionsIn(level, building);
            if (racks.isEmpty()) continue;
            for (BlockPos rackPos : racks) {
                BlockState state = level.getBlockState(rackPos);
                // rackPositionsIn already verifies isSkinRack at the snapshot
                // time, but re-check here in case the brain re-eval is on
                // the same tick the player removed the block.
                if (!SkinRackStateMachine.isSkinRack(state)) continue;

                if (SkinRackStateMachine.currentState(state) == SkinRackStateMachine.STATE_SALTED
                        && (supplyMask & MASK_CLOTH_WET) == 0) {
                    salteRackPendingNoWetCloth = true;
                }

                Action action = pickAction(state, supplyMask);
                if (action == null) continue;

                double dsq = rackPos.distSqr(origin);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = new Plan(rackPos, action);
                    if (action == Action.COLLECT) {
                        break outer;
                    }
                }
            }
        }

        // Fallback: a salted rack is waiting, but our cloth is dry. If the
        // villager carries any dry cloth and the village has a water
        // cauldron, walk to the cauldron and re-wet there. Next session
        // resumes the rack soak.
        //
        // Cauldron picking order:
        //   1. The villager's own JOB_SITE cauldron, if it's a water
        //      cauldron — vanilla's WorkAtPoi behavior is constantly
        //      pulling them there anyway, so picking anything else creates
        //      a tug-of-war over WALK_TARGET that reads as "wandering".
        //   2. Otherwise the nearest indexed water cauldron in the village.
        if (best == null
                && salteRackPendingNoWetCloth
                && (supplyMask & MASK_CLOTH_DRY) != 0) {
            BlockPos cauldron = jobSiteWaterCauldron(level, villager);
            if (cauldron == null) cauldron = findNearestWaterCauldron(level, village, origin);
            if (cauldron != null) {
                best = new Plan(cauldron, Action.WET_CLOTH);
            }
        }

        if (best == null) {
            backoff(villager, now);
            return Optional.empty();
        }
        // Found work: clear the throttle so the next session re-evaluates promptly.
        NEXT_SCAN_TICK.remove(villager.getUUID());
        log(villager, "findWork picked action={} anchor={} mask={}",
                best.action(), best.anchor(), Integer.toBinaryString(supplyMask));
        return Optional.of(best);
    }

    /**
     * Resolve the villager's bound job-site block to a water cauldron, or
     * {@code null} if there is no job site, the dimension differs, or the
     * block at that position isn't a water cauldron right now (player may
     * have drained or replaced it).
     */
    @Nullable
    private static BlockPos jobSiteWaterCauldron(ServerLevel level, VillagerEntityMCA villager) {
        var brain = villager.getBrain();
        var memory = brain.getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE);
        if (memory == null || memory.isEmpty()) return null;
        net.minecraft.core.GlobalPos gp = memory.get();
        if (!gp.dimension().equals(level.dimension())) return null;
        BlockPos pos = gp.pos();
        if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) return null;
        return pos;
    }

    @Nullable
    private static BlockPos findNearestWaterCauldron(ServerLevel level, Village village, BlockPos origin) {
        BlockPos best = null;
        double bestDsq = Double.MAX_VALUE;
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) continue;
            // The leatherworker building type already requires
            // #minecraft:cauldrons, so the indexed positions cover the
            // common case. Iterate them directly to avoid a volume scan.
            List<BlockPos> cauldrons = building.getBlocks().get(WATER_CAULDRON_ID);
            if (cauldrons == null || cauldrons.isEmpty()) continue;
            for (BlockPos pos : cauldrons) {
                BlockState state = level.getBlockState(pos);
                if (!state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) continue;
                double dsq = pos.distSqr(origin);
                if (dsq < bestDsq) {
                    bestDsq = dsq;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }

    private static final net.minecraft.resources.ResourceLocation WATER_CAULDRON_ID =
            //? if >=1.21 {
            net.minecraft.resources.ResourceLocation.parse("minecraft:water_cauldron");
            //?} else {
            /*new net.minecraft.resources.ResourceLocation("minecraft", "water_cauldron");
            *///?}

    private static void backoff(VillagerEntityMCA villager, long now) {
        NEXT_SCAN_TICK.put(villager.getUUID(), now + NO_WORK_BACKOFF_TICKS);
    }

    public static void forget(VillagerEntityMCA villager) {
        NEXT_SCAN_TICK.remove(villager.getUUID());
    }

    private static boolean debugEnabled() {
        try {
            return TownsteadConfig.DEBUG_VILLAGER_AI.get();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void log(VillagerEntityMCA villager, String message, Object... args) {
        if (!debugEnabled()) return;
        String formatted = message;
        for (Object arg : args) {
            formatted = formatted.replaceFirst("\\{}",
                    java.util.regex.Matcher.quoteReplacement(String.valueOf(arg)));
        }
        Townstead.LOGGER.info("[SkinRackJob] villager={} {}",
                villager.getStringUUID(), formatted);
    }

    private static int readSupplyMask(VillagerEntityMCA villager) {
        SimpleContainer inv = villager.getInventory();
        int mask = 0;
        int needed = MASK_HIDE | MASK_SALT | MASK_CLOTH_WET | MASK_CLOTH_DRY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if ((mask & MASK_HIDE) == 0 && SkinRackStateMachine.isHideItem(stack)) {
                mask |= MASK_HIDE;
            } else if ((mask & MASK_SALT) == 0 && SkinRackStateMachine.isSaltItem(stack)) {
                mask |= MASK_SALT;
            } else if (SpongeRagHelper.isCloth(stack)) {
                if (SpongeRagHelper.isWet(stack)) mask |= MASK_CLOTH_WET;
                else mask |= MASK_CLOTH_DRY;
            }
            if (mask == needed) break;
        }
        return mask;
    }

    @Override
    public void execute(ServerLevel level, VillagerEntityMCA villager, LeatherworkerJob.Plan basePlan) {
        if (!(basePlan instanceof Plan plan)) return;
        BlockPos pos = plan.anchor();
        BlockState state = level.getBlockState(pos);
        log(villager, "execute enter plannedAction={} pos={} liveBlock={} liveState={} villagerPos={}",
                plan.action(), pos,
                net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                SkinRackStateMachine.isSkinRack(state) ? SkinRackStateMachine.currentState(state) : -1,
                villager.blockPosition());

        if (plan.action() == Action.WET_CLOTH) {
            if (state.is(net.minecraft.world.level.block.Blocks.WATER_CAULDRON)) {
                doWetCloth(level, villager, pos);
            } else {
                log(villager, "execute WET_CLOTH abort: not a water cauldron at pos={}", pos);
            }
            return;
        }

        if (!SkinRackStateMachine.isSkinRack(state)) {
            log(villager, "execute abort: anchor is not a skin rack pos={}", pos);
            return;
        }

        Action action = pickAction(state, readSupplyMask(villager));
        log(villager, "execute re-picked action={} (planned was {}) liveState={}",
                action, plan.action(), SkinRackStateMachine.currentState(state));
        if (action == null) return;

        switch (action) {
            case PLACE -> doPlace(level, villager, pos);
            case SALT -> doSalt(level, villager, pos);
            case SOAK -> doSoak(level, villager, pos);
            case COLLECT -> doCollect(level, villager, pos, state);
            case WET_CLOTH -> { /* unreachable for rack target */ }
        }
    }

    private static void doWetCloth(ServerLevel level, VillagerEntityMCA villager, BlockPos cauldronPos) {
        ItemStack cloth = findFirstMatching(villager,
                stack -> SpongeRagHelper.isCloth(stack) && !SpongeRagHelper.isWet(stack));
        if (cloth.isEmpty()) return;
        equipMainHand(villager, cloth);
        villager.swing(InteractionHand.MAIN_HAND, true);
        SkinRackStateMachine.wetClothAtCauldron(level, cauldronPos, cloth);
    }

    @Override
    public boolean tryPullMissingSupply(ServerLevel level, VillagerEntityMCA villager) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()) return false;
        Village village = resolveVillage(villager).orElse(null);
        if (village == null) return false;

        boolean wantsHide = false;
        boolean wantsSalt = false;
        boolean wantsCloth = false;

        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) continue;
            for (BlockPos rackPos : rackPositionsIn(level, building)) {
                BlockState state = level.getBlockState(rackPos);
                if (!SkinRackStateMachine.isSkinRack(state)) continue;
                int s = SkinRackStateMachine.currentState(state);
                if (s == SkinRackStateMachine.STATE_EMPTY) {
                    if (!hasHideInInventory(villager)) wantsHide = true;
                } else if (SkinRackStateMachine.isRawHide(state)) {
                    if (!hasSaltInInventory(villager)) wantsSalt = true;
                } else if (s == SkinRackStateMachine.STATE_SALTED) {
                    if (!hasAnyClothInInventory(villager)) wantsCloth = true;
                }
            }
        }

        BlockPos anchor = villager.blockPosition();
        boolean pulled =
                (wantsHide && pullOne(level, villager, anchor, SkinRackStateMachine::isHideItem))
                        || (wantsSalt && pullOne(level, villager, anchor, SkinRackStateMachine::isSaltItem))
                        || (wantsCloth && pullOne(level, villager, anchor,
                                SkinRackJob::clothPullPredicate, SkinRackJob::clothPullScore));
        // A successful pull changes inventory contents; clear the negative
        // cache so the next brain re-eval re-scans immediately instead of
        // waiting out the backoff with the supplies we just acquired.
        if (pulled) NEXT_SCAN_TICK.remove(villager.getUUID());
        return pulled;
    }

    private static boolean pullOne(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
            java.util.function.Predicate<ItemStack> matcher) {
        return pullOne(level, villager, anchor, matcher, ItemStack::getCount);
    }

    private static boolean pullOne(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor,
            java.util.function.Predicate<ItemStack> matcher,
            java.util.function.ToIntFunction<ItemStack> scorer) {
        return NearbyItemSources.pullSingleToInventory(
                level, villager, SUPPLY_RADIUS, SUPPLY_VERTICAL,
                matcher, scorer, anchor);
    }

    private static boolean clothPullPredicate(ItemStack stack) {
        return SpongeRagHelper.isCloth(stack);
    }

    /** Wet cloths score higher than dry; among wet, higher wetness wins. */
    private static int clothPullScore(ItemStack stack) {
        int wetness = SpongeRagHelper.readWetness(stack);
        return wetness * 100 + stack.getCount();
    }

    @Override
    @Nullable
    public String missingSupplyDialogueKey(ServerLevel level, VillagerEntityMCA villager) {
        Village village = resolveVillage(villager).orElse(null);
        if (village == null) return null;

        boolean needsHide = false;
        boolean needsSalt = false;
        boolean needsCloth = false;
        boolean needsStorageForLeather = false;

        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete()) continue;
            for (BlockPos rackPos : rackPositionsIn(level, building)) {
                BlockState state = level.getBlockState(rackPos);
                if (!SkinRackStateMachine.isSkinRack(state)) continue;
                int s = SkinRackStateMachine.currentState(state);
                if (s == SkinRackStateMachine.STATE_EMPTY) {
                    if (!hasOrCanPullHide(level, villager, rackPos)) needsHide = true;
                } else if (SkinRackStateMachine.isRawHide(state)) {
                    if (!hasOrCanPullSalt(level, villager, rackPos)) needsSalt = true;
                } else if (s == SkinRackStateMachine.STATE_SALTED) {
                    if (!hasOrCanPullCloth(level, villager, rackPos)) needsCloth = true;
                } else if (s == SkinRackStateMachine.STATE_CURED) {
                    needsStorageForLeather |= isInventoryFullForItem(villager, Items.LEATHER);
                }
            }
        }
        if (needsHide) return "dialogue.chat.leatherworker_request.no_hide";
        if (needsSalt) return "dialogue.chat.leatherworker_request.no_salt";
        if (needsCloth) return "dialogue.chat.leatherworker_request.no_wet_sponge";
        if (needsStorageForLeather) return "dialogue.chat.leatherworker_request.no_storage";
        return null;
    }

    // ── action selection ──

    private enum Action { PLACE, SALT, SOAK, COLLECT, WET_CLOTH }

    @Nullable
    private static Action pickAction(BlockState state, int supplyMask) {
        int s = SkinRackStateMachine.currentState(state);
        if (s == SkinRackStateMachine.STATE_SOAKED) return null; // mod's timer owns this state
        if (s == SkinRackStateMachine.STATE_CURED) return Action.COLLECT;
        if (s == SkinRackStateMachine.STATE_SALTED) {
            // Wet cloth available → soak immediately. Dry cloth + cauldron
            // is handled by the caller, which substitutes a WET_CLOTH plan
            // targeting the cauldron rather than the rack.
            return (supplyMask & MASK_CLOTH_WET) != 0 ? Action.SOAK : null;
        }
        if (s == SkinRackStateMachine.STATE_EMPTY) {
            return (supplyMask & MASK_HIDE) != 0 ? Action.PLACE : null;
        }
        if (SkinRackStateMachine.isRawHide(state)) {
            return (supplyMask & MASK_SALT) != 0 ? Action.SALT : null;
        }
        return null;
    }

    // ── transitions ──

    private static void doPlace(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack hide = findFirstMatching(villager, SkinRackStateMachine::isHideItem);
        log(villager, "doPlace pos={} foundHide={} count={} mainHandBefore={} invHideTotal={}",
                pos,
                hide.isEmpty() ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(hide.getItem()),
                hide.getCount(),
                villager.getMainHandItem().isEmpty() ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(villager.getMainHandItem().getItem()),
                countMatching(villager, SkinRackStateMachine::isHideItem));
        if (hide.isEmpty()) return;
        villager.swing(InteractionHand.MAIN_HAND, true);
        boolean ok = SkinRackStateMachine.placeHide(level, pos, hide);
        log(villager, "doPlace result ok={} invHideAfter={} mainHandAfter={}",
                ok,
                countMatching(villager, SkinRackStateMachine::isHideItem),
                villager.getMainHandItem().isEmpty() ? null : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(villager.getMainHandItem().getItem()));
        if (ok) {
            equipMainHand(villager, hide);
        }
    }

    private static int countMatching(VillagerEntityMCA villager, java.util.function.Predicate<ItemStack> matcher) {
        SimpleContainer inv = villager.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (matcher.test(s)) total += s.getCount();
        }
        return total;
    }

    private static void doSalt(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack salt = findFirstMatching(villager, SkinRackStateMachine::isSaltItem);
        if (salt.isEmpty()) return;
        villager.swing(InteractionHand.MAIN_HAND, true);
        if (SkinRackStateMachine.applySalt(level, pos, salt)) {
            equipMainHand(villager, salt);
        }
    }

    private static void doSoak(ServerLevel level, VillagerEntityMCA villager, BlockPos pos) {
        ItemStack cloth = findFirstMatching(villager, SpongeRagHelper::isWet);
        if (cloth.isEmpty()) return;
        villager.swing(InteractionHand.MAIN_HAND, true);
        if (SkinRackStateMachine.applySoak(level, pos, cloth)) {
            equipMainHand(villager, cloth);
        }
    }

    private static void doCollect(ServerLevel level, VillagerEntityMCA villager, BlockPos pos, BlockState state) {
        // Mod requires empty hand for state 30; mirror by stashing whatever
        // the villager currently holds into inventory before the swing.
        ItemStack held = villager.getMainHandItem();
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (!held.isEmpty()) {
            ItemStack leftover = villager.getInventory().addItem(held);
            if (!leftover.isEmpty()) spawnAtFeet(level, villager, leftover);
        }
        villager.swing(InteractionHand.MAIN_HAND, true);

        ItemStack drop = SkinRackStateMachine.collectAndClear(level, pos, state);
        if (drop.isEmpty()) return;
        ItemStack leftover = villager.getInventory().addItem(drop);
        if (!leftover.isEmpty()) {
            // Inventory full: drop at the rack so the player notices and so
            // the workflow doesn't strand the leather inside a closed slot.
            SkinRackStateMachine.spawnDrop(level, pos, leftover);
        }
    }

    private static void spawnAtFeet(ServerLevel level, VillagerEntityMCA villager, ItemStack stack) {
        ItemEntity ie = new ItemEntity(level,
                villager.getX(), villager.getY() + 0.25, villager.getZ(), stack);
        ie.setPickUpDelay(10);
        level.addFreshEntity(ie);
    }

    // ── inventory & supply helpers ──

    private static ItemStack findFirstMatching(VillagerEntityMCA villager, java.util.function.Predicate<ItemStack> matcher) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (matcher.test(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void equipMainHand(VillagerEntityMCA villager, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (villager.getMainHandItem() == stack) return;
        // Display only: copy a single unit. The previously held item
        // (always a 1-unit display copy we installed in a prior cycle, or
        // empty) is discarded by setItemInHand; that's correct because the
        // copy was never withdrawn from inventory in the first place.
        // Recovering it would duplicate.
        //? if >=1.21 {
        ItemStack display = stack.copyWithCount(1);
        //?} else {
        /*ItemStack display = stack.copy();
        display.setCount(1);
        *///?}
        villager.setItemInHand(InteractionHand.MAIN_HAND, display);
    }

    private static boolean hasHideInInventory(VillagerEntityMCA villager) {
        return !findFirstMatching(villager, SkinRackStateMachine::isHideItem).isEmpty();
    }

    private static boolean hasSaltInInventory(VillagerEntityMCA villager) {
        return !findFirstMatching(villager, SkinRackStateMachine::isSaltItem).isEmpty();
    }

    private static boolean hasWetClothInInventory(VillagerEntityMCA villager) {
        return !findFirstMatching(villager, SpongeRagHelper::isWet).isEmpty();
    }

    private static boolean hasAnyClothInInventory(VillagerEntityMCA villager) {
        return !findFirstMatching(villager, SpongeRagHelper::isCloth).isEmpty();
    }

    private static boolean hasOrCanPullHide(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (hasHideInInventory(villager)) return true;
        return findInStorage(level, villager, anchor, SkinRackStateMachine::isHideItem) != null;
    }

    private static boolean hasOrCanPullSalt(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (hasSaltInInventory(villager)) return true;
        return findInStorage(level, villager, anchor, SkinRackStateMachine::isSaltItem) != null;
    }

    private static boolean hasOrCanPullCloth(ServerLevel level, VillagerEntityMCA villager, BlockPos anchor) {
        if (hasAnyClothInInventory(villager)) return true;
        return findInStorage(level, villager, anchor, SpongeRagHelper::isCloth) != null;
    }

    @Nullable
    private static NearbyItemSources.ContainerSlot findInStorage(ServerLevel level, VillagerEntityMCA villager,
            BlockPos anchor, java.util.function.Predicate<ItemStack> matcher) {
        if (!TownsteadConfig.ENABLE_CONTAINER_SOURCING.get()) return null;
        return NearbyItemSources.findBestNearbySlot(
                level, villager, SUPPLY_RADIUS, SUPPLY_VERTICAL,
                matcher, ItemStack::getCount, anchor);
    }

    private static boolean isInventoryFullForItem(VillagerEntityMCA villager, net.minecraft.world.item.Item item) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) return false;
            if (stack.getItem() == item && stack.getCount() < stack.getMaxStackSize()) return false;
        }
        return true;
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }

    /** Plan subclass that carries the chosen action so {@link #execute} can replay the same decision. */
    public static final class Plan extends LeatherworkerJob.Plan {
        private final Action action;

        Plan(BlockPos anchor, Action action) {
            super(anchor);
            this.action = action;
        }

        public Action action() {
            return action;
        }
    }
}
