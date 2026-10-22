package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.assign.Assignable;
import com.aetherianartificer.townstead.assign.AssignableProvider;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * A hunter's crossbow ammunition on the ability wheel, as Vampirism's ammo wheel offers it: each
 * crossbow arrow, and Any. Pressing one loads it in the crossbow in hand, if that crossbow chooses its
 * arrows. Each shows as on while it is the one loaded. Ids are {@code <ns>:ammo/<item>}.
 */
public final class VampirismAmmoAssignables implements AssignableProvider {
    private static final String PREFIX = "ammo/";
    private static final ResourceLocation ANY = ResourceLocation.tryBuild("vampirism", PREFIX + "any");

    private static volatile boolean probed;
    private static Class<?> crossbowType, arrowType;
    private static Method canSelect, getAmmo, setAmmo;

    @Override
    public void collect(ServerPlayer player, List<Assignable> out) {
        if (!VampirismStateProviders.isHunter(player) || !probe()) return;
        Component source = Component.translatable("townstead.wheel.source.ammo");
        for (Item item : BuiltInRegistries.ITEM) {
            if (!arrowType.isInstance(item)) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            out.add(new Assignable(ResourceLocation.tryBuild(itemId.getNamespace(), PREFIX + itemId.getPath()),
                    item.getDescription(), itemId.toString(), source, Assignable.Kind.ABILITY, 0, 0, "", 0, ""));
        }
        out.add(new Assignable(ANY, Component.translatable("townstead.wheel.ammo.any"), "minecraft:arrow",
                source, Assignable.Kind.ABILITY, 0, 0, "", 0, ""));
    }

    @Override
    public boolean invoke(ServerPlayer player, ResourceLocation id) {
        if (!id.getPath().startsWith(PREFIX) || !probe()) return false;
        Item ammo = ammo(id);
        if (ammo == null && !ANY.equals(id)) return false;
        ItemStack crossbow = crossbow(player);
        if (crossbow == null) {
            player.displayClientMessage(Component.translatable("townstead.wheel.ammo.no_crossbow"), true);
            return true;
        }
        try {
            setAmmo.invoke(crossbow.getItem(), crossbow, ammo);
        } catch (Throwable ignored) {
            // This crossbow would not take it.
        }
        return true;
    }

    @Override
    public @Nullable Live live(ServerPlayer player, ResourceLocation id) {
        if (!id.getPath().startsWith(PREFIX) || !probe()) return null;
        Item ammo = ammo(id);
        if (ammo == null && !ANY.equals(id)) return null;
        ItemStack crossbow = crossbow(player);
        boolean loaded = false;
        if (crossbow != null) {
            try {
                Object current = getAmmo.invoke(crossbow.getItem(), crossbow);
                Item item = current instanceof Optional<?> optional ? (Item) optional.orElse(null) : null;
                loaded = item == ammo;
            } catch (Throwable ignored) {
                // Unknown: shown as not loaded.
            }
        }
        return new Live(true, loaded, loaded ? Long.MAX_VALUE : 0, 0, 0, 0, crossbow != null);
    }

    /** The arrow item an id names, or null for Any or anything that is not a crossbow arrow. */
    private static @Nullable Item ammo(ResourceLocation id) {
        if (ANY.equals(id)) return null;
        ResourceLocation itemId = ResourceLocation.tryBuild(id.getNamespace(), id.getPath().substring(PREFIX.length()));
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) return null;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        return arrowType.isInstance(item) ? item : null;
    }

    /** The crossbow in the player's main hand, when it is one that chooses its arrows. */
    private static @Nullable ItemStack crossbow(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        if (!crossbowType.isInstance(stack.getItem())) return null;
        try {
            return Boolean.TRUE.equals(canSelect.invoke(stack.getItem(), stack)) ? stack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean probe() {
        if (probed) return setAmmo != null;
        synchronized (VampirismAmmoAssignables.class) {
            if (!probed) {
                try {
                    if (ModCompat.isLoaded(VampirismStateProviders.MOD_ID)) {
                        crossbowType = Class.forName("de.teamlapen.vampirism.api.items.IVampirismCrossbow");
                        arrowType = Class.forName("de.teamlapen.vampirism.api.items.IVampirismCrossbowArrow");
                        canSelect = crossbowType.getMethod("canSelectAmmunition", ItemStack.class);
                        getAmmo = crossbowType.getMethod("getAmmunition", ItemStack.class);
                        setAmmo = crossbowType.getMethod("setAmmunition", ItemStack.class, Item.class);
                    }
                } catch (Throwable ignored) {
                    setAmmo = null;
                }
                probed = true;
            }
            return setAmmo != null;
        }
    }
}
