package com.aetherianartificer.townstead.compat.mca;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Fail when an MCA update moves armor writes outside the paths our assignment mixin protects. */
class EquipmentTaskContractTest {
    @Test void guardDutyAndArmorToggleUseProtectedWritePaths() throws Exception {
        var type = new ClassNode();
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "net/conczin/mca/entity/ai/brain/tasks/EquipmentTask.class")) {
            assertNotNull(stream);
            new ClassReader(stream).accept(type, 0);
        }
        Set<String> writers = new HashSet<>();
        int clears = 0, selections = 0;
        for (var method : type.methods) {
            boolean armorSlot = false, writes = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof FieldInsnNode field && field.owner.endsWith("/EquipmentSlot")
                        && Set.of("HEAD", "CHEST", "LEGS", "FEET").contains(field.name)) armorSlot = true;
                if (instruction instanceof MethodInsnNode call && call.name.equals("setItemSlot")) {
                    assertEquals("net/conczin/mca/entity/VillagerEntityMCA", call.owner);
                    writes = true;
                    if (method.name.equals("start")) clears++;
                    if (method.name.equals("equipBestArmor")) selections++;
                }
            }
            if (writes && (armorSlot || method.desc.contains("EquipmentSlot;"))) writers.add(method.name);
        }
        assertEquals(Set.of("start", "equipBestArmor"), writers);
        assertTrue(clears >= 4, "Duty end must expose the four armor clear calls to the redirect");
        assertEquals(1, selections, "Armor selection must expose its write to the redirect");
        assertTrue(type.fields.stream().anyMatch(f -> f.name.equals("condition")
                && f.desc.equals("Ljava/util/function/Predicate;")));
    }
}
