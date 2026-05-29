package com.aetherianartificer.townstead.compat.starcatcher;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight Starcatcher integration, loaded conditionally at runtime.
 *
 * Two responsibilities:
 *   1. Tag-based rod recognition (any item tagged {@code starcatcher:rods}
 *      counts as a valid fishing tool — for inventory pick, hand display,
 *      and damage).
 *   2. Catch-pool roll. Starcatcher fish live in their own data-driven
 *      registry ({@code starcatcher:fish_properties}) and are awarded via a
 *      custom bobber+minigame, never via the vanilla {@code minecraft:gameplay/fishing}
 *      loot table. So when a villager reels in while holding a Starcatcher
 *      rod we replicate the relevant slice of {@code FishingBobEntity.reel()}:
 *      pick a fish via {@code calculateChance} on each registered FishProperties
 *      and produce an itemstack via {@code FishProperties.makeItemStack}.
 *      Minigame, tournaments, tackle attachments, bait consumption, and
 *      golden/perfect catch rolls are intentionally skipped.
 *
 * All Starcatcher API access is reflective so this stays compile-clean
 * whether or not Starcatcher is on the classpath, on either branch.
 */
public final class StarcatcherCompat {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/StarcatcherCompat");
    private static final String MOD_ID = "starcatcher";

