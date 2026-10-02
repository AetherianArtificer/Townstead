package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicies;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** The server side of the Wardrobe screen, shared by both loaders' network handlers. */
public final class WardrobeServer {

    private WardrobeServer() {}

    public static int daysPerWeek(MinecraftServer server) {
        CalendarProfile profile = server == null ? null : TownsteadCalendar.activeProfile(server);
        return profile == null || profile.daysPerWeek() <= 0 ? 7 : profile.daysPerWeek();
    }

    /** Today's weekday index, or 0 when the calendar has no answer. */
    public static int today(MinecraftServer server) {
        if (server == null) return 0;
        CalendarDate today = TownsteadCalendar.today(server);
        if (today == null || today == CalendarDate.UNKNOWN) return 0;
        return Math.max(0, today.dayOfWeek());
    }

    /** Applies an assignment. Returns true when the grid changed. A query changes nothing. */
    public static boolean apply(ServerPlayer sender, WardrobeAssignPayload payload) {
        MinecraftServer server = sender == null ? null : sender.getServer();
        if (server == null || payload == null || payload.day() == WardrobeAssignPayload.QUERY) return false;
        String policy = payload.policy() == null ? "" : payload.policy().trim();
        if (!policy.isEmpty() && templateOf(policy) == null) return false;
        WardrobeAssignments assignments = WardrobeAssignments.get(server);
        int days = daysPerWeek(server);
        if (payload.day() == WardrobeAssignPayload.ALL_DAYS) {
            for (int d = 0; d < days; d++) set(assignments, payload.villager(), d, policy);
        } else if (payload.day() >= 0 && payload.day() < days) {
            set(assignments, payload.villager(), payload.day(), policy);
        } else {
            return false;
        }
        return true;
    }

    private static void set(WardrobeAssignments assignments, @Nullable UUID villager, int day, String policy) {
        if (villager == null) assignments.setVillage(day, policy);
        else assignments.setVillager(villager, day, policy);
    }

    /** The template a cell names, or null when the id is unknown or not a template. */
    public static @Nullable WardrobePolicy templateOf(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        ResourceLocation location = com.aetherianartificer.townstead.data.DataPackLang.parseId(id);
        if (location == null) return null;
        WardrobePolicy policy = WardrobePolicies.byId(location);
        return policy != null && policy.scope() == WardrobePolicy.Scope.VILLAGER ? policy : null;
    }

    public static WardrobeSyncPayload snapshot(MinecraftServer server) {
        int days = daysPerWeek(server);
        WardrobeAssignments assignments = WardrobeAssignments.get(server);
        return new WardrobeSyncPayload(WardrobeTemplate.all(), assignments.villageRow(days),
                assignments.villagerRows(days));
    }
}
