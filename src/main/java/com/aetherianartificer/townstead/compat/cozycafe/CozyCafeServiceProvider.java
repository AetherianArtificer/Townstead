package com.aetherianartificer.townstead.compat.cozycafe;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.hospitality.service.ExactServiceProduct;
import com.aetherianartificer.townstead.hospitality.service.HospitalityServiceProvider;
import com.aetherianartificer.townstead.hospitality.service.ServiceClaim;
import com.aetherianartificer.townstead.hospitality.service.ServiceFollowup;
import com.aetherianartificer.townstead.hospitality.service.ServiceFollowupCompletion;
import com.aetherianartificer.townstead.hospitality.service.ServiceFulfillment;
import com.aetherianartificer.townstead.hospitality.service.ServicePreparation;
import com.aetherianartificer.townstead.hospitality.service.ServicePreparationResult;
import com.aetherianartificer.townstead.hospitality.service.ServiceRequest;
import com.aetherianartificer.townstead.hospitality.service.ServiceRequestKey;
import com.aetherianartificer.townstead.hospitality.service.ServiceSite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optional, compile-independent Cozy Cafe provider. Published Cozy builds expose enough public
 * state to discover requests, but not a non-player fulfillment method. Fulfillment therefore
 * fails closed until the small upstream automation seam described by {@link #AUTOMATION_METHOD}
 * is present; private fields and serialized NBT are never mutated.
 */
public final class CozyCafeServiceProvider implements HospitalityServiceProvider {
    public static final ResourceLocation ID = id("townstead:cozy_cafe");
    public static final String AUTOMATION_METHOD = "handleAutomationServe";
    private static final ResourceLocation MANAGER_BLOCK = id("cozycafe:cafe_manager");
    private static final ResourceLocation MENU_BLOCK = id("cozycafe:cafe_menu");
    private static final ResourceLocation PLATING_STATION = id("cozycafe:plating_station");
    private static final ResourceLocation SERVING_PLATE = id("cozycafe:serving_plate");
    private static final ResourceLocation DRINK = id("cozycafe:drink");
    private static final ResourceLocation MAIN = id("cozycafe:main");
    private static final ResourceLocation DESSERT = id("cozycafe:dessert");
    private static final ResourceLocation BEVERAGE_DOMAIN = id("townstead:beverage_service");
    private static final ResourceLocation COOK_DOMAIN = id("townstead:cook");
    private static final ResourceLocation BAKER_DOMAIN = id("townstead:baker");
    private static final int MAX_DISCOVERY_RADIUS = 48;
    private static final int MAX_VERTICAL_RADIUS = 8;
    private static final int MENU_SCAN_RADIUS = 20;
    private static final int PREPARATION_SCAN_RADIUS = 20;
    private static final Map<Class<?>, Access> ACCESS = new ConcurrentHashMap<>();

    @Override public ResourceLocation id() { return ID; }

    @Override
    public List<ServiceSite> discover(ServerLevel level, BlockPos center, int radius) {
        if (!ModCompat.isLoaded("cozycafe") || level == null || center == null) return List.of();
        int horizontal = Math.max(0, Math.min(radius, MAX_DISCOVERY_RADIUS));
        int vertical = Math.min(horizontal, MAX_VERTICAL_RADIUS);
        List<ServiceSite> sites = new ArrayList<>();
        BlockPos from = center.offset(-horizontal, -vertical, -horizontal);
        BlockPos to = center.offset(horizontal, vertical, horizontal);
        for (BlockPos cursor : BlockPos.betweenClosed(from, to)) {
            if (!level.isLoaded(cursor) || !isBlock(level, cursor, MANAGER_BLOCK)) continue;
            BlockEntity manager = level.getBlockEntity(cursor);
            if (manager == null || !access(manager).isOpen(manager)) continue;
            BlockPos anchor = new BlockPos(cursor.getX(), cursor.getY(), cursor.getZ());
            Set<Long> menus = connectedMenus(level, anchor);
            Set<Long> bounds = new LinkedHashSet<>(menus);
            bounds.add(anchor.asLong());
            sites.add(new ServiceSite(ID, level.dimension().location(), Long.toString(anchor.asLong()),
                    anchor, bounds, Map.of("authority", "cozycafe")));
        }
        return List.copyOf(sites);
    }

    @Override
    public List<ServiceRequest> requests(ServerLevel level, ServiceSite site) {
        if (!ModCompat.isLoaded("cozycafe") || level == null || site == null
                || !ID.equals(site.provider())) return List.of();
        List<ServiceRequest> requests = new ArrayList<>();
        for (long packed : site.bounds()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos) || !isBlock(level, pos, MENU_BLOCK)) continue;
            BlockEntity menu = level.getBlockEntity(pos);
            if (menu == null) continue;
            Access access = access(menu);
            ItemStack requested = access.requested(menu);
            if (requested.isEmpty() || !access.canServe(menu)) continue;
            BlockPos manager = access.manager(menu);
            if (manager == null || !manager.equals(site.anchor())) continue;
            int course = access.course(menu);
            int waited = Math.max(0, access.waitTime(menu));
            int maxWait = Math.max(1, access.maxWaitTime(menu));
            long deadline = level.getGameTime() + Math.max(0, maxWait - waited);
            ResourceLocation category = category(course);
            ResourceLocation domain = domain(course);
            ResourceLocation item = BuiltInRegistries.ITEM.getKey(requested.getItem());
            ServiceRequestKey key = new ServiceRequestKey(ID, level.dimension().location(), site.id(),
                    Long.toString(pos.asLong()), course + ":" + item);
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("course", Integer.toString(course));
            metadata.put("waited", Integer.toString(waited));
            metadata.put("max_wait", Integer.toString(maxWait));
            metadata.put("manager", Long.toString(manager.asLong()));
            requests.add(new ServiceRequest(key, ServiceRequest.Authority.FOREIGN,
                    ExactServiceProduct.item(requested.copyWithCount(1)), category, domain, pos,
                    deadline, Math.max(1, 1000 - Math.max(0, maxWait - waited)), metadata));
        }
        requests.sort((left, right) -> {
            int priority = Integer.compare(right.priority(), left.priority());
            return priority != 0 ? priority : left.key().request().compareTo(right.key().request());
        });
        return List.copyOf(requests);
    }

    @Override
    public boolean accepts(ServiceRequest request, ItemStack offered) {
        if (request == null || offered == null || offered.isEmpty()) return false;
        if (request.product().matches(offered)) return true;
        if (!SERVING_PLATE.equals(BuiltInRegistries.ITEM.getKey(offered.getItem()))) {
            return false;
        }
        ItemStack stored = Access.storedPlateFood(offered);
        return !stored.isEmpty() && request.product().matches(stored);
    }

    @Override
    public List<ServicePreparation> preparations(ServerLevel level, ServiceSite site,
                                                  ServiceRequest request, ItemStack offered,
                                                  LivingEntity worker) {
        if (!ModCompat.isLoaded("cozycafe") || level == null || site == null || request == null
                || offered == null || offered.isEmpty() || worker == null
                || !request.product().matches(offered)) return List.of();
        List<ServicePreparation> out = new ArrayList<>();
        BlockPos from = site.anchor().offset(-PREPARATION_SCAN_RADIUS, -MAX_VERTICAL_RADIUS,
                -PREPARATION_SCAN_RADIUS);
        BlockPos to = site.anchor().offset(PREPARATION_SCAN_RADIUS, MAX_VERTICAL_RADIUS,
                PREPARATION_SCAN_RADIUS);
        for (BlockPos cursor : BlockPos.betweenClosed(from, to)) {
            if (!level.isLoaded(cursor) || !isBlock(level, cursor, PLATING_STATION)) continue;
            BlockEntity station = level.getBlockEntity(cursor);
            if (station == null) continue;
            Access stationAccess = access(station);
            if (!stationAccess.canAutomationPlate(station, offered)) continue;
            ItemStack current = stationAccess.plateItem(station);
            boolean ready = isCleanServingPlate(current)
                    || (current.isEmpty() && findCleanServingPlate(worker) != null);
            if (!ready) continue;
            out.add(new ServicePreparation(request.key(), cursor,
                    Map.of("kind", "cozycafe:plating_station")));
        }
        return List.copyOf(out);
    }

    @Override
    public ServicePreparationResult prepare(ServerLevel level, ServicePreparation preparation,
                                            ServiceRequest request, ItemStack offered,
                                            LivingEntity worker) {
        if (level == null || preparation == null || request == null || offered == null
                || offered.isEmpty() || worker == null
                || !preparation.request().equals(request.key())
                || !request.product().matches(offered)) {
            return ServicePreparationResult.rejected(ServicePreparationResult.Status.REFUSED,
                    "missing or stale Cozy Cafe preparation input");
        }
        BlockPos pos = preparation.position();
        if (!level.isLoaded(pos) || !isBlock(level, pos, PLATING_STATION)) {
            return ServicePreparationResult.rejected(ServicePreparationResult.Status.CANCELLED,
                    "Cozy Cafe plating station is missing");
        }
        BlockEntity station = level.getBlockEntity(pos);
        if (station == null) {
            return ServicePreparationResult.rejected(ServicePreparationResult.Status.CANCELLED,
                    "Cozy Cafe plating station block entity is missing");
        }
        Access stationAccess = access(station);
        if (!stationAccess.canAutomationPlate(station, offered)) {
            return ServicePreparationResult.rejected(ServicePreparationResult.Status.REFUSED,
                    "Cozy Cafe does not consider this an eligible plated main");
        }
        try {
            ItemStack current = stationAccess.plateItem(station);
            if (current.isEmpty()) {
                ItemStack cleanPlate = findCleanServingPlate(worker);
                if (cleanPlate == null || stationAccess.automationInsertPlate() == null) {
                    return ServicePreparationResult.rejected(ServicePreparationResult.Status.REFUSED,
                            "worker has no clean Cozy Cafe serving plate");
                }
                int plateBefore = cleanPlate.getCount();
                Object inserted = stationAccess.automationInsertPlate().invoke(
                        station, worker, cleanPlate);
                if (!(inserted instanceof Boolean success) || !success
                        || plateBefore - cleanPlate.getCount() != 1) {
                    return ServicePreparationResult.rejected(ServicePreparationResult.Status.ERROR,
                            "native plate insertion did not conserve one plate");
                }
            } else if (!isCleanServingPlate(current)) {
                return ServicePreparationResult.rejected(ServicePreparationResult.Status.REFUSED,
                        "plating station is occupied");
            }

            int before = offered.getCount();
            Method plate = stationAccess.automationPlate();
            Method collect = stationAccess.automationCollect();
            if (plate == null || collect == null) {
                return ServicePreparationResult.rejected(ServicePreparationResult.Status.UNSUPPORTED,
                        "published Cozy Cafe has no complete non-player plating API");
            }
            Object plated = plate.invoke(station, worker, offered);
            if (!(plated instanceof Boolean success) || !success
                    || before - offered.getCount() != 1) {
                return ServicePreparationResult.rejected(ServicePreparationResult.Status.ERROR,
                        "native plating did not conserve one food item");
            }
            Object rawOutput = collect.invoke(station, worker);
            if (!(rawOutput instanceof ItemStack output) || output.isEmpty()
                    || !accepts(request, output)) {
                return ServicePreparationResult.rejected(ServicePreparationResult.Status.ERROR,
                        "native plating did not return the exact requested serving");
            }
            return ServicePreparationResult.success(1, output);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return ServicePreparationResult.rejected(ServicePreparationResult.Status.ERROR,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Override
    public ServiceFulfillment fulfill(ServerLevel level, ServiceClaim claim, ItemStack offered,
                                      LivingEntity supplier, @Nullable BlockPos paymentSink) {
        if (!ModCompat.isLoaded("cozycafe")) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.UNSUPPORTED,
                    "Cozy Cafe is not installed");
        }
        if (level == null || claim == null || supplier == null || offered == null || offered.isEmpty()) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.REFUSED,
                    "missing claim, supplier, or offered stack");
        }
        ServiceRequest request = claim.request();
        if (request.expired(level.getGameTime())) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.CANCELLED, "request expired");
        }
        if (!accepts(request, offered)) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.REFUSED,
                    "offered stack does not exactly satisfy the request");
        }
        BlockPos pos = request.delivery();
        if (!level.isLoaded(pos) || !isBlock(level, pos, MENU_BLOCK)) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.CANCELLED,
                    "Cafe Menu block is missing");
        }
        BlockEntity menu = level.getBlockEntity(pos);
        if (menu == null) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.CANCELLED,
                    "Cafe Menu block entity is missing");
        }
        Access access = access(menu);
        ItemStack current = access.requested(menu);
        if (current.isEmpty() || !request.product().matches(current) || !access.canServe(menu)) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.ALREADY_COMPLETED,
                    "native request changed before delivery");
        }
        Method automation = access.automationServe();
        if (automation == null) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.UNSUPPORTED,
                    "published Cozy Cafe has no non-player service API; expected public "
                            + AUTOMATION_METHOD + "(LivingEntity, ItemStack, BlockPos)");
        }
        int before = offered.getCount();
        try {
            Object raw = automation.invoke(menu, supplier, offered, paymentSink);
            boolean success = raw instanceof Boolean value && value;
            int accepted = before - offered.getCount();
            if (!success) {
                return ServiceFulfillment.rejected(ServiceFulfillment.Status.REFUSED,
                        "Cozy Cafe declined the offered serving");
            }
            if (accepted != request.product().quantity()) {
                Townstead.LOGGER.error("Cozy Cafe automation reported success but accepted {} of {} items at {}",
                        accepted, request.product().quantity(), pos);
                return ServiceFulfillment.rejected(ServiceFulfillment.Status.ERROR,
                        "native success did not conserve the requested quantity");
            }
            return ServiceFulfillment.success(accepted, ItemStack.EMPTY);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return ServiceFulfillment.rejected(ServiceFulfillment.Status.ERROR,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    @Override
    public List<ServiceFollowup> followups(ServerLevel level, ServiceSite site) {
        if (!ModCompat.isLoaded("cozycafe") || level == null || site == null
                || !ID.equals(site.provider())) return List.of();
        List<ServiceFollowup> work = new ArrayList<>();
        for (long packed : site.bounds()) {
            BlockPos pos = BlockPos.of(packed);
            if (!level.isLoaded(pos) || !isBlock(level, pos, MENU_BLOCK)
                    || !booleanProperty(level.getBlockState(pos), "dirty")) continue;
            ServiceRequestKey key = new ServiceRequestKey(ID, level.dimension().location(), site.id(),
                    Long.toString(pos.asLong()), "clear-dirty-plate");
            work.add(new ServiceFollowup(key, id("cozycafe:clear_dirty_plate"), pos,
                    Map.of("output_item", "cozycafe:dirty_serving_plate")));
        }
        return List.copyOf(work);
    }

    @Override
    public ServiceFollowupCompletion completeFollowup(ServerLevel level, ServiceFollowup followup,
                                                       LivingEntity worker) {
        if (level == null || followup == null || worker == null) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.REFUSED,
                    "missing level, follow-up, or worker");
        }
        BlockPos pos = followup.position();
        if (!level.isLoaded(pos) || !isBlock(level, pos, MENU_BLOCK)) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.CANCELLED,
                    "Cafe Menu block is missing");
        }
        if (!booleanProperty(level.getBlockState(pos), "dirty")) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.ALREADY_COMPLETED,
                    "dish is no longer dirty");
        }
        BlockEntity menu = level.getBlockEntity(pos);
        if (menu == null) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.CANCELLED,
                    "Cafe Menu block entity is missing");
        }
        Method clear = access(menu).automationClear();
        if (clear == null) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.UNSUPPORTED,
                    "published Cozy Cafe has no non-player dish-clearing API");
        }
        try {
            Object raw = clear.invoke(menu, worker);
            if (raw instanceof ItemStack output && !output.isEmpty()) {
                return ServiceFollowupCompletion.success(output);
            }
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.REFUSED,
                    "Cozy Cafe did not return a dirty dish");
        } catch (ReflectiveOperationException | RuntimeException exception) {
            return ServiceFollowupCompletion.rejected(ServiceFollowupCompletion.Status.ERROR,
                    exception.getClass().getSimpleName() + ": " + exception.getMessage());
        }
    }

    private static Set<Long> connectedMenus(ServerLevel level, BlockPos manager) {
        Set<Long> menus = new LinkedHashSet<>();
        BlockPos from = manager.offset(-MENU_SCAN_RADIUS, -MAX_VERTICAL_RADIUS, -MENU_SCAN_RADIUS);
        BlockPos to = manager.offset(MENU_SCAN_RADIUS, MAX_VERTICAL_RADIUS, MENU_SCAN_RADIUS);
        for (BlockPos cursor : BlockPos.betweenClosed(from, to)) {
            if (!level.isLoaded(cursor) || !isBlock(level, cursor, MENU_BLOCK)) continue;
            BlockEntity menu = level.getBlockEntity(cursor);
            if (menu == null) continue;
            BlockPos linked = access(menu).manager(menu);
            if (manager.equals(linked)) menus.add(cursor.asLong());
        }
        return Set.copyOf(menus);
    }

    private static boolean isBlock(ServerLevel level, BlockPos pos, ResourceLocation id) {
        return id.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()));
    }

    private static boolean booleanProperty(BlockState state, String name) {
        for (Property<?> property : state.getProperties()) {
            if (!name.equals(property.getName())) continue;
            Comparable<?> value = state.getValue(property);
            return value instanceof Boolean result && result;
        }
        return false;
    }

    private static boolean isCleanServingPlate(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && SERVING_PLATE.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                && Access.storedPlateFood(stack).isEmpty();
    }

    private static @Nullable ItemStack findCleanServingPlate(LivingEntity worker) {
        if (!(worker instanceof net.conczin.mca.entity.VillagerEntityMCA villager)) return null;
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = villager.getInventory().getItem(slot);
            if (isCleanServingPlate(candidate)) return candidate;
        }
        return null;
    }

    private static ResourceLocation category(int course) {
        return switch (course) { case 0 -> DRINK; case 2 -> DESSERT; default -> MAIN; };
    }

    private static ResourceLocation domain(int course) {
        return switch (course) { case 0 -> BEVERAGE_DOMAIN; case 2 -> BAKER_DOMAIN; default -> COOK_DOMAIN; };
    }

    private static Access access(Object target) {
        return ACCESS.computeIfAbsent(target.getClass(), Access::new);
    }

    private static ResourceLocation id(String raw) {
        ResourceLocation result = ResourceLocation.tryParse(raw);
        if (result == null) throw new IllegalStateException("invalid built-in id " + raw);
        return result;
    }

    /** Public-method-only reflection, cached once per Cozy block-entity class. */
    private static final class Access {
        private final Method isOpen;
        private final Method canServe;
        private final Method requested;
        private final Method manager;
        private final Method course;
        private final Method waitTime;
        private final Method maxWaitTime;
        private final Method automationServe;
        private final Method automationClear;
        private final Method plateItem;
        private final Method canAutomationPlate;
        private final Method automationInsertPlate;
        private final Method automationPlate;
        private final Method automationCollect;

        Access(Class<?> type) {
            this.isOpen = method(type, "isOpen");
            this.canServe = method(type, "canServe");
            this.requested = method(type, "getRequestedItem");
            this.manager = method(type, "getCafeManager");
            this.course = method(type, "getCurrentCourse");
            this.waitTime = method(type, "getWaitTime");
            this.maxWaitTime = method(type, "getMaxWaitTime");
            this.automationServe = method(type, AUTOMATION_METHOD,
                    LivingEntity.class, ItemStack.class, BlockPos.class);
            this.automationClear = method(type, "handleAutomationClearDirty", LivingEntity.class);
            this.plateItem = method(type, "getPlateItem");
            this.canAutomationPlate = method(type, "canAutomationPlate", ItemStack.class);
            this.automationInsertPlate = method(type, "handleAutomationInsertPlate",
                    LivingEntity.class, ItemStack.class);
            this.automationPlate = method(type, "handleAutomationPlate",
                    LivingEntity.class, ItemStack.class);
            this.automationCollect = method(type, "handleAutomationCollect", LivingEntity.class);
        }

        boolean isOpen(Object target) { return bool(invoke(isOpen, target)); }
        boolean canServe(Object target) { return bool(invoke(canServe, target)); }
        ItemStack requested(Object target) {
            Object value = invoke(requested, target);
            return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
        }
        BlockPos manager(Object target) {
            Object value = invoke(manager, target);
            return value instanceof BlockPos pos ? new BlockPos(pos.getX(), pos.getY(), pos.getZ()) : null;
        }
        int course(Object target) { return integer(invoke(course, target), 1); }
        int waitTime(Object target) { return integer(invoke(waitTime, target), 0); }
        int maxWaitTime(Object target) { return integer(invoke(maxWaitTime, target), 600); }
        Method automationServe() { return automationServe; }
        Method automationClear() { return automationClear; }
        ItemStack plateItem(Object target) {
            Object value = invoke(plateItem, target);
            return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
        }
        boolean canAutomationPlate(Object target, ItemStack offered) {
            return bool(invoke(canAutomationPlate, target, offered));
        }
        Method automationInsertPlate() { return automationInsertPlate; }
        Method automationPlate() { return automationPlate; }
        Method automationCollect() { return automationCollect; }

        private static Method method(Class<?> type, String name, Class<?>... parameters) {
            try { return type.getMethod(name, parameters); }
            catch (ReflectiveOperationException ignored) { return null; }
        }

        private static Object invoke(Method method, Object target, Object... arguments) {
            if (method == null) return null;
            try { return method.invoke(target, arguments); }
            catch (ReflectiveOperationException | RuntimeException ignored) { return null; }
        }

        private static boolean bool(Object value) { return value instanceof Boolean result && result; }
        private static int integer(Object value, int fallback) {
            return value instanceof Number number ? number.intValue() : fallback;
        }

        private static ItemStack storedPlateFood(ItemStack plate) {
            try {
                Class<?> type = Class.forName("io.github.chakyl.cozycafe.item.ServingPlateItem");
                Method method = type.getMethod("getStoredFood", ItemStack.class);
                Object value = method.invoke(null, plate);
                return value instanceof ItemStack stack ? stack.copy() : ItemStack.EMPTY;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return ItemStack.EMPTY;
            }
        }
    }
}
