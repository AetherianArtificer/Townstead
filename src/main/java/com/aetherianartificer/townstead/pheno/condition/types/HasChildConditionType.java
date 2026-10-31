package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.UUID;

/**
 * True when the entity has a living child in MCA's family tree. With {@code young} (the default),
 * only a child who is not grown up yet counts, and only one loaded in the world, since that is
 * where their age can be read.
 * <pre>{ "type": "pheno:has_child" }</pre>
 * <pre>{ "type": "pheno:has_child", "young": false }</pre>
 */
public final class HasChildConditionType implements ConditionType {
    public static final String KEY = "pheno:has_child";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        boolean young = GsonHelper.getAsBoolean(json, "young", true);
        return ctx -> {
            if (ctx.entity() == null || !(ctx.entity().level() instanceof ServerLevel level)) return false;
            FamilyTree tree = FamilyTree.get(level);
            FamilyTreeNode node = tree.getOrEmpty(ctx.entity().getUUID()).orElse(null);
            if (node == null) return false;
            for (UUID id : node.children()) {
                FamilyTreeNode child = tree.getOrEmpty(id).orElse(null);
                if (child == null || child.isDeceased()) continue;
                if (!young) return true;
                if (find(level, id) instanceof LivingEntity living && living.isAlive() && living.isBaby()) return true;
            }
            return false;
        };
    }

    private static Entity find(ServerLevel level, UUID id) {
        for (ServerLevel world : level.getServer().getAllLevels()) {
            Entity entity = world.getEntity(id);
            if (entity != null) return entity;
        }
        return null;
    }
}
