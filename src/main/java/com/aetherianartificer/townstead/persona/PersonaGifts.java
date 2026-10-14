package com.aetherianartificer.townstead.persona;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.minecraft.server.level.ServerPlayer;
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
        net.minecraft.resources.ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        String firstKey = gift.first() != null && gift.first().perItem() ? index + "#" + itemId : String.valueOf(index);
        boolean first = gift.first() != null && !PersonaBonds.gaveFirst(player, persona.id(), firstKey);
        com.aetherianartificer.townstead.gift.GiftEngine.Response response = switch (gift.response()) {
            case LOVES -> com.aetherianartificer.townstead.gift.GiftEngine.Response.LOVES;
            case LIKES -> com.aetherianartificer.townstead.gift.GiftEngine.Response.LIKES;
            case DISLIKES -> com.aetherianartificer.townstead.gift.GiftEngine.Response.DISLIKES;
        };
        var taking = new com.aetherianartificer.townstead.gift.GiftEngine.Taking(response,
                com.aetherianartificer.townstead.gift.GiftEngine.defaultFate(response), gift.satisfaction(), !first,
                first ? gift.first().relationship() : gift.relationship(), "townstead:gift/" + persona.id(), null,
                first && gift.first().knot() != null ? gift.first().knot() : gift.knot(), null, Map.of());
        if (!com.aetherianartificer.townstead.gift.GiftEngine.apply(player, villager, memory, stack, taking)) return false;
        if (first) PersonaBonds.markFirst(player, persona.id(), firstKey);
        return true;
    }
}
