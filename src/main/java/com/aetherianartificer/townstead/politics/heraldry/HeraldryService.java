package com.aetherianartificer.townstead.politics.heraldry;

import com.aetherianartificer.townstead.politics.charter.*;
import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import java.util.*;

public final class HeraldryService {
    private HeraldryService() {}
    public static String settlement(SettlementRef ref) { return "settlement:" + ref.dimension() + "|" + ref.villageId(); }
    public static String polity(ResourceLocation id) { return "polity:" + id; }
    public static String organization(ResourceLocation id) { return "organization:" + id; }
    private static ResourceLocation capability(String name) { return ResourceLocation.tryParse("townstead:" + name); }
    public static List<CharterSnapshotS2CPayload.Heraldry> views(ServerPlayer player, CharterSavedData.Binding binding, String settlementName) {
        var data = PoliticalSavedData.get(player.server); var polity = data.polity(binding.polity());
        if (polity == null) return List.of();
        boolean ruler = CharterIdentityService.mayManage(player, binding);
        var out = new ArrayList<CharterSnapshotS2CPayload.Heraldry>();
        out.add(view(player, settlement(binding.settlement()), Component.translatable("charter.townstead.heraldry.settlement", settlementName), ruler));
        out.add(view(player, polity(polity.id()), Component.translatable("charter.townstead.heraldry.polity", polity.name()), ruler));
        for (var org : data.organizations()) {
            if (org.status() != PoliticalStatus.Organization.ACTIVE || data.supersededGovernment(org.id())) continue;
            if (!org.id().equals(polity.governmentOrganization()) && !polity.settlements().contains(org.home())) continue;
            boolean edit = PoliticalAuthority.mayAct(data, player.getUUID(), org.actor(), capability("edit_heraldry")).allowed();
            out.add(view(player, organization(org.id()), Component.translatable("charter.townstead.heraldry.organization", org.name()), edit));
        }
        return List.copyOf(out);
    }
    private static CharterSnapshotS2CPayload.Heraldry view(ServerPlayer player, String actor, Component name, boolean edit) {
        var entry = HeraldrySavedData.get(player.server).get(actor);
        return new CharterSnapshotS2CPayload.Heraldry(actor, CharterSnapshotS2CPayload.Text.of(name), entry.recipe().encode(), entry.revision(), edit);
    }
    public static Component handle(ServerPlayer player, CharterSavedData.Binding binding, CharterActionC2SPayload request) {
        var target = views(player, binding, "").stream().filter(v -> v.actor().equals(request.target())).findFirst().orElse(null);
        if (target == null) return message("denied");
        if (target.revision() != request.revision()) return message("stale");
        var saved = HeraldrySavedData.get(player.server);
        if (request.operation().equals("publish")) {
            if (!target.editable()) return message("denied");
            EmblemRecipe recipe;
            try { recipe = EmblemRecipe.decode(request.argument()); } catch (RuntimeException error) { return message("invalid"); }
            if (!EmblemItems.valid(player.serverLevel().registryAccess(), recipe)) return message("invalid");
            if (!saved.publish(target.actor(), recipe, request.revision(), player.getUUID(), player.serverLevel().getGameTime())) return message("stale");
            refreshCloths(player);
            return message("published");
        }
        if (!request.operation().equals("stamp")) return message("invalid");
        var entry = saved.get(target.actor());
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
    private static Component message(String key) { return Component.translatable("charter.townstead.heraldry." + key); }
    public static String clothRecipe(ServerLevel level, net.minecraft.core.BlockPos pos) {
        var binding = CharterSavedData.get(level.getServer()).binding(level.dimension().location(), pos);
        if (binding == null) return "";
        var saved = HeraldrySavedData.get(level.getServer());
        var local = saved.get(settlement(binding.settlement()));
        return (local.revision() > 0 ? local : saved.get(polity(binding.polity()))).recipe().encode();
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
