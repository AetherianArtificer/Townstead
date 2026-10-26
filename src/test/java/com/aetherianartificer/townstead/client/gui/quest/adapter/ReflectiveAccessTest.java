package com.aetherianartificer.townstead.client.gui.quest.adapter;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the variadic match. NeoForge's {@code PacketDistributor#sendToServer(payload, payload...)}
 * has the same shape as {@link Sender#send(String, String...)}, and an adapter that cannot resolve it
 * loses its packet with no error at all.
 */
class ReflectiveAccessTest {
    @Test
    void resolvesAVariadicStaticCallWithNoTrailingArguments() throws ReflectiveOperationException {
        Sender.calls.clear();
        ReflectiveAccess.callStatic(Sender.class, "send", "one");
        assertEquals(List.of("one[]"), Sender.calls);
    }

    @Test
    void packsLooseTrailingArgumentsIntoTheArray() throws ReflectiveOperationException {
        Sender.calls.clear();
        ReflectiveAccess.callStatic(Sender.class, "send", "one", "two", "three");
        assertEquals(List.of("one[two, three]"), Sender.calls);
    }

    @Test
    void prefersAnExactMatchOverTheVariadicOverload() throws ReflectiveOperationException {
        Sender.calls.clear();
        ReflectiveAccess.callStatic(Sender.class, "exact", 3);
        assertEquals(List.of("exact:3"), Sender.calls);
    }

    @Test
    void stillRejectsAnIncompatibleArgument() {
        assertThrows(NoSuchMethodException.class,
                () -> ReflectiveAccess.callStatic(Sender.class, "send", 42));
    }

    static final class Sender {
        static final List<String> calls = new ArrayList<>();

        static void send(String first, String... rest) {
            calls.add(first + List.of(rest));
        }

        static void exact(int value) {
            calls.add("exact:" + value);
        }

        static void exact(int value, int... rest) {
            calls.add("variadic:" + value);
        }
    }
}
