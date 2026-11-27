package com.aetherianartificer.townstead.client.tts;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.client.tts.AudioCache;

import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Calls MCA's TTS audio cache. Older MCA builds take a public {@code Consumer<OutputStream>};
 * newer ones keep the methods package-private and take their own {@code AudioRetriever}, so
 * the methods are found by name and the download is handed over as whichever type they take.
 */
public final class McaAudio {
    private static final Map<String, Method> METHODS = new ConcurrentHashMap<>();
    private static volatile boolean warned;

    private McaAudio() {
    }

    /** Writes downloaded audio; MCA's own download methods may throw. */
    @FunctionalInterface
    public interface Writer {
        void write(OutputStream output) throws IOException;
    }

    /** Downloads into MCA's disk cache unless the file is already there; true when it is usable. */
    public static boolean cachedRetrieve(String identifier, Writer writer) {
        Method method = method("cachedRetrieve", 2);
        return call(method, identifier, adapt(method, writer));
    }

    /** MCA's get: the disk cache when persistent, otherwise held in memory. */
    public static boolean get(String identifier, Writer writer, boolean persistent) {
        Method method = method("get", 3);
        return call(method, identifier, adapt(method, writer), persistent);
    }

    /** Retries with the retriever MCA itself was given, whatever its type. */
    public static boolean cachedRetrieveWith(String identifier, Object mcaRetriever) {
        return call(method("cachedRetrieve", 2), identifier, mcaRetriever);
    }

    private static Method method(String name, int parameters) {
        return METHODS.computeIfAbsent(name + "/" + parameters, key -> {
            for (Method m : AudioCache.class.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == parameters
                        && m.getParameterTypes()[0] == String.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
            return null;
        });
    }

    private static Object adapt(Method method, Writer writer) {
        if (method == null) return null;
        Class<?> type = method.getParameterTypes()[1];
        if (type == Consumer.class) {
            return (Consumer<OutputStream>) output -> {
                try {
                    writer.write(output);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            };
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, m, args) -> {
            if (m.getDeclaringClass() == Object.class) {
                return switch (m.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "TownsteadAudioRetriever";
                };
            }
            writer.write((OutputStream) args[0]);
            return null;
        });
    }

    private static boolean call(Method method, Object... args) {
        if (method == null || args[1] == null) {
            warn(null);
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null, args));
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn(e);
            return false;
        }
    }

    private static void warn(Throwable error) {
        if (!warned) {
            warned = true;
            Townstead.LOGGER.warn("[TTS] MCA's audio cache could not be called on this MCA build", error);
        }
    }
}
