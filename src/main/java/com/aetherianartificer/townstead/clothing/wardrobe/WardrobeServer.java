package com.aetherianartificer.townstead.clothing.wardrobe;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.clothing.policy.SkinPicker;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicies;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.tick.WardrobeVillagerTicker;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The server side of the Wardrobe screen, shared by both loaders' network handlers. */
public final class WardrobeServer {

    /** The residents each player's screen last asked about, so an edit can answer with the same rows. */
    private static final Map<UUID, List<UUID>> WATCHED = new ConcurrentHashMap<>();

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

    /** Applies an edit and returns the sync to answer with. */
    public static WardrobeSyncPayload handle(ServerPlayer sender, WardrobeAssignPayload payload) {
        MinecraftServer server = sender.getServer();
        if (payload.op() == WardrobeAssignPayload.Op.QUERY) {
            WATCHED.put(sender.getUUID(), List.copyOf(payload.targets()));
        } else {
            apply(server, payload);
            List<UUID> touched = payload.op() == WardrobeAssignPayload.Op.PASTE ? payload.targets()
                    : payload.villager() == null ? WATCHED.getOrDefault(sender.getUUID(), List.of())
                    : List.of(payload.villager());
            for (UUID uuid : touched) {
                VillagerEntityMCA villager = find(server, uuid);
                if (villager != null) WardrobeVillagerTicker.refresh(villager);
            }
        }
        return snapshot(server, WATCHED.getOrDefault(sender.getUUID(), List.of()));
    }

    static void apply(MinecraftServer server, WardrobeAssignPayload payload) {
        if (server == null) return;
        WardrobeAssignments assignments = WardrobeAssignments.get(server);
        int days = daysPerWeek(server);
        UUID villager = payload.villager();
        String value = payload.value().trim();
        switch (payload.op()) {
            case CELL -> {
                if (villager == null || !value.isEmpty() && !wearable(server, villager, value)) return;
                if (payload.day() == WardrobeAssignPayload.ALL_DAYS) {
                    for (int d = 0; d < days; d++) assignments.setVillager(villager, d, value);
                } else if (payload.day() >= 0 && payload.day() < days) {
                    assignments.setVillager(villager, payload.day(), value);
                } else {
                    return;
                }
                if (!value.isEmpty()) assignments.countPick(villager, value);
            }
            case WORK -> {
                if (villager == null || !value.isEmpty() && !wearable(server, villager, value)) return;
                VillagerEntityMCA entity = find(server, villager);
                assignments.setWork(villager, value, entity == null ? "" : professionOf(entity));
            }
            case WEATHER -> {
                byte state;
                try {
                    state = Byte.parseByte(value);
                } catch (NumberFormatException e) {
                    return;
                }
                boolean warmLayer = payload.day() == 0;
                if (villager == null) {
                    boolean on = state != WardrobeAssignments.OFF;
                    assignments.setVillageWeather(warmLayer ? on : assignments.villageWarm(),
                            warmLayer ? assignments.villageLight() : on);
                } else if (state >= WardrobeAssignments.INHERIT && state <= WardrobeAssignments.OFF) {
                    assignments.setWeather(villager, warmLayer, state);
                }
            }
            case STAR -> {
                if (villager == null || value.isEmpty()) return;
                assignments.setStarred(villager, value, payload.day() == 1);
            }
            case PASTE -> {
                List<String> row = payload.row();
                for (String cell : row) {
                    if (!cell.isEmpty() && !skinExists(cell) && templateOf(cell) == null) return;
                }
                for (UUID target : payload.targets()) {
                    for (int d = 0; d < days; d++) {
                        assignments.setVillager(target, d, d < row.size() ? row.get(d) : "");
                    }
                }
            }
            default -> {
            }
        }
    }

    /** A known skin that fits the villager's body, when the villager is loaded to check. */
    private static boolean wearable(MinecraftServer server, UUID uuid, String skin) {
        if (!skinExists(skin)) return false;
        VillagerEntityMCA villager = find(server, uuid);
        return villager == null || SkinPicker.fitsBody(villager, skin);
    }

