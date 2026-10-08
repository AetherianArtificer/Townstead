package com.aetherianartificer.townstead.compat.jade;

import com.aetherianartificer.townstead.politics.definition.FactionKind;
import com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.social.BondKinds;
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

/** The offices someone holds, "Presiding Councilor of Ashford", so an envoy can find who speaks for a town. */
enum OfficeJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.tryParse("townstead:offices");
    private static final String KEY = "townstead_offices";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (!(accessor.getEntity() instanceof LivingEntity entity) || entity.getServer() == null) return;
        PoliticalSavedData politics = PoliticalSavedData.get(entity.getServer());
        Party self = Party.person(entity.getUUID());
        java.util.UUID viewer = accessor.getPlayer() == null ? null : accessor.getPlayer().getUUID();
        ListTag list = new ListTag();
        for (BondInstance bond : politics.activeBonds(self)) {
            Party other = bond.other(self);
            Faction faction = other == null ? null : politics.faction(other.faction());
            FactionKind kind = faction == null ? null : PoliticalDefinitions.snapshot().kind(faction.kind());
            if (kind == null || kind.office(bond.kind()) == null) continue;
            var office = BondKinds.byId(bond.kind());
            // A hidden office, such as a Mask of an Umbral Table, shows only to the faction's own members.
            var holderRole = office.roleFor(com.aetherianartificer.townstead.social.BondKind.Party.PERSON);
            if (holderRole != null && holderRole.membersOnly()
                    && (viewer == null || !com.aetherianartificer.townstead.politics.state.FactionBonds.member(
                            politics, viewer, faction.id(), kind.membership().bond()))) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("office", office.displayLangKey());
            // Data-pack offices have no client lang key; the server's resolved name is the fallback.
            entry.putString("office_text", office.displayName().getString());
            entry.putString("faction", faction.name());
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
            String key = entry.getString("office"), text = entry.getString("office_text");
            Component office = key.isEmpty() ? Component.literal(text) : Component.translatableWithFallback(key, text);
            tooltip.add(Component.translatable("townstead.jade.office", office, entry.getString("faction")));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
