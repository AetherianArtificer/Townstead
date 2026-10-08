package com.aetherianartificer.townstead.client.gui.quest.adapter;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Small, deliberately defensive reflection boundary shared by optional quest adapters. */
final class ReflectiveAccess {
    private ReflectiveAccess() {}

    static Class<?> classOrNull(String name) {
        try {
            return Class.forName(name, false, ReflectiveAccess.class.getClassLoader());
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object call(Object receiver, String name, Object... arguments) throws ReflectiveOperationException {
        if (receiver == null) throw new NoSuchMethodException(name + " on null");
        Method method = matching(receiver.getClass(), name, false, arguments);
        method.setAccessible(true);
        return method.invoke(receiver, spread(method, arguments));
    }

    static Object callOrNull(Object receiver, String name, Object... arguments) {
        try {
            return call(receiver, name, arguments);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object callStatic(Class<?> owner, String name, Object... arguments) throws ReflectiveOperationException {
        Method method = matching(owner, name, true, arguments);
        method.setAccessible(true);
        return method.invoke(null, spread(method, arguments));
    }

    static Object field(Object receiver, String name) throws ReflectiveOperationException {
        Class<?> type = receiver instanceof Class<?> clazz ? clazz : receiver.getClass();
        Field field = findField(type, name);
        field.setAccessible(true);
        return field.get(receiver instanceof Class<?> ? null : receiver);
    }

    static Object construct(Class<?> owner, Object... arguments) throws ReflectiveOperationException {
        for (Constructor<?> constructor : owner.getDeclaredConstructors()) {
            if (compatible(constructor.getParameterTypes(), arguments)) {
                constructor.setAccessible(true);
                return constructor.newInstance(arguments);
            }
        }
        throw new NoSuchMethodException(owner.getName() + " constructor/" + arguments.length);
    }

    static List<?> list(Object value) {
        if (value instanceof List<?> list) return list;
        if (value instanceof Collection<?> collection) return List.copyOf(collection);
        if (value instanceof Iterable<?> iterable) {
            List<Object> result = new ArrayList<>();
            iterable.forEach(result::add);
            return result;
        }
        return List.of();
    }

    static Object optional(Object value) {
        return value instanceof Optional<?> optional ? optional.orElse(null) : value;
    }

    static String text(Object value) {
        if (value instanceof Component component) return component.getString();
        return value == null ? "" : value.toString();
    }

    static String itemId(Object value) {
        if (!(value instanceof ItemStack stack) || stack.isEmpty()) return "";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    static boolean bool(Object value) {
        return value instanceof Boolean b && b;
    }

    static long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    static Method matchingOrNull(Class<?> owner, String name, boolean requireStatic, Object... arguments) {
        try {
            return matching(owner, name, requireStatic, arguments);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private static Method matching(Class<?> owner, String name, boolean requireStatic, Object[] arguments)
            throws ReflectiveOperationException {
        Method variadic = null;
        for (Method method : allMethods(owner)) {
            if (!method.getName().equals(name) || Modifier.isStatic(method.getModifiers()) != requireStatic) continue;
            if (compatible(method.getParameterTypes(), arguments)) return method;
            if (variadic == null && variadicCompatible(method, arguments)) variadic = method;
        }
        if (variadic != null) return variadic;
        throw new NoSuchMethodException(owner.getName() + "#" + name + "/" + arguments.length);
    }

    private static List<Method> allMethods(Class<?> owner) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                methods.addAll(List.of(type.getDeclaredMethods()));
            } catch (Throwable ignored) {
                // An optional dependency may itself name another absent optional type.
            }
        }
        try {
            methods.addAll(List.of(owner.getMethods()));
        } catch (Throwable ignored) {
        }
        return methods;
    }

    private static Field findField(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> type = owner; type != null; type = type.getSuperclass()) {
            try {
                return type.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getName() + "#" + name);
    }

    private static boolean compatible(Class<?>[] parameters, Object[] arguments) {
        if (parameters.length != arguments.length) return false;
        for (int i = 0; i < parameters.length; i++) {
            if (!assignable(parameters[i], arguments[i])) return false;
        }
        return true;
    }

    /**
     * A trailing {@code T...} the caller passes as loose arguments, or leaves empty. NeoForge's
     * {@code PacketDistributor#sendToServer(payload, payload...)} is the reason this exists: matched
     * on arity alone it is invisible to a one-argument call, and the adapter silently loses the send.
     */
    private static boolean variadicCompatible(Method method, Object[] arguments) {
        if (!method.isVarArgs()) return false;
        Class<?>[] parameters = method.getParameterTypes();
        int fixed = parameters.length - 1;
        if (arguments.length < fixed) return false;
        for (int i = 0; i < fixed; i++) {
            if (!assignable(parameters[i], arguments[i])) return false;
        }
        Class<?> element = parameters[fixed].getComponentType();
        for (int i = fixed; i < arguments.length; i++) {
            if (!assignable(element, arguments[i])) return false;
        }
        return true;
    }

    /** Packs the trailing arguments of a variadic match into the array the method expects. */
    private static Object[] spread(Method method, Object[] arguments) {
        if (!method.isVarArgs()) return arguments;
        Class<?>[] parameters = method.getParameterTypes();
        int fixed = parameters.length - 1;
        if (arguments.length == parameters.length && parameters[fixed].isInstance(arguments[fixed])) {
            return arguments;
        }
        Object[] packed = new Object[parameters.length];
        System.arraycopy(arguments, 0, packed, 0, fixed);
        Object tail = Array.newInstance(parameters[fixed].getComponentType(), arguments.length - fixed);
        for (int i = fixed; i < arguments.length; i++) Array.set(tail, i - fixed, arguments[i]);
        packed[fixed] = tail;
        return packed;
    }

    private static boolean assignable(Class<?> parameter, Object argument) {
        if (argument == null) return !parameter.isPrimitive();
        return wrap(parameter).isAssignableFrom(argument.getClass());
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }
}
