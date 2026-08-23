package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.work.feedback.WorkFeedbackTicker;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Butchery runtime facts supplied to data-authored profession feedback. */
public final class ButcheryWorkFeedback implements WorkFeedbackTicker.Observer {
    public static final ButcheryWorkFeedback INSTANCE = new ButcheryWorkFeedback();

    //? if >=1.21 {
    private static final ResourceLocation ID = ResourceLocation.parse("townstead:butchery_promotion");
    private static final ResourceLocation PROFESSION = ResourceLocation.parse("minecraft:butcher");
    private static final ResourceLocation HOOK_ID = ResourceLocation.parse("butchery:hook");
    private static final ResourceLocation SKIN_RACK_ID = ResourceLocation.parse("butchery:skin_rack");
    private static final ResourceLocation MEAT_GRINDER_ID = ResourceLocation.parse("butchery:meat_grinder");
    private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:skins"));
    private static final TagKey<Item> RAW_MEATS_TAG = TagKey.create(
            Registries.ITEM, ResourceLocation.parse("butchery:raw_meats"));
    //?} else {
    /*private static final ResourceLocation ID = new ResourceLocation("townstead", "butchery_promotion");
    private static final ResourceLocation PROFESSION = new ResourceLocation("minecraft", "butcher");
    private static final ResourceLocation HOOK_ID = new ResourceLocation("butchery", "hook");
    private static final ResourceLocation SKIN_RACK_ID = new ResourceLocation("butchery", "skin_rack");
    private static final ResourceLocation MEAT_GRINDER_ID = new ResourceLocation("butchery", "meat_grinder");
    private static final TagKey<Item> SKINS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "skins"));
    private static final TagKey<Item> RAW_MEATS_TAG = TagKey.create(
            Registries.ITEM, new ResourceLocation("butchery", "raw_meats"));
    *///?}

    private ButcheryWorkFeedback() {}

    public static void bootstrap() {
        WorkFeedbackTicker.register(INSTANCE);
        register("has_carcass_shop", ButcheryWorkFeedback::hasCarcassShop);
        register("slaughter_disabled", ButcheryWorkFeedback::slaughterDisabled);
        register("no_skinning_knife", ButcheryWorkFeedback::noSkinningKnife);
        register("no_cleaver", ButcheryWorkFeedback::noCleaver);
        register("no_blood_grate", ButcheryWorkFeedback::noBloodGrate);
        register("pending_work", ButcheryWorkFeedback::pendingWork);
        register("no_hook", ButcheryWorkFeedback::noHook);
        register("no_livestock", ButcheryWorkFeedback::noLivestock);
        register("no_skin_rack", ButcheryWorkFeedback::noSkinRack);
        register("no_grinder", ButcheryWorkFeedback::noGrinder);
    }

    private static void register(String path, java.util.function.Predicate<VillagerEntityMCA> signal) {
        //? if >=1.21 {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("townstead_butchery", path);
        //?} else {
        /*ResourceLocation id = new ResourceLocation("townstead_butchery", path);
        *///?}
        com.aetherianartificer.townstead.work.feedback.WorkFeedbackSignals.register(id, signal);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    private static boolean applies(VillagerEntityMCA villager) {
        return ButcheryCompat.isLoaded()
                && com.aetherianartificer.townstead.work.WorkTaskDeclarations.permitsTask(
                villager,
                com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BUTCHERY_SUITE);
    }

    @Override
    public @Nullable WorkFeedbackTicker.Event observe(
            ServerLevel level, VillagerEntityMCA villager) {
        if (!applies(villager)) return null;
        return shopPromotion(villager, level);
    }

    /** Detect a change in the best shop this villager can reach, seeding first sight silently. */
    private static @Nullable WorkFeedbackTicker.Event shopPromotion(
            VillagerEntityMCA villager, ServerLevel level) {
        int currentTier = ButcheryShopScanner.tierFor(level, villager);
        TownsteadVillager.ProfessionMemory memory =
                TownsteadVillagers.get(villager).professionMemory();
        int lastTier = memory.lastSeenShopTier();
        if (lastTier < 0) {
            memory.setLastSeenShopTier(currentTier);
            return null;
        }
        if (currentTier <= lastTier) {
            if (currentTier != lastTier) memory.setLastSeenShopTier(currentTier);
            return null;
        }
        return new WorkFeedbackTicker.Event(PROFESSION,
                "shop_promoted_to_tier_" + currentTier, new Object[0],
                () -> memory.setLastSeenShopTier(currentTier));
    }

    private static boolean hasCarcassShop(VillagerEntityMCA villager) {
        return server(villager) != null && applies(villager)
                && !ButcheryShopScanner.carcassCapableShops(server(villager), villager).isEmpty();
    }

    private static boolean slaughterDisabled(VillagerEntityMCA villager) {
        return applies(villager) && !SlaughterPolicy.slaughterEnabledFor(villager);
    }

    private static boolean noSkinningKnife(VillagerEntityMCA villager) {
        ServerLevel level = server(villager);
        return level != null && applies(villager)
                && CarcassWorkTask.hasPendingSkinningWithoutKnife(level, villager);
    }

    private static boolean noCleaver(VillagerEntityMCA villager) {
        return applies(villager) && !ButcherToolDamage.hasCleaver(villager);
    }

    private static boolean noBloodGrate(VillagerEntityMCA villager) {
        ServerLevel level = server(villager);
        if (level == null || !applies(villager)) return false;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            if (CarcassWorkTask.hasFreshCarcassWithoutBloodGrate(level, ref.building())) return true;
        }
        return false;
    }

