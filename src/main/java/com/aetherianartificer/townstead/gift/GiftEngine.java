package com.aetherianartificer.townstead.gift;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.social.RelationshipService;
import com.aetherianartificer.townstead.story.StoryService;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What happens when a gift is taken a particular way: the item is kept, used up or handed back;
 * MCA's hearts, mood and saturation move by the satisfaction, with MCA's own maths; relationship
 * qualities change; an action runs on the receiver with the giver as {@code other} and the gift
 * in focus; and the receiver answers with an Ink knot, a line, or MCA's usual gift line.
 */
public final class GiftEngine {
    private GiftEngine() {}

    public enum Response { LOVES, LIKES, DISLIKES, NEUTRAL }

    /** {@code KEEP} adds the item to the receiver, {@code USE} takes it away, {@code RETURN} leaves it with the giver. */
    public enum Fate { KEEP, USE, RETURN }

    private static final byte POSITIVE = 16;
    private static final byte NEGATIVE = 15;

    public static int defaultSatisfaction(Response response) {
        return switch (response) {
            case LOVES -> 30;
            case LIKES -> 15;
            case DISLIKES -> -15;
            case NEUTRAL -> 0;
        };
    }

    public static Fate defaultFate(Response response) {
        return response == Response.LOVES || response == Response.LIKES ? Fate.KEEP : Fate.RETURN;
    }

    /** How a gift is taken. {@code relationshipSource} names the cause the relationship records. */
    public record Taking(Response response, Fate fate, int satisfaction, boolean desaturate,
                         Map<String, Float> relationship, String relationshipSource,
                         @Nullable Action action, @Nullable String knot, @Nullable String line,
                         Map<String, Object> vars) {}

    /** False when the gift cannot be taken this way (the receiver has no room for it). */
    public static boolean apply(ServerPlayer player, VillagerEntityMCA villager, Memories memory,
                                ItemStack stack, Taking taking) {
        boolean keeps = taking.fate() == Fate.KEEP;
        if (keeps && !villager.getInventory().canAddItem(stack)) return false;
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());

        Config config = Config.getInstance();
        int satisfaction = taking.satisfaction();
        if (keeps && taking.desaturate()) {
            int given = villager.getRelationships().getGiftSaturation().get(stack);
            satisfaction -= (int) (given * config.giftDesaturationFactor
                    * Math.pow(Math.max(satisfaction, 0.0), config.giftDesaturationExponent));
        }
        satisfaction = (int) (satisfaction * config.giftSatisfactionFactor);

        ItemStack gift;
        if (keeps) {
            villager.getRelationships().getGiftSaturation().add(stack);
            gift = stack.split(1);
            villager.getInventory().addItem(gift);
        } else if (taking.fate() == Fate.USE) {
            gift = stack.copyWithCount(1);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        } else {
            gift = stack;
        }

        if (taking.response() != Response.NEUTRAL) {
            villager.level().broadcastEntityEvent(villager, taking.response() == Response.DISLIKES ? NEGATIVE : POSITIVE);
        }
        if (satisfaction != 0) {
            villager.getVillagerBrain().modifyMoodValue((int) (satisfaction * config.giftMoodEffect
                    + config.baseGiftMoodEffect * Mth.sign(satisfaction)));
            memory.modHearts(satisfaction);
        }

        long day = TownsteadCalendar.worldDay(player.server);
        long time = villager.level().getGameTime();
        for (Map.Entry<String, Float> change : taking.relationship().entrySet()) {
            String quality = change.getKey().contains(":") ? change.getKey() : "townstead:" + change.getKey();
            RelationshipService.apply(player.server, villager.getUUID(), player.getUUID(),
                    "gift:" + taking.relationshipSource() + ":" + villager.getUUID() + ":" + player.getUUID()
                            + ":" + time + ":" + quality,
                    quality, change.getValue(), day, -1, taking.relationshipSource());
        }

        if (taking.action() != null) taking.action().run(new ActionContext(villager, player).withItem(gift));

        Map<String, Object> vars = new HashMap<>(taking.vars());
        vars.put("gift_item", com.aetherianartificer.townstead.story.StoryText.item(itemId));
        if (taking.knot() != null && StoryService.play(player, villager, taking.knot(), vars)) return true;
        String line = taking.line() != null ? taking.line() : switch (taking.response()) {
            case LOVES -> "gift.best";
            case LIKES -> "gift.good";
            case DISLIKES -> "gift.fail";
            case NEUTRAL -> null;
        };
        if (line != null) villager.sendChatMessage(player, line);
        return true;
    }
}
