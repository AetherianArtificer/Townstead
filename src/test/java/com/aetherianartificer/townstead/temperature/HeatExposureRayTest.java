package com.aetherianartificer.townstead.temperature;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HeatExposureRayTest {
    @Test
    void environmentalHeatRayUsesEntityFreeConstructor() throws Exception {
        // Inspect the compiled call without bootstrapping game registries in JUnit.
        var calls = new ArrayList<String>();
        int[] nullArguments = {0};
        try (var input = getClass().getResourceAsStream("RoomHeat.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (!name.equals("exposureRay")) return null;
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public void visitInsn(int opcode) {
                            if (opcode == Opcodes.ACONST_NULL) nullArguments[0]++;
                        }
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name,
                                                    String descriptor, boolean isInterface) {
                            calls.add(owner + "." + name + descriptor);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        String contextConstructor = "(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/ClipContext$Block;Lnet/minecraft/world/level/ClipContext$Fluid;Lnet/minecraft/world/phys/shapes/CollisionContext;)V";
        var constructors = new ArrayList<String>();
        try (var input = getClass().getResourceAsStream("/net/minecraft/world/level/ClipContext.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                           String signature, String[] exceptions) {
                    if (name.equals("<init>")) constructors.add(descriptor);
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        }
        if (constructors.contains(contextConstructor)) {
            assertEquals(0, nullArguments[0], "newer entity constructors cannot accept null");
            assertTrue(calls.contains("net/minecraft/world/phys/shapes/CollisionContext.empty()Lnet/minecraft/world/phys/shapes/CollisionContext;"));
            assertTrue(calls.contains("net/minecraft/world/level/ClipContext.<init>" + contextConstructor));
            assertFalse(calls.stream().anyMatch(call -> call.contains("Lnet/minecraft/world/entity/Entity;")));
        } else {
            assertEquals(1, nullArguments[0], "1.20.1 converts the null entity to an empty collision context");
            assertTrue(calls.contains("net/minecraft/world/level/ClipContext.<init>" +
                    contextConstructor.replace("Lnet/minecraft/world/phys/shapes/CollisionContext;", "Lnet/minecraft/world/entity/Entity;")));
        }
    }
}