    private static boolean pendingWork(VillagerEntityMCA villager) {
        ServerLevel level = server(villager);
        return level != null && applies(villager) && CarcassWorkTask.hasPendingWork(level, villager);
    }

    private static boolean noHook(VillagerEntityMCA villager) {
        ServerLevel level = server(villager);
        if (level == null || !applies(villager)) return false;
        for (ButcheryShopScanner.ShopRef ref : ButcheryShopScanner.carcassCapableShops(level, villager)) {
            List<BlockPos> hooks = ref.building().getBlocks().get(HOOK_ID);
            if (hooks != null && !hooks.isEmpty()) return false;
        }
        return true;
    }

    private static boolean noLivestock(VillagerEntityMCA villager) {
        ServerLevel level = server(villager);
        if (level == null || !applies(villager)) return false;
        for (ButcheryShopScanner.HuntRef ref : ButcheryShopScanner.huntableBuildings(level, villager)) {
            if (hasValidTarget(level, villager, ref.building())) return false;
        }
        return true;
    }

    private static boolean noSkinRack(VillagerEntityMCA villager) {
        return missingStation(villager, SKINS_TAG, SKIN_RACK_ID);
    }

    private static boolean noGrinder(VillagerEntityMCA villager) {
        return missingStation(villager, RAW_MEATS_TAG, MEAT_GRINDER_ID);
    }

    private static boolean missingStation(VillagerEntityMCA villager, TagKey<Item> inventoryTag,
                                          ResourceLocation blockId) {
        Optional<Village> village = resolveVillage(villager);
        return applies(villager) && village.isPresent()
                && hasTaggedItemInInventory(villager, inventoryTag)
                && !villageHasBlock(village.get(), blockId);
    }

    private static boolean hasTaggedItemInInventory(VillagerEntityMCA villager,
                                                     TagKey<Item> tag) {
        for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
            ItemStack stack = villager.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(tag)) return true;
        }
        return false;
    }

    private static boolean villageHasBlock(Village village, ResourceLocation blockId) {
        for (Building building
                : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            if (!building.isComplete()) continue;
            List<BlockPos> positions = building.getBlocks().get(blockId);
            if (positions != null && !positions.isEmpty()) return true;
        }
        return false;
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        return nearest.filter(village -> village.isWithinBorder(villager));
    }

    private static boolean hasValidTarget(ServerLevel level, VillagerEntityMCA villager,
                                          Building building) {
        BlockPos origin = villager.blockPosition();
        AABB search = AABB.ofSize(
                new Vec3(origin.getX() + 0.5, origin.getY() + 0.5, origin.getZ() + 0.5),
                24, 8, 24);
        List<Animal> animals = level.getEntitiesOfClass(Animal.class, search,
                animal -> building.containsPos(animal.blockPosition())
                        && SlaughterPolicy.canSlaughter(villager, animal));
        return !animals.isEmpty();
    }

    private static @Nullable ServerLevel server(VillagerEntityMCA villager) {
        return villager.level() instanceof ServerLevel level ? level : null;
    }
}
