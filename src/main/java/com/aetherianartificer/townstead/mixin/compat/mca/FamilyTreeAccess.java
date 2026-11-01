package com.aetherianartificer.townstead.mixin.compat.mca;

import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.UUID;

@Mixin(value = FamilyTree.class, remap = false)
public interface FamilyTreeAccess {
    @Invoker("<init>")
    static FamilyTree townstead$create(ServerLevel level) {
        throw new AssertionError();
    }

    @Accessor("entries")
    Map<UUID, FamilyTreeNode> townstead$entries();
}
