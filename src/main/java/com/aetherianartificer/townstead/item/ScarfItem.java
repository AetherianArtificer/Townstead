package com.aetherianartificer.townstead.item;

import com.aetherianartificer.townstead.root.EntityGroups;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A wearable scarf. Equips to the head slot ({@link Equipable}) on both versions, and renders a worn 3D
 * model via {@code WornItemLayer} ({@link Wearable}) while the flat dyeable icon shows in inventory slots.
 * Greyscale + dyeable, tinted by {@link ScarfColor}. On 1.20.1 it also implements {@code DyeableLeatherItem}
 * (dye-craft + cauldron wash); on 1.21.1 dyeing rides the {@code #minecraft:dyeable} tag + {@code DYED_COLOR}
 * component, so no interface is needed.
 */
public class ScarfItem extends Item implements Equipable, Wearable
        //? if <1.21 {
        /*, net.minecraft.world.item.DyeableLeatherItem
        *///?}
{
    private static final ResourceLocation GEO = rl("geo/scarf.geo.json");

    /** Whether the loaded content has spider-folk at all; without them the scarf has no wearer. */
    public static boolean anyoneCanWear() {
        return com.aetherianartificer.townstead.root.PresentEntityGroups.present(
                com.aetherianartificer.townstead.root.gene.types.EntityGroupGeneType.Group.ARTHROPOD);
    }
    private static final ResourceLocation TEXTURE = rl("textures/entity/scarf.png");

    private static ResourceLocation rl(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("townstead", path);
        //?} else {
        /*return new ResourceLocation("townstead", path);
        *///?}
    }
    // Default seat on a humanoid neck (offset px, rotation deg): the geo is authored above a spider's neck,
    // so without this it rides over the head; +Y drops the band to the neck, +Z pulls it back off the face.
    // Rigs add their own delta via wearables.head.items."townstead:scarf" instead of using this.
    private static final float[][] HUMANOID_SEAT = {{0f, 10f, 1f}, {0f, 0f, 0f}};

    public ScarfItem(Properties properties) {
        super(properties);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    // Item.use() would otherwise shadow Equipable's default, so right-click wouldn't equip (as ArmorItem does).
    // Only spider-folk may wear a scarf; a non-arthropod is refused (server-authoritative) with a message.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // The group lives server-side; let the client predict success and the server decide authoritatively.
        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        if (!EntityGroups.isArthropod(player)) {
            player.displayClientMessage(Component.translatable(ScarfEquip.DENY_KEY), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return this.swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public ResourceLocation wornGeo() {
        return GEO;
    }

    @Override
    public ResourceLocation wornTexture() {
        return TEXTURE;
    }

    @Override
    public int wornColor(ItemStack stack) {
        return ScarfColor.get(stack);
    }

    @Override
    public String anchorChannel() {
        return "head";
    }

    @Override
    public float[][] humanoidSeat() {
        return HUMANOID_SEAT;
    }
}