    //? if >=1.21 {
    private static final TagKey<Item> RODS_TAG = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "rods"));
    //?} else {
    /*private static final TagKey<Item> RODS_TAG = ItemTags.create(
            new ResourceLocation(MOD_ID, "rods"));
    *///?}

    private StarcatcherCompat() {}

    public static boolean isLoaded() {
        return ModCompat.isLoaded(MOD_ID);
    }

    /**
     * True if the stack is a Starcatcher rod (tag-based lookup, so it
     * automatically covers every rod variant the mod adds or future-proofs).
     * Returns false if Starcatcher isn't loaded.
     */
    public static boolean isStarcatcherRod(ItemStack stack) {
        if (!isLoaded()) return false;
        if (stack == null || stack.isEmpty()) return false;
        return stack.is(RODS_TAG);
    }

    // ── Reflective Starcatcher fishing API ──

    // Resolved lazily on first use. Once resolved, the API is either fully
    // available (apiReady = true) or permanently unavailable (apiUnavailable
    // = true) — we don't repeatedly try and log on every reel.
    private static volatile boolean apiResolved;
    private static volatile boolean apiUnavailable;
    private static Class<?> fishPropertiesClass;
    private static Method getFishesMethod;       // (Level) -> List<FishProperties>
    private static Method getNonFishesMethod;    // (Level) -> List<FishProperties>
    private static Method calculateChanceMethod; // (Entity, Level, ItemStack, Context) -> int
    private static Method makeItemStackMethod;   // static (ItemStack rod, FishProperties, int, int, float, boolean, Player, boolean) -> ItemStack
    private static Method loadTreasureMethod;    // (ServerPlayer) -> FishProperties
    private static Method sizeWeightMethod;      // () -> SizeAndWeight
    private static Field sizeAverageField;
    private static Field weightAverageField;
    // Context enum constant reflecting AbstractFishRestriction.Context.FISHING.
    private static Object contextFishing;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object lookupEnumConstant(Class<?> enumCls, String name) {
        return Enum.valueOf((Class<Enum>) enumCls.asSubclass(Enum.class), name);
    }

    private static synchronized boolean ensureApi() {
        if (apiResolved) return !apiUnavailable;
        apiResolved = true;
        try {
            ClassLoader cl = StarcatcherCompat.class.getClassLoader();
            fishPropertiesClass = Class.forName(
                    "com.wdiscute.starcatcher.registry.FishProperties", true, cl);
            Class<?> levelCls = Class.forName("net.minecraft.world.level.Level");
            Class<?> entityCls = Class.forName("net.minecraft.world.entity.Entity");
            Class<?> stackCls = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> playerCls = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> serverPlayerCls = Class.forName("net.minecraft.server.level.ServerPlayer");
            Class<?> restrictionCls = Class.forName(
                    "com.wdiscute.starcatcher.registry.fishrestrictions.AbstractFishRestriction");
            Class<?> contextCls = null;
            for (Class<?> inner : restrictionCls.getDeclaredClasses()) {
                if ("Context".equals(inner.getSimpleName())) {
                    contextCls = inner;
                    break;
                }
            }
            if (contextCls == null) throw new NoSuchMethodException("Context enum not found");
            contextFishing = lookupEnumConstant(contextCls, "FISHING");

            getFishesMethod = fishPropertiesClass.getMethod("getFishes", levelCls);
            getNonFishesMethod = fishPropertiesClass.getMethod("getNonFishes", levelCls);
            calculateChanceMethod = fishPropertiesClass.getMethod(
                    "calculateChance", entityCls, levelCls, stackCls, contextCls);
            makeItemStackMethod = fishPropertiesClass.getMethod(
                    "makeItemStack", stackCls, fishPropertiesClass,
                    int.class, int.class, float.class, boolean.class, playerCls, boolean.class);
            loadTreasureMethod = fishPropertiesClass.getMethod("loadTreasure", serverPlayerCls);
            sizeWeightMethod = fishPropertiesClass.getMethod("sizeWeight");

            Class<?> sizeWeightCls = sizeWeightMethod.getReturnType();
            // SizeAndWeight is a record; field names are sizeAverage / weightAverage
            // on both 1.20.1 and 1.21.1 branches.
            sizeAverageField = sizeWeightCls.getDeclaredField("sizeAverage");
            weightAverageField = sizeWeightCls.getDeclaredField("weightAverage");
            sizeAverageField.setAccessible(true);
            weightAverageField.setAccessible(true);

            apiUnavailable = false;
            return true;
        } catch (Throwable t) {
            apiUnavailable = true;
            LOGGER.info("Starcatcher fishing API not available ({}); villagers will fall back to vanilla loot for Starcatcher rods.",
                    t.toString());
            return false;
        }
    }

    /**
     * Roll a Starcatcher catch the way the player would, minus minigame/golden/
     * perfect/tackle-attachment effects. Returns an empty list if Starcatcher
     * isn't loaded, the API can't be resolved, or no fish has chance > 0 at
     * this hook position. Caller should fall back to vanilla loot in that case.
     *
     * @param level     server level the hook sits in
     * @param rod       the actual Starcatcher rod stack the villager is holding;
     *                  its bait/hook/bobber data components flow into chance
     *                  calculation
     * @param hook      the vanilla FishingHook (used as the "entity" arg for
     *                  restriction checks — biome/elevation/fluid look at
     *                  {@code entity.blockPosition()})
     * @param fakePlayer the per-fisherman FakePlayer; passed to makeItemStack
     *                  and loadTreasure (both need a Player; loadTreasure needs
     *                  ServerPlayer specifically)
     */
    public static List<ItemStack> rollStarcatcherCatch(ServerLevel level,
                                                       ItemStack rod,
                                                       @Nullable FishingHook hook,
                                                       ServerPlayer fakePlayer) {
        if (!isLoaded() || !ensureApi()) return Collections.emptyList();
        if (level == null || rod == null || rod.isEmpty() || fakePlayer == null) {
            return Collections.emptyList();
        }
        Entity chanceEntity = hook != null ? hook : fakePlayer;
        try {
            // Non-fishes (trophies / secrets / etc.): mirror reel()'s early-break —
            // first one whose chance > 0 wins outright.
            Object chosen = null;
            @SuppressWarnings("unchecked")
            List<Object> nonFishes = (List<Object>) getNonFishesMethod.invoke(null, level);
            for (Object fp : nonFishes) {
                int chance = (int) calculateChanceMethod.invoke(fp, chanceEntity, level, rod, contextFishing);
                if (chance > 0) {
                    chosen = fp;
                    break;
                }
            }

            // If no non-fish hit, weighted pick from fishes by chance.
            if (chosen == null) {
                @SuppressWarnings("unchecked")
                List<Object> fishes = (List<Object>) getFishesMethod.invoke(null, level);
                int totalWeight = 0;
                List<Object> candidates = new ArrayList<>(fishes.size());
                int[] weights = new int[fishes.size()];
                int idx = 0;
                for (Object fp : fishes) {
                    int chance = (int) calculateChanceMethod.invoke(fp, chanceEntity, level, rod, contextFishing);
                    if (chance > 0) {
                        candidates.add(fp);
                        weights[idx++] = chance;
                        totalWeight += chance;
                    }
                }
                if (totalWeight <= 0) {
                    // Nothing legal here — caller falls back to vanilla loot.
                    return Collections.emptyList();
                }
                int roll = level.random.nextInt(totalWeight);
                int acc = 0;
                for (int i = 0; i < candidates.size(); i++) {
                    acc += weights[i];
                    if (roll < acc) {
                        chosen = candidates.get(i);
                        break;
                    }
                }
                if (chosen == null) chosen = candidates.get(candidates.size() - 1);
            }

            // Load treasure data so the chosen fp carries its full catch info.
            try {
                chosen = loadTreasureMethod.invoke(chosen, fakePlayer);
            } catch (Throwable t) {
                // loadTreasure can fail if data maps aren't reachable for this fp;
                // fall through with the un-loaded fp — makeItemStack still works.
            }

            // Use the fp's average size/weight; villagers don't play the minigame,
            // so the percentile-based variance is moot. golden=false, perfect=false.
            Object sw = sizeWeightMethod.invoke(chosen);
            float sizeAvg = sizeAverageField.getFloat(sw);
            float weightAvg = weightAverageField.getFloat(sw);
            int size = Math.max(1, Math.round(sizeAvg));
            int weight = Math.max(1, Math.round(weightAvg));

            ItemStack catchStack = (ItemStack) makeItemStackMethod.invoke(
                    null, rod, chosen, size, weight, 50.0F, false, fakePlayer, false);
            if (catchStack == null || catchStack.isEmpty()) return Collections.emptyList();
            return List.of(catchStack);
        } catch (Throwable t) {
            LOGGER.debug("Starcatcher catch roll failed: {}", t.toString());
            return Collections.emptyList();
        }
    }
}
