package com.aetherianartificer.townstead.compat.mca;

//? if >=1.21 {
import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomWorkflow;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
//?}

/**
 * Analyze-only access to MCA's room workflow. Townstead's automatic discovery and diagnostics
 * inspect a scan before they decide whether to commit it; MCA's public workflow entry points
 * analyze and commit in one call.
 *
 * <p>Reflective: MCA keeps these methods package-private and has renamed their result records
 * (BuildingScanResult and RegisteredRoomUpdate became RoomWorkflow.Addition and RoomUpdate).
 * Both shapes carry the same components, so results are read by component name.</p>
 */
public final class McaRoomWorkflow {
    private McaRoomWorkflow() {}

    //? if >=1.21 {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

    // One analysis. result is null when MCA's workflow could not be reached. raw
    // is MCA's own record, passed back unchanged to commit it.
    public record Scan(Building.validationResult result, List<String> matchingTypes,
                       Building building, Village village, Object raw) {
        static final Scan UNAVAILABLE = new Scan(null, List.of(), null, null, null);

        public boolean isAmbiguous() {
            return requiresTypeChoice(matchingTypes);
        }
    }

    public static RoomWorkflow workflow(ServerLevel level) {
        return new RoomWorkflow(VillageManager.get(level), level);
    }

    public static Scan analyzeRoom(ServerLevel level, BlockPos source) {
        return scan(invoke(workflow(level), "analyzeRoom", source), "building");
    }

    public static Scan analyzeBuildingAddition(ServerLevel level, BlockPos source) {
        return scan(invoke(workflow(level), "analyzeBuildingAddition", source), "building");
    }

    public static Scan analyzeRegisteredRoomUpdate(ServerLevel level, Village village, int roomId, BlockPos source) {
        return scan(invoke(workflow(level), "analyzeRegisteredRoomUpdate", village, roomId, source), "replacementRoom");
    }

    public static Building.validationResult commitRoomAddition(VillageManager manager, Scan scan) {
        return commit(manager, "commitRoomAddition", scan);
    }

    public static Building.validationResult commitRegisteredRoomUpdate(VillageManager manager, Scan scan) {
        return commit(manager, "commitRegisteredRoomUpdate", scan);
    }

    // A component of one of MCA's scan records, or null when it has none by that name.
    public static Object component(Object record, String name) {
        if (record == null || !record.getClass().isRecord()) return null;
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (!component.getName().equals(name)) continue;
            try {
                Method accessor = component.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(record);
            } catch (ReflectiveOperationException | RuntimeException e) {
                warn("read " + name, e);
                return null;
            }
        }
        return null;
    }

    // A copy of one of MCA's scan records with its candidate type list replaced.
    public static Object withMatchingTypes(Object record, List<String> matchingTypes) {
        RecordComponent[] components = record.getClass().getRecordComponents();
        Object[] values = new Object[components.length];
        Class<?>[] types = new Class<?>[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            values[i] = components[i].getName().equals("matchingTypes")
                    ? matchingTypes : component(record, components[i].getName());
        }
        try {
            Constructor<?> constructor = record.getClass().getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(values);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("copy " + record.getClass().getSimpleName(), e);
            return record;
        }
    }

    @SuppressWarnings("unchecked")
    private static Scan scan(Object raw, String buildingComponent) {
        if (raw == null) return Scan.UNAVAILABLE;
        Object matching = component(raw, "matchingTypes");
        return new Scan(
                (Building.validationResult) component(raw, "result"),
                matching instanceof List<?> list ? (List<String>) list : List.of(),
                (Building) component(raw, buildingComponent),
                (Village) component(raw, "village"),
                raw);
    }

    private static Building.validationResult commit(VillageManager manager, String name, Scan scan) {
        if (scan.raw() == null) return null;
        Object result = invoke(manager, name, scan.raw(), null);
        return result instanceof Building.validationResult value ? value : null;
    }

    private static boolean requiresTypeChoice(List<String> types) {
        try {
            Class<?> resolver = Class.forName("net.conczin.mca.server.world.data.RoomTypeResolver");
            Object result = invokeOn(resolver, null, "requiresTypeChoice", types);
            return result instanceof Boolean value ? value : types.size() > 1;
        } catch (ClassNotFoundException e) {
            return types.size() > 1;
        }
    }

    private static Object invoke(Object target, String name, Object... args) {
        return invokeOn(target.getClass(), target, name, args);
    }

    // Calls a declared method by name and argument count, matching arguments by runtime type.
    private static Object invokeOn(Class<?> owner, Object target, String name, Object... args) {
        Method method = METHODS.computeIfAbsent(owner.getName() + "#" + name + "/" + args.length + key(args),
                ignored -> find(owner, name, args));
        if (method == null) {
            warn(name, null);
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(name, e);
            return null;
        }
    }

    private static String key(Object[] args) {
        StringBuilder key = new StringBuilder();
        for (Object arg : args) key.append(':').append(arg == null ? "null" : arg.getClass().getName());
        return key.toString();
    }

    private static Method find(Class<?> owner, String name, Object[] args) {
        for (Method method : owner.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            Class<?>[] params = method.getParameterTypes();
            boolean fits = true;
            for (int i = 0; i < params.length && fits; i++) {
                fits = args[i] == null ? !params[i].isPrimitive() : wrap(params[i]).isInstance(args[i]);
            }
            if (!fits) continue;
            method.setAccessible(true);
            return method;
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        return type;
    }

    private static void warn(String what, Throwable error) {
        if (WARNED.add(what)) {
            Townstead.LOGGER.warn("[Buildings] MCA room workflow call {} unavailable on this MCA build", what, error);
        }
    }
    //?}
}