    /** Whether MCA knows this clothing skin, including skins added through its Skin Library. */
    public static boolean skinExists(@Nullable String skin) {
        if (skin == null || skin.isEmpty()) return false;
        ClothingList list = ClothingList.getInstance();
        if (list != null && list.clothing.containsKey(skin)) return true;
        return CustomClothingManager.getClothing().getEntries().containsKey(skin);
    }

    /** The template a cell names, or null when the id is unknown or not a template. */
    public static @Nullable WardrobePolicy templateOf(@Nullable String id) {
        if (id == null || id.isEmpty() || !id.contains(":") || id.endsWith(".png")) return null;
        ResourceLocation location = com.aetherianartificer.townstead.data.DataPackLang.parseId(id);
        if (location == null) return null;
        WardrobePolicy policy = WardrobePolicies.byId(location);
        return policy != null && policy.scope() == WardrobePolicy.Scope.VILLAGER ? policy : null;
    }

    /**
     * The picked work skin, or empty. A pick made under another profession is dropped, so a
     * profession change brings that profession's own clothes.
     */
    public static String workSkin(MinecraftServer server, VillagerEntityMCA villager) {
        WardrobeAssignments assignments = WardrobeAssignments.get(server);
        WardrobeAssignments.Entry entry = assignments.entry(villager.getUUID());
        if (entry == null || entry.work().isEmpty()) return "";
        if (!entry.workProfession().equals(professionOf(villager))) {
            assignments.setWork(villager.getUUID(), "", "");
            return "";
        }
        return entry.work();
    }

    public static String professionOf(VillagerEntityMCA villager) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession());
        return key == null ? "" : key.toString();
    }

    public static WardrobeSyncPayload snapshot(MinecraftServer server, List<UUID> residents) {
        int days = daysPerWeek(server);
        WardrobeAssignments assignments = WardrobeAssignments.get(server);
        Map<UUID, WardrobeSyncPayload.Resident> out = new LinkedHashMap<>();
        for (UUID uuid : residents) {
            VillagerEntityMCA villager = find(server, uuid);
            if (villager != null) workSkin(server, villager);
            WardrobeAssignments.Entry entry = assignments.entry(uuid);
            out.put(uuid, new WardrobeSyncPayload.Resident(
                    assignments.row(uuid, days),
                    entry == null ? "" : entry.work(),
                    entry == null ? WardrobeAssignments.INHERIT : entry.warm(),
                    entry == null ? WardrobeAssignments.INHERIT : entry.light(),
                    entry == null ? List.of() : new ArrayList<>(entry.starred()),
                    entry == null ? Map.of() : new LinkedHashMap<>(entry.picks()),
                    villager == null ? 0 : villager.getGenetics().getGender().ordinal(),
                    villager == null ? "" : TownsteadVillagers.get(villager).life().rootId(),
                    villager != null && villager.isClothingLocked(),
                    villager == null ? "" : household(villager),
                    villager == null ? "" : worksite(villager),
                    villager == null ? "" : villager.getClothes()));
        }
        return new WardrobeSyncPayload(WardrobeTemplate.all(), assignments.villageWarm(), assignments.villageLight(), out);
    }

    private static @Nullable VillagerEntityMCA find(MinecraftServer server, UUID uuid) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(uuid);
            if (entity instanceof VillagerEntityMCA villager) return villager;
        }
        return null;
    }

    private static String household(VillagerEntityMCA villager) {
        Optional<GlobalPos> home = villager.getResidency().getHome();
        return home.map(pos -> placeKey(villager, pos)).orElse("");
    }

    private static String worksite(VillagerEntityMCA villager) {
        return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(pos -> placeKey(villager, pos)).orElse("");
    }

    /** The building around a position when MCA knows one, else the position itself. */
    private static String placeKey(VillagerEntityMCA villager, GlobalPos pos) {
        if (villager.getServer() != null) {
            ServerLevel level = villager.getServer().getLevel(pos.dimension());
            Building building = level == null ? null : McaBuildingCompat.buildingAt(level, pos.pos());
            if (building != null) return pos.dimension().location() + "#" + building.getId();
        }
        BlockPos block = pos.pos();
        return pos.dimension().location() + "@" + block.getX() + "," + block.getY() + "," + block.getZ();
    }
}
