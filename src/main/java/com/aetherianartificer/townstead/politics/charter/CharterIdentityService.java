package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Naming changes the public faction label, never its settlement, stable ID, or government. */
public final class CharterIdentityService {
    private CharterIdentityService() {}
    public static boolean mayManage(ServerPlayer player, CharterSavedData.Binding binding) {
        var data = PoliticalSavedData.get(player.server); var polity = data.polity(binding.polity());
        if (polity == null) return false;
        var civic = CivicProviders.read(player, binding.settlement());
        return civic != null && civic.controlsGovernment() ? civic.mayManage()
                : PoliticalAuthority.mayAct(data, player.getUUID(), polity.actor(), ResourceLocation.tryParse("townstead:govern_polity")).allowed();
    }
    public static Component handle(ServerPlayer player, CharterSavedData.Binding binding, CharterActionC2SPayload request) {
        if (!request.operation().equals("rename_polity") || !request.target().equals("polity:" + binding.polity()) || !mayManage(player, binding))
            return message("denied");
        var data = PoliticalSavedData.get(player.server);
        if (FactionLifecycle.leader(data, data.polity(binding.polity()), player.getUUID()))
            return FactionLifecycle.prepare(player, binding, request.operation(), request.argument(), request.name());
        return message(rename(data, binding.polity(), request.argument(), request.name()));
    }
    /** Compare the name reviewed by the player; unrelated record updates need not invalidate naming. */
    public static String rename(PoliticalSavedData data, ResourceLocation id, String expected, String requested) {
        var polity = data.polity(id);
        if (polity == null) return "unavailable";
        if (!polity.name().equals(expected)) return "stale";
        String name = normalize(requested);
        if (name == null) return "invalid";
        data.putPolity(new PolityInstance(polity.id(), name, polity.color(), polity.emblem(), polity.createdAt(), polity.provenance(),
                polity.status(), polity.settlements(), polity.governmentOrganization()));
        data.putFactionName(id, com.aetherianartificer.townstead.culture.FactionNaming.Name.custom(name));
        return "saved";
    }
    public static String normalize(String raw) {
        if (raw == null || raw.length() > 48) return null;
        for (int i = 0; i < raw.length(); i++) if (Character.isISOControl(raw.charAt(i)) || raw.charAt(i) == '\u00a7') return null;
        String name = raw.strip().replaceAll("\\s+", " ");
        return name.length() < 2 ? null : name;
    }
    private static Component message(String key) { return Component.translatable("charter.townstead.identity." + key); }
}
