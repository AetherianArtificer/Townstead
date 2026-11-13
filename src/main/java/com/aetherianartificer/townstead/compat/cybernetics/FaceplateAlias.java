package com.aetherianartificer.townstead.compat.cybernetics;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Cybernetics lets a player wear a faceplate mask named in an anvil, and go by that name. The mask
 * sits in their synced cyberware data, so both sides can see it. Read by reflection: no build
 * dependency, and nothing happens without the mod.
 */
public final class FaceplateAlias {

    //? if neoforge {
    private static final net.minecraft.resources.ResourceLocation ATTACHMENT =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createcybernetics", "cyberware");
    private static volatile boolean resolved;
    private static net.neoforged.neoforge.attachment.AttachmentType<Object> type;
    private static java.lang.reflect.Method maskStack;
    //?}

    private FaceplateAlias() {}

    /** The alias this player wears behind a faceplate, or null when they wear none. */
    @Nullable
    public static Component worn(Player player) {
        Component custom = player.getCustomName();
        if (custom == null) return null;
        //? if neoforge {
        net.minecraft.world.item.ItemStack mask = mask(player);
        if (mask == null || mask.isEmpty()) return null;
        Component named = mask.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        return named != null && named.getString().trim().equals(custom.getString()) ? custom : null;
        //?} else {
        /*return null;
        *///?}
    }

    //? if neoforge {
    @Nullable
    private static net.minecraft.world.item.ItemStack mask(Player player) {
        if (!resolve() || !player.hasData(type)) return null;
        try {
            return (net.minecraft.world.item.ItemStack) maskStack.invoke(player.getData(type));
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean resolve() {
        if (resolved) return type != null;
        synchronized (FaceplateAlias.class) {
            if (resolved) return type != null;
            try {
                var found = net.neoforged.neoforge.registries.NeoForgeRegistries.ATTACHMENT_TYPES.get(ATTACHMENT);
                if (found != null) {
                    maskStack = Class.forName("com.perigrine3.createcybernetics.common.capabilities.PlayerCyberwareData")
                            .getMethod("getFaceplateMaskStack");
                    type = (net.neoforged.neoforge.attachment.AttachmentType<Object>) found;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                type = null;
            }
            resolved = true;
            return type != null;
        }
    }
    //?}
}
