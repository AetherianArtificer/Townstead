package com.aetherianartificer.townstead;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TownsteadMixinPluginTest {
    private static final String CONTEXT =
            "traben/entity_model_features/models/animation/EMFAnimationEntityContext";
    private static final String DESC =
            "()Ltraben/entity_model_features/models/animation/state/EMFEntityRenderState;";

    @Test
    void addsTheRemovedEmfAccessorExactlyOnce() {
        ClassNode target = new ClassNode();
        target.name = CONTEXT;

        assertTrue(TownsteadMixinPlugin.addEmfStateBridge(target));
        assertFalse(TownsteadMixinPlugin.addEmfStateBridge(target));
        assertEquals(1, target.methods.stream()
                .filter(method -> method.name.equals("getEmfState") && method.desc.equals(DESC))
                .count());
    }

    @Test
    void preservesTheAccessorOwnedByOlderEmf() {
        ClassNode target = new ClassNode();
        target.name = CONTEXT;
        target.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getEmfState", DESC, null, null));

        assertFalse(TownsteadMixinPlugin.addEmfStateBridge(target));
        assertEquals(1, target.methods.size());
    }

    @Test
    void ignoresUnrelatedClasses() {
        ClassNode target = new ClassNode();
        target.name = "example/Other";
        assertFalse(TownsteadMixinPlugin.addEmfStateBridge(target));
    }
}
