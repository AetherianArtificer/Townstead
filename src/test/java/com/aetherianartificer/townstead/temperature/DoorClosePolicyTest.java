package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.aetherianartificer.townstead.temperature.DoorClosePolicy.Decision.*;

class DoorClosePolicyTest {
    @Test void finishesOpeningActionEvenAfterNavigationEnds() {
        assertEquals(WAIT, DoorClosePolicy.decide(10, true, true, false, false));
        assertEquals(CLOSE, DoorClosePolicy.decide(20, true, true, false, false));
    }
    @Test void holdsForTrafficThenClosesWhenPassageClears() {
        assertEquals(WAIT, DoorClosePolicy.decide(100, true, true, false, true));
        assertEquals(CLOSE, DoorClosePolicy.decide(110, true, true, false, false));
    }
    @Test void relinquishesPoweredOrAlreadyClosedDoorsWithoutLoadingChunks() {
        assertEquals(FORGET, DoorClosePolicy.decide(100, true, true, true, false));
        assertEquals(FORGET, DoorClosePolicy.decide(100, true, false, false, false));
        assertEquals(WAIT, DoorClosePolicy.decide(100, false, true, false, false));
        assertEquals(FORGET, DoorClosePolicy.decide(1201, false, true, false, false));
    }
    @Test void mcaDoorHookMatchesTheDependencyMethodDescriptor() throws Exception {
        try (var input = getClass().getResourceAsStream("/net/conczin/mca/entity/ai/brain/tasks/SmarterOpenDoorsTask.class")) {
            assertNotNull(input);
            var type = new org.objectweb.asm.tree.ClassNode();
            new org.objectweb.asm.ClassReader(input).accept(type, org.objectweb.asm.ClassReader.SKIP_CODE);
            assertTrue(type.methods.stream().anyMatch(method -> method.name.equals("setOpen")
                    && (method.access & org.objectweb.asm.Opcodes.ACC_STATIC) != 0
                    && method.desc.equals("(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Z)Z")));
        }
    }
}
