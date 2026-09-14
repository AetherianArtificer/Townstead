package com.aetherianartificer.townstead.mixin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageMourningGravesMixinTest {
    @Test
    void copiesUnmodifiableGraveListsBeforeMcaShufflesThem() throws Exception {
        Method mutableCopy = VillageMourningGravesMixin.class.getDeclaredMethod("mutableCopy", List.class);
        mutableCopy.setAccessible(true);

        assertTrue(Modifier.isPrivate(mutableCopy.getModifiers()),
                "Mixin helper methods must be private or Mixin rejects the class at runtime");
        assertTrue(Modifier.isStatic(mutableCopy.getModifiers()));

        @SuppressWarnings("unchecked")
        List<Integer> graves = (List<Integer>) mutableCopy.invoke(null, List.of(1, 2, 3));

        assertDoesNotThrow(() -> Collections.shuffle(graves));
        assertEquals(3, graves.size());
    }
}
