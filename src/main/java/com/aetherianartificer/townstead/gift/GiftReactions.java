package com.aetherianartificer.townstead.gift;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The gift reactions in {@code data/<ns>/gift_reaction/}, and the one entry point for every gift a
 * player gives a villager: a Persona's own {@code gifts} rules first, then these reactions by
 * {@code priority}, highest first. A gift none of them takes stays MCA's.
 */
public final class GiftReactions extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/GiftReactions");
    private static final String FIRSTS = "townstead:gift_reaction_firsts";
    private static volatile List<GiftReaction> reactions = List.of();

    public GiftReactions() {
        super(new Gson(), "gift_reaction");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager manager, ProfilerFiller profiler) {
        // Gate on "mods", apply overrides and patches, as every Townstead data kind does.
        entries = com.aetherianartificer.townstead.data.DataDocuments.resolve("gift_reaction", entries, manager);
        List<GiftReaction> loaded = new ArrayList<>();
        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            try {
                var json = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                com.aetherianartificer.townstead.data.TownsteadSchema.validate(json, GiftReaction.SCHEMA);
                loaded.add(GiftReaction.parse(entry.getKey(), json));
            } catch (Exception exception) {
                LOGGER.warn("Could not load gift reaction {}: {}", entry.getKey(), exception.getMessage());
            }
        }
        loaded.sort(Comparator.comparingInt(GiftReaction::priority).reversed()
                .thenComparing(reaction -> reaction.id().toString()));
        reactions = List.copyOf(loaded);
        LOGGER.info("Loaded {} gift reaction(s)", loaded.size());
    }

    public static List<GiftReaction> all() {
        return reactions;
    }

    /** True when the gift was taken here, by a Persona's rule or by a reaction. */
    public static boolean give(ServerPlayer player, VillagerEntityMCA villager, Memories memory) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;
        if (com.aetherianartificer.townstead.persona.PersonaGifts.give(player, villager, memory)) return true;
        if (reactions.isEmpty()) return false;
        ConditionContext ctx = new ConditionContext(villager, player).withItem(stack);
        for (GiftReaction reaction : reactions) {
            if (!GiftItems.matches(reaction.items(), stack, Map.of())) continue;
            if (reaction.condition() != null && !reaction.condition().test(ctx)) continue;
            if (take(player, villager, memory, stack, reaction)) return true;
        }
        return false;
    }

    private static boolean take(ServerPlayer player, VillagerEntityMCA villager, Memories memory,
                                ItemStack stack, GiftReaction reaction) {
        GiftReaction.First first = reaction.first();
        String firstKey = null;
        if (first != null) {
            firstKey = reaction.id() + "|" + villager.getUUID()
                    + (first.perItem() ? "|" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()) : "");
            if (gaveBefore(player, firstKey)) firstKey = null;
        }
        boolean isFirst = firstKey != null;
        String source = reaction.id().getNamespace() + ":gift/" + reaction.id().getPath();
        GiftEngine.Taking taking = new GiftEngine.Taking(reaction.response(), reaction.fate(), reaction.satisfaction(),
                !isFirst, isFirst ? first.relationship() : reaction.relationship(), source, reaction.action(),
                isFirst && first.knot() != null ? first.knot() : reaction.knot(),
                isFirst && first.line() != null ? first.line() : reaction.line(), Map.of());
        if (!GiftEngine.apply(player, villager, memory, stack, taking)) return false;
        if (isFirst) markGiven(player, firstKey);
        return true;
    }

    private static boolean gaveBefore(ServerPlayer player, String key) {
        ListTag list = player.getPersistentData().getList(FIRSTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (key.equals(list.getString(i))) return true;
        return false;
    }

    private static void markGiven(ServerPlayer player, String key) {
        ListTag list = player.getPersistentData().getList(FIRSTS, Tag.TAG_STRING);
        list.add(StringTag.valueOf(key));
        player.getPersistentData().put(FIRSTS, list);
    }
}
