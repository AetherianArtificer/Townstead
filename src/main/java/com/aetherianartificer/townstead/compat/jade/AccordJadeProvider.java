package com.aetherianartificer.townstead.compat.jade;

import com.aetherianartificer.townstead.item.AccordLetterItem;
import com.aetherianartificer.townstead.politics.charter.AccordAcceptance;
import com.aetherianartificer.townstead.politics.charter.CharterAccords;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Looking at whoever speaks for a faction while holding their accord letter shows whether they
 * would accept it, and why, before it is handed over.
 */
enum AccordJadeProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = ResourceLocation.tryParse("townstead:accord_acceptance");
    private static final String KEY = "townstead_accord";

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        Player player = accessor.getPlayer();
        if (!(accessor.getEntity() instanceof LivingEntity entity) || player == null || entity.getServer() == null) return;
        AccordLetterItem.Letter letter = AccordLetterItem.read(player.getMainHandItem());
        if (letter == null) return;
        PoliticalSavedData politics = PoliticalSavedData.get(entity.getServer());
        Faction recipient = politics.faction(letter.recipient());
        Faction proposer = politics.faction(letter.proposer());
        if (recipient == null || proposer == null
                || !PoliticalAuthority.allowed(politics, entity.getUUID(), recipient.id(), CharterAccords.REPRESENT)) return;
        AccordAcceptance.Result result = AccordAcceptance.evaluate(entity.getServer(), player.getUUID(), player,
                proposer, recipient, entity.getUUID(), entity);
        CompoundTag tag = new CompoundTag();
        tag.putInt("total", result.total());
        ListTag lines = new ListTag();
        for (AccordAcceptance.Line line : result.lines()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("key", line.key());
            entry.putInt("value", line.value());
            lines.add(entry);
        }
        tag.put("lines", lines);
        data.put(KEY, tag);
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag tag = data.getCompound(KEY);
        int total = tag.getInt("total");
        tooltip.add(Component.translatable(total > 0 ? "townstead.jade.accord.accept" : "townstead.jade.accord.refuse",
                CharterAccords.signed(total)).withStyle(total > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        ListTag lines = tag.getList("lines", Tag.TAG_COMPOUND);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag line = lines.getCompound(i);
            tooltip.add(CharterAccords.describe(new AccordAcceptance.Line(line.getString("key"), line.getInt("value")))
                    .copy().withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
