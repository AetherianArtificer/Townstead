package com.aetherianartificer.townstead.compat.jade;

import com.aetherianartificer.townstead.pheno.state.EntityStateDefinition;
import com.aetherianartificer.townstead.pheno.state.EntityStates;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * One line per aspect the looked-at entity carries: "Vampire · Level 6", or its tier. States live
 * on the server, so they travel with Jade's own request for the entity under the crosshair.
 */
enum AspectJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.tryParse("townstead:aspects");
    private static final String KEY = "townstead_aspects";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof LivingEntity entity)) return;
        ListTag list = new ListTag();
        for (EntityStateDefinition definition : EntityStates.definitions().values()) {
            EntityStateDefinition.Aspect aspect = definition.aspect();
            if (aspect == null || !aspect.display()) continue;
            EntityStates.Resolved state = EntityStates.resolve(entity, definition.id());
            if (!state.active()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("id", definition.id().toString());
            if (state.tier() != null) entry.putString("tier", state.tier());
            if (aspect.level() && state.amount() >= 1) entry.putInt("level", (int) Math.floor(state.amount()));
            list.add(entry);
        }
        if (!list.isEmpty()) data.put(KEY, list);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(KEY, Tag.TAG_LIST)) return;
        ListTag list = data.getList(KEY, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation id = ResourceLocation.tryParse(entry.getString("id"));
            if (id == null) continue;
            String base = "state." + id.getNamespace() + "." + id.getPath();
            Component name = Component.translatable(base);
            if (entry.contains("level")) {
                tooltip.add(Component.translatable("townstead.jade.aspect.level", name, entry.getInt("level")));
            } else if (entry.contains("tier")) {
                tooltip.add(Component.translatable("townstead.jade.aspect.tier", name,
                        Component.translatable(base + ".tier." + entry.getString("tier"))));
            } else {
                tooltip.add(name);
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
