package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.root.ExpressedGenes;
import com.aetherianartificer.townstead.root.Root;
import com.aetherianartificer.townstead.root.RootRegistry;
import com.aetherianartificer.townstead.root.gene.types.CannibalGeneType;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Who may eat the flesh of people, in one place.
 *
 * <p>Two different acts hide in "cannibalism": eating a sapient of another kind (predation — a
 * spider-folk taking human meat is hunting) and eating your own kind (the transgression). The
 * setting is a cumulative ladder: each tier widens who may eat, and no tier revokes the one
 * below it. What counts as sapient flesh at all is the {@code townstead:cannibal_meats} tag;
 * whose flesh it is comes from the per-kind {@code townstead:flesh/...} tags a root names as
 * its own via {@code kin_flesh}.</p>
 */
public final class CannibalismPolicy {

    /** Each tier includes everything below it. */
    public enum Mode {
        /** Nobody eats sapient flesh. */
        OFF,
        /** Roots that declare {@code eats_sapients} may eat other kinds, never their own. */
        PREDATORS,
        /** Predators as above, and cannibals (gene or acquired) may eat anything, kin included. */
        TRAIT,
        /** Anything goes. */
        EVERYONE
    }

    /** What a root's body counts as when it declares nothing: human. */
    public static final ResourceLocation HUMAN_FLESH = rl("townstead:flesh/human");

    /**
     * Chance per entered-starvation episode that a villager comes out of it changed. Extremely
     * low on purpose: over a long game it should be a story, not a mechanic anyone plans around.
     */
    private static final double STARVATION_BREAK_CHANCE = 0.0025;

    private CannibalismPolicy() {}

    public static Mode mode() {
        return TownsteadConfig.CANNIBALISM_MODE.get();
    }

    /**
     * Whether this eater may eat this stack of sapient flesh. Callers have already established
     * the stack IS sapient flesh; a null eater is an unknown mouth and gets the strictest
     * answer that is not a lie.
     */
    public static boolean mayEat(@Nullable LivingEntity eater, ItemStack stack) {
        Mode mode = mode();
        if (mode == Mode.OFF) return false;
        if (mode == Mode.EVERYONE) return true;
        if (!(eater instanceof VillagerEntityMCA villager)) return false;
        if (mode == Mode.TRAIT && isCannibal(villager)) return true;
        // The predators floor, active under both remaining tiers.
        Root root = rootOf(villager);
        if (root == null || !root.eatsSapients()) return false;
        return !isKin(villager, stack);
    }

    /** A cannibal by either door: born with the gene, or broken by starvation. */
    public static boolean isCannibal(VillagerEntityMCA villager) {
        if (TownsteadVillagers.get(villager).life().cannibal()) return true;
        return !ExpressedGenes.instancesOf(villager, CannibalGeneType.Instance.class).isEmpty();
    }

    /** Whether this stack is the eater's own kind's flesh. Players resolve through their root too. */
    public static boolean isKin(LivingEntity eater, ItemStack stack) {
        Root root = rootOf(eater);
        ResourceLocation kin = root != null && root.kinFlesh() != null ? root.kinFlesh() : HUMAN_FLESH;
        return stack.is(net.minecraft.tags.TagKey.create(Registries.ITEM, kin));
    }

    /**
     * Chronicles a player's sapient meal. Players are never gated — what a player puts in their
     * own mouth is their business — but the chronicle does not look away: the fact is recorded
     * with the same key villagers get, and any culture that later minds will mind equally.
     */
    public static void onFinishItem(LivingEntity entity, ItemStack stack) {
        if (!(entity instanceof net.minecraft.server.level.ServerPlayer player)) return;
        if (!FoodSafety.isCannibalFare(stack)) return;
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.taboo(
                player, "townstead:ate_sapient_flesh",
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()),
                Map.of("own_kind", String.valueOf(isKin(player, stack))));
    }

    /**
     * Rolled once each time a villager slides into starvation. Only at the trait tier and above:
     * below that the trait can never be acted on, so it must never appear either — a hidden
     * affliction with no possible expression is a trap for a config change years later.
     */
    public static void onStarvation(ServerLevel level, VillagerEntityMCA villager) {
        Mode mode = mode();
        if (mode != Mode.TRAIT && mode != Mode.EVERYONE) return;
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        if (life.cannibal()) return;
        if (level.random.nextDouble() >= STARVATION_BREAK_CHANCE) return;
        life.setCannibal(true);
        TownsteadVillagers.flush(villager);
        com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps.taboo(
                villager, "townstead:became_cannibal", null, Map.of());
    }

    @Nullable
    private static Root rootOf(LivingEntity eater) {
        String rootId;
        if (eater instanceof VillagerEntityMCA villager) {
            rootId = TownsteadVillagers.get(villager).life().rootId();
        } else if (eater instanceof net.minecraft.world.entity.player.Player player) {
            rootId = com.aetherianartificer.townstead.root.PlayerRoot.getRootId(player);
        } else {
            return null;
        }
        if (rootId == null || rootId.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(rootId);
        return id == null ? null : RootRegistry.resolveOrDefault(id);
    }

    private static ResourceLocation rl(String raw) {
        //? if >=1.21 {
        return ResourceLocation.parse(raw);
        //?} else {
        /*return new ResourceLocation(raw);
        *///?}
    }
}
