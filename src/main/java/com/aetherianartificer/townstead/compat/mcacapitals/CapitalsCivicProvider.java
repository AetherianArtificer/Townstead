package com.aetherianartificer.townstead.compat.mcacapitals;

import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.politics.charter.*;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** No static Capitals links: optional on both loaders. Only allowlisted validated native entry points mutate. */
public final class CapitalsCivicProvider implements CivicProviders.Provider {
    private static final java.util.concurrent.atomic.AtomicBoolean WARNED = new java.util.concurrent.atomic.AtomicBoolean();
    private static void warn(Throwable error) {
        if (WARNED.compareAndSet(false, true)) com.aetherianartificer.townstead.Townstead.LOGGER.warn("Capitals civic integration could not read or execute the installed API", error);
    }
    private static final String ROOT = "com.majesttyx.mcacapitals.";
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final Map<String, String> PETITIONS = Map.of(
            "petition_lord", "mcacapitals_petition_noble_lord", "petition_duke", "mcacapitals_petition_noble_duke",
            "petition_commander", "mcacapitals_petition_commander", "petition_hand", "mcacapitals_petition_hand",
            "petition_throne", "mcacapitals_petition_throne");
    @Override public String id() { return "mcacapitals"; }
    private static Object call(String owner, Object target, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        String key = owner + "#" + name + Arrays.toString(types);
        Method method = METHODS.get(key);
        if (method == null) { method = Class.forName(ROOT + owner).getMethod(name, types); METHODS.put(key, method); }
        return method.invoke(target, args);
    }
    private static Object get(Object record, String method) throws ReflectiveOperationException {
        return call("capital.CapitalRecord", record, method, new Class<?>[0]);
    }
    private static Object capital(ServerLevel level, SettlementRef settlement) throws ReflectiveOperationException {
        if (!level.dimension().location().equals(settlement.dimension())) return null;
        return call("capital.CapitalManager", null, "getCapitalForVillage", new Class<?>[]{ServerLevel.class, Integer.class}, level, settlement.villageId());
    }
    private static CharterSnapshotS2CPayload.Text text(Component c) { return CharterSnapshotS2CPayload.Text.of(c); }
    private static Component tr(String key, Object... args) { return Component.translatable("charter.townstead.capitals." + key, args); }
    private static CharterSnapshotS2CPayload.Civic unavailable(String actor) {
        return new CharterSnapshotS2CPayload.Civic("mcacapitals", actor, "unavailable", true, false,
                text(tr("unavailable")), text(tr("standing_unavailable")), text(tr("unavailable_help")), List.of(), List.of(), List.of());
    }
    @Override public boolean ownsGovernment(net.minecraft.server.MinecraftServer server, SettlementRef settlement) {
        var saved = CharterSavedData.get(server);
        if (saved.externalGovernment(settlement).startsWith("mcacapitals:")) return true;
        if (!ModCompat.isLoaded(id())) return false;
        try {
            for (ServerLevel level : server.getAllLevels()) {
                if (!level.dimension().location().equals(settlement.dimension())) continue;
                Object record = capital(level, settlement);
                if (record == null || (boolean) get(record, "isMonarchyRejected") || get(record, "getState").toString().equals("PENDING")) return false;
                UUID sovereign = (UUID) get(record, (boolean) get(record, "isPlayerSovereign") ? "getPlayerSovereignId" : "getSovereign");
                if (sovereign == null) return false;
                saved.observeGovernment(settlement, "mcacapitals:" + get(record, "getCapitalId"));
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { warn(error); }
        return false;
    }
    @Override public CharterSnapshotS2CPayload.Civic read(ServerPlayer viewer, SettlementRef settlement) {
        var saved = CharterSavedData.get(viewer.server);
        String bound = saved.externalGovernment(settlement);
        if (!ModCompat.isLoaded(id())) return bound.startsWith("mcacapitals:") ? unavailable(bound) : null;
        try {
            Object record = capital(viewer.serverLevel(), settlement);
            if (record == null) return bound.startsWith("mcacapitals:") ? unavailable(bound) : null;
            String actor = "mcacapitals:" + get(record, "getCapitalId");
            if (!bound.isEmpty() && !bound.equals(actor)) return unavailable(bound);
            UUID sovereign = (UUID) get(record, "getSovereign");
            if ((boolean) get(record, "isPlayerSovereign")) sovereign = (UUID) get(record, "getPlayerSovereignId");
            boolean rejected = (boolean) get(record, "isMonarchyRejected");
            boolean governs = !rejected && sovereign != null && !get(record, "getState").toString().equals("PENDING");
            if (governs) saved.observeGovernment(settlement, actor);
            if (!governs && !bound.isEmpty()) return unavailable(actor);
            Class<?> type = Class.forName(ROOT + "capital.CapitalRecord");
            Object authority = call("capital.CapitalPlayerAuthorityResolver", null, "resolve",
                    new Class<?>[]{ServerLevel.class, type, UUID.class}, viewer.serverLevel(), record, viewer.getUUID());
            Component standing = (Component) authority.getClass().getMethod("displayTitle").invoke(authority);
            Object residents = call("capital.CapitalResidentScanner", null, "scanResidents",
                    new Class<?>[]{ServerLevel.class, UUID.class}, viewer.serverLevel(), get(record, "getCapitalId"));
            int reputation = (int) call("util.MCAReputationBridge", null, "getCapitalHeartsScore",
                    new Class<?>[]{ServerLevel.class, Set.class, UUID.class}, viewer.serverLevel(), residents, viewer.getUUID());
            standing = standing.copy().append("\n").append(tr("reputation", reputation));
            boolean mayManage = governs && viewer.getUUID().equals(sovereign);
            List<CharterSnapshotS2CPayload.Role> offices = new ArrayList<>();
            if (governs) {
                office(viewer, record, offices, "sovereign", sovereign);
                String[][] fields = {{"heir", "getHeir"}, {"consort", "getConsort"}, {"hand", "getHand"},
                        {"commander", "getCommander"}, {"maester", "getGrandMaester"}, {"herald", "getHerald"}, {"laws", "getMasterOfLaws"}};
                for (String[] field : fields) office(viewer, record, offices, field[0], (UUID) get(record, field[1]));
            }
            List<CharterSnapshotS2CPayload.Text> history = new ArrayList<>();
            List<?> entries = (List<?>) get(record, "getChronicleEntries");
            for (int i = entries.size() - 1; i >= Math.max(0, entries.size() - 40); i--) {
                Component entry = (Component) call("capital.CapitalChronicleService", null, "renderStoredEntry",
                        new Class<?>[]{String.class}, entries.get(i).toString());
                history.add(text(entry));
            }
            List<CharterSnapshotS2CPayload.Action> actions = new ArrayList<>();
            Entity audience = sovereign == null ? null : viewer.serverLevel().getEntity(sovereign);
            if (governs && audience != null && !(audience instanceof ServerPlayer) && viewer.distanceToSqr(audience) <= 144) {
                for (String operation : PETITIONS.keySet().stream().sorted().toList())
                    actions.add(new CharterSnapshotS2CPayload.Action(operation, text(tr(operation)), text(tr("petition_review")), false));
            }
            if (!governs && !rejected && charterHand(viewer, (UUID) get(record, "getCapitalId")) != null)
                actions.add(new CharterSnapshotS2CPayload.Action("review_charter", text(tr("review_charter")), text(tr("review_charter_help")), false));
            String state = governs ? "active" : rejected ? "rejected" : "pending";
            return new CharterSnapshotS2CPayload.Civic(id(), actor, state, governs, mayManage,
                    text(tr(governs ? "monarchy" : rejected ? "rejected" : "pending")), text(standing), text(tr(governs ? "audience_help" : rejected ? "rejected_help" : charterHand(viewer, (UUID) get(record, "getCapitalId")) != null ? "review_charter_help" : "royal_charter_help")),
                    offices, history, actions);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            warn(error);
            // Never turn an incompatible or temporarily missing external government into a native council.
            return unavailable(bound.isEmpty() ? "mcacapitals:unavailable" : bound);
        }
    }
    private static void office(ServerPlayer viewer, Object capital, List<CharterSnapshotS2CPayload.Role> offices, String role, UUID person) throws ReflectiveOperationException {
        if (person == null) return;
        String name = (String) call("capital.CapitalChronicleIdentitySnapshot", null, "name",
                new Class<?>[]{ServerLevel.class, Class.forName(ROOT + "capital.CapitalRecord"), UUID.class}, viewer.serverLevel(), capital, person);
        offices.add(new CharterSnapshotS2CPayload.Role(text(tr("office." + role)), List.of(text(Component.literal(name)))));
    }
    /** Match the same main-hand-first selection used by Capitals' decision screen. */
    private static net.minecraft.world.InteractionHand charterHand(ServerPlayer viewer, UUID capitalId) throws ReflectiveOperationException {
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            var stack = viewer.getItemInHand(hand);
            if (!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("mcacapitals:royal_charter")) continue;
            var tag = (net.minecraft.nbt.CompoundTag) call("util.ModItemStackData", null, "getCustomData",
                    new Class<?>[]{net.minecraft.world.item.ItemStack.class}, stack);
            return capitalId.toString().equals(tag.getString("CapitalId")) ? hand : null;
        }
        return null;
    }
    @Override public boolean opensScreen(String action) { return action.equals("review_charter"); }
    @Override public boolean execute(ServerPlayer viewer, SettlementRef settlement, String actor, String operation) {
        String command = PETITIONS.get(operation);
        if ((command == null && !operation.equals("review_charter")) || !ModCompat.isLoaded(id())) return false;
        var current = read(viewer, settlement);
        if (current == null || !current.actor().equals(actor) || current.actions().stream().noneMatch(a -> a.id().equals(operation))) return false;
        try {
            Object record = capital(viewer.serverLevel(), settlement);
            if (operation.equals("review_charter")) {
                var hand = charterHand(viewer, (UUID) get(record, "getCapitalId"));
                if (hand == null) return false;
                var stack = viewer.getItemInHand(hand);
                stack.getItem().use(viewer.serverLevel(), viewer, hand);
                return true;
            }
            UUID sovereign = (UUID) get(record, "getSovereign");
            Entity audience = viewer.serverLevel().getEntity(sovereign);
            if (audience == null || viewer.distanceToSqr(audience) > 144) return false;
            // This native route rechecks audience, standing, population, rank conflicts and all petition-specific requirements.
            return (boolean) call("dialogue.CapitalPetitionService", null, "handleCustomCommand",
                    new Class<?>[]{ServerPlayer.class, Entity.class, String.class}, viewer, audience, command);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) { warn(error); return false; }
    }
}
