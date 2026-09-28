package com.aetherianartificer.townstead.persona;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.social.RelationshipService;
import com.aetherianartificer.townstead.story.StoryService;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gifts handed to a Persona. An item one of their {@code gifts} rules names is taken the Persona's
 * way: MCA's hearts, mood and saturation, the rule's relationship changes, and an Ink knot for
 * the reply. The first time a player gives that kind of gift, the rule's {@code first} plays
 * instead. Any other item is left to MCA.
 */
public final class PersonaGifts {
    private static final byte POSITIVE = 16;
    private static final byte NEGATIVE = 15;

    private PersonaGifts() {}

    /** True when the gift was handled here. */
    public static boolean give(ServerPlayer player, VillagerEntityMCA villager, Memories memory) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) return false;
        PersonaInstances.Instance instance = PersonaInstances.get(player.server).of(villager.getUUID());
        PersonaDefinition persona = instance == null ? null : Personas.byId(instance.persona());
        if (persona == null || persona.gifts().isEmpty()) return false;

        Map<String, Object> vars = new HashMap<>();
        for (PersonaRoll.Option option : PersonaService.rolls(persona, player).values()) vars.putAll(option.vars());
        List<PersonaGift> gifts = persona.gifts();
        int index = -1;
        for (int i = 0; i < gifts.size(); i++) {
            if (gifts.get(i).matches(stack, vars)) {
                index = i;
                break;
            }
        }
        if (index < 0) return false;
        PersonaGift gift = gifts.get(index);
        boolean keeps = gift.response() != PersonaGift.Response.DISLIKES;
        if (keeps && !villager.getInventory().canAddItem(stack)) return false;

        boolean first = gift.first() != null && !PersonaBonds.gaveFirst(player, persona.id(), index);
        int satisfaction = gift.satisfaction();
        if (keeps && !first) {
            int given = villager.getRelationships().getGiftSaturation().get(stack);
            Config config = Config.getInstance();
            satisfaction -= (int) (given * config.giftDesaturationFactor
                    * Math.pow(Math.max(satisfaction, 0.0), config.giftDesaturationExponent));
        }
        satisfaction = (int) (satisfaction * Config.getInstance().giftSatisfactionFactor);

        if (keeps) {
            villager.getRelationships().getGiftSaturation().add(stack);
            villager.getInventory().addItem(stack.split(1));
        }
        villager.level().broadcastEntityEvent(villager, keeps ? POSITIVE : NEGATIVE);
        villager.getVillagerBrain().modifyMoodValue((int) (satisfaction * Config.getInstance().giftMoodEffect
                + Config.getInstance().baseGiftMoodEffect * Mth.sign(satisfaction)));
        memory.modHearts(satisfaction);

        Map<String, Float> relationship = first ? gift.first().relationship() : gift.relationship();
        long day = TownsteadCalendar.worldDay(player.server);
        long time = villager.level().getGameTime();
        for (Map.Entry<String, Float> change : relationship.entrySet()) {
            String quality = change.getKey().contains(":") ? change.getKey() : "townstead:" + change.getKey();
            RelationshipService.apply(player.server, villager.getUUID(), player.getUUID(),
                    "gift:" + persona.id() + ":" + villager.getUUID() + ":" + player.getUUID() + ":" + time + ":" + quality,
                    quality, change.getValue(), day, -1, "townstead:gift/" + persona.id());
        }
        if (first) PersonaBonds.markFirst(player, persona.id(), index);

        String knot = first && gift.first().knot() != null ? gift.first().knot() : gift.knot();
        if (knot == null || !StoryService.play(player, villager, knot)) {
            villager.sendChatMessage(player, switch (gift.response()) {
                case LOVES -> "gift.best";
                case LIKES -> "gift.good";
                case DISLIKES -> "gift.fail";
            });
        }
        return true;
    }
}
