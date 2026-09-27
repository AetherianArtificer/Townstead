package com.aetherianartificer.townstead.politics.heraldry;

import com.aetherianartificer.townstead.politics.charter.*;
import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import java.util.*;

/**
 * The emblems a Charter shows: the settlement's and its faction's. Changing one is a clause on the
 * faction's draft; stamping an item with a published emblem is immediate.
 */
public final class HeraldryService {
    private HeraldryService() {}

    public static String settlement(SettlementRef ref) { return "settlement:" + ref.dimension() + "|" + ref.villageId(); }

    public static String faction(ResourceLocation id) { return "faction:" + id; }

    public static List<CharterSnapshotS2CPayload.Heraldry> views(ServerPlayer player, CharterSavedData.Binding binding, String settlementName) {
        var data = PoliticalSavedData.get(player.server);
        var faction = data.faction(binding.faction());
        if (faction == null) return List.of();
        boolean ruler = CharterDrafts.mayDraft(player, faction);
        return List.of(view(player, settlement(binding.settlement()), Component.translatable("charter.townstead.heraldry.settlement", settlementName), ruler),
                view(player, faction(faction.id()), Component.translatable("charter.townstead.heraldry.faction", faction.name()), ruler));
    }

    private static CharterSnapshotS2CPayload.Heraldry view(ServerPlayer player, String actor, Component name, boolean edit) {
        var entry = HeraldrySavedData.get(player.server).get(actor);
        return new CharterSnapshotS2CPayload.Heraldry(actor, CharterSnapshotS2CPayload.Text.of(name), entry.recipe().encode(), entry.revision(), edit);
    }

    /** Stamps the published emblem onto an item the player holds. */
    public static Component handle(ServerPlayer player, CharterSavedData.Binding binding, CharterActionC2SPayload request) {
        var target = views(player, binding, "").stream().filter(v -> v.actor().equals(request.target())).findFirst().orElse(null);
        if (target == null) return message("denied");
        if (!request.operation().equals("stamp")) return message("invalid");
        var entry = HeraldrySavedData.get(player.server).get(target.actor());
        if (entry.revision() == 0 || !EmblemItems.valid(player.serverLevel().registryAccess(), entry.recipe())) return message("unpublished");
        String[] selection = request.argument().split("\\|", 2);
        int slot;
        try { slot = Integer.parseInt(selection[0]); } catch (NumberFormatException error) { return message("item_changed"); }
        // Only main inventory/hotbar and the offhand; never armor, cursor or another container.
        if (selection.length != 2 || slot < 0 || slot > 40 || slot >= 36 && slot != 40) return message("item_changed");
        var held = player.getInventory().getItem(slot);
        if (!EmblemItems.canDecorate(held) || !net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem()).toString().equals(selection[1]))
            return message("item_changed");
        var stamped = EmblemItems.stampedCopy(player.serverLevel().registryAccess(), held, entry.recipe());
        if (held.getCount() == 1) player.getInventory().setItem(slot, stamped);
        else { held.shrink(1); if (!player.addItem(stamped)) player.drop(stamped, false); }
        player.getInventory().setChanged(); player.containerMenu.broadcastChanges(); return message("stamped");
    }

    /** True when {@code actor} is one of the emblems this Charter shows. */
    public static boolean shows(CharterSavedData.Binding binding, String actor) {
        return actor.equals(settlement(binding.settlement())) || actor.equals(faction(binding.faction()));
    }

    /** Publishes a proclaimed emblem; false when it changed since it was drafted. */
    public static boolean publish(ServerPlayer author, String actor, EmblemRecipe recipe, long expected) {
        if (!HeraldrySavedData.get(author.server).publish(actor, recipe, expected, author.getUUID(), author.serverLevel().getGameTime())) return false;
        refreshCloths(author);
        return true;
    }

    private static Component message(String key) { return Component.translatable("charter.townstead.heraldry." + key); }

    public static String clothRecipe(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var binding = CharterSavedData.get(level.getServer()).binding(level.dimension().location(), pos);
        if (binding == null) return "";
        var saved = HeraldrySavedData.get(level.getServer());
        var local = saved.get(settlement(binding.settlement()));
        return (local.revision() > 0 ? local : saved.get(faction(binding.faction()))).recipe().encode();
    }

    private static void refreshCloths(ServerPlayer player) {
        for (var binding : CharterSavedData.get(player.server).bindings()) {
            for (ServerLevel level : player.server.getAllLevels()) {
                if (!level.dimension().location().equals(binding.dimension()) || !level.hasChunkAt(binding.lectern())) continue;
                var be = level.getBlockEntity(binding.lectern());
                if (!(be instanceof CharterLecternAccess access)) continue;
                access.townstead$setEmblem(clothRecipe(level, binding.lectern())); be.setChanged();
                level.sendBlockUpdated(binding.lectern(), be.getBlockState(), be.getBlockState(), 3);
            }
        }
    }
}
