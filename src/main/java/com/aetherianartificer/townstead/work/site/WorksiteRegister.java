package com.aetherianartificer.townstead.work.site;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every registered {@link Worksite}, stored once on the overworld and keyed by binding.
 *
 * <p>Sits beside {@code TownsteadVillageSavedData} rather than inside it. The two hold different
 * kinds of thing and fail differently — that one is a rebuildable overlay of MCA's geometry, this
 * one is player intent that must outlive MCA's model changing — and sharing a file would tie their
 * schemas together for the sake of saving a little structure.</p>
 *
 * <p><strong>Registration is a transition, never a tick.</strong> Callers resolve by
 * {@link #find(WorksiteKey)}, which is a map probe on a key the work task already computed;
 * {@link #register} is only reached from the rare events that create places — a room being typed, a
 * villager being assigned, a station being discovered.</p>
 */
public class WorksiteRegister extends SavedData {

    public static final String FILE_ID = "townstead_worksites";
    public static final int SCHEMA_VERSION = 6;   // 6 separated private access from named people

    private static final String KEY_SCHEMA_VERSION = "schemaVersion";
    private static final String KEY_SITES = "worksites";
    private static final String KEY_NEXT_ID = "nextId";
    private static final String KEY_ID = "id";
    private static final String KEY_BINDING = "binding";
    private static final String KEY_DIM = "dim";
    private static final String KEY_VALUE = "value";
    private static final String KEY_NAME = "name";
    private static final String KEY_NAME_CUSTOM = "nameCustom";
    private static final String KEY_VILLAGE = "village";
    private static final String KEY_CREATED = "created";
    private static final String KEY_LAST_SEEN = "lastSeen";
    private static final String KEY_ORDERS = "orders";
    private static final String KEY_DRIVER = "driver";
    private static final String KEY_OWNERSHIP_TAG = "ownershipTag";
    private static final String KEY_OWNERSHIP_SCOPE = "ownershipScope";
    private static final String KEY_OWNERSHIP_PRIVATE = "ownershipPrivate";
    private static final String KEY_OWNERS = "owners";
    private static final String KEY_OWNER_UUID = "uuid";
    private static final String KEY_OWNER_NAME = "name";
    private static final String KEY_OWNER_KIND = "kind";
    private static final String KEY_LIST_ONLY = "listOnly";
    private static final String KEY_ORDER_OUTPUT = "item";
    private static final String KEY_ORDER_PRODUCT = "product";
    private static final String KEY_ORDER_PRODUCT_NAME = "productName";
    private static final String KEY_ORDER_MODE = "mode";
    private static final String KEY_ORDER_TARGET = "target";
    private static final String KEY_ORDER_SCOPE = "scope";
    private static final String KEY_ORDER_PAUSED = "paused";
    private static final String KEY_ORDER_PROFESSION = "profession";
    private static final String KEY_ORDER_MIN_RANK = "minRank";
    private static final String KEY_ORDER_KIND = "kind";
    private static final String KEY_ORDER_VILLAGER = "villager";
    private static final String KEY_ORDER_PRODUCED = "produced";

    private final Map<WorksiteKey, Worksite> sites = new HashMap<>();

    /** Monotonic and persisted, so a retired site's id is never handed to a different place. */
    private long nextId = 1L;

    private int loadedSchemaVersion = SCHEMA_VERSION;

    public WorksiteRegister() {}

    public static WorksiteRegister get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        //? if >=1.21 {
        return overworld.getDataStorage().computeIfAbsent(
                new Factory<>(WorksiteRegister::new, WorksiteRegister::load),
                FILE_ID);
        //?} else {
        /*return overworld.getDataStorage().computeIfAbsent(
                WorksiteRegister::load,
                WorksiteRegister::new,
                FILE_ID);
        *///?}
    }

    // ── Lookup ──

    /** The record for this key, or null. The per-tick path, so it does nothing but probe. */
    @Nullable
    public Worksite find(@Nullable WorksiteKey key) {
        return key == null ? null : sites.get(key);
    }

    @Nullable
    public Worksite byId(long id) {
        for (Worksite site : sites.values()) {
            if (site.id() == id) return site;
        }
        return null;
    }

    private boolean activityScanValid;
    private boolean anyActivity;

    /**
     * Whether any worksite anywhere holds an activity line.
     *
     * <p>Exists so work tasks can ask "may I?" in their start conditions without paying for
     * it. Those run in brain eligibility, every villager every tick, and resolving a worksite means
     * walking villages and buildings — far too expensive to do speculatively. In the overwhelming
     * case there are no activity lines at all and this answers false immediately.</p>
     */
    public boolean anyActivityOrders() {
        if (!activityScanValid) {
            anyActivity = false;
            for (Worksite site : sites.values()) {
                for (com.aetherianartificer.townstead.work.order.Order order : site.orders().orders()) {
                    if (order.isActivity()) {
                        anyActivity = true;
                        break;
                    }
                }
                if (anyActivity) break;
            }
            activityScanValid = true;
        }
        return anyActivity;
    }

    /** Called whenever an order list changes, so the next ask re-counts. */
    public void invalidateActivityScan() {
        activityScanValid = false;
    }

    public Collection<Worksite> all() {
        return List.copyOf(sites.values());
    }

    public int size() {
        return sites.size();
    }

    // ── Mutation ──

    /**
     * The record for this key, creating it if this is the first time anyone has worked here.
     * Idempotent: a second call returns the same record with the same id.
     */
    public Worksite register(WorksiteKey key, @Nullable String name, int villageId, long gameTime) {
        Worksite existing = sites.get(key);
        if (existing != null) {
            existing.see(gameTime);
            if (villageId != Worksite.NO_VILLAGE && villageId != existing.villageId()) {
                existing.setVillageId(villageId);
                setDirty();
            }
            return existing;
        }
        Worksite created = new Worksite(nextId++, key, name, villageId, gameTime, gameTime);
        sites.put(key, created);
        setDirty();
        return created;
    }

    /**
     * Moves a record onto a new key, keeping its id and everything hanging off it. This is what a
     * binding repair looks like: the place did not change, only the way we find it did.
     */
    public boolean rebind(WorksiteKey from, WorksiteKey to) {
        if (from == null || to == null || from.equals(to)) return false;
        Worksite site = sites.get(from);
        if (site == null || sites.containsKey(to)) return false;
        sites.remove(from);
        site.rebind(to);
        sites.put(to, site);
        setDirty();
        return true;
    }

    public boolean remove(@Nullable WorksiteKey key) {
        if (key == null || sites.remove(key) == null) return false;
        setDirty();
        return true;
    }

    /** Marks a site as still in use, so pruning can tell a quiet kitchen from an abandoned one. */
    public void touch(@Nullable WorksiteKey key, long gameTime) {
        Worksite site = find(key);
        if (site == null) return;
        long before = site.lastSeenGameTime();
        site.see(gameTime);
        if (site.lastSeenGameTime() != before) setDirty();
    }

    /**
     * Retires sites whose binding says they are gone. Ids are never reused, so a pruned site can
     * only ever be replaced by a genuinely new one.
     */
    public int prune(ServerLevel level) {
        List<WorksiteKey> gone = new ArrayList<>();
        for (Map.Entry<WorksiteKey, Worksite> entry : sites.entrySet()) {
            WorksiteKey key = entry.getKey();
            if (!key.dimension().equals(level.dimension().location())) continue;
            WorksiteBindings.Binding binding = WorksiteBindings.forKey(key);
            // An unregistered binding means its mod is absent, not that the place is gone.
            if (binding == null) continue;
            if (!binding.stillExists(level, key)) gone.add(key);
        }
        for (WorksiteKey key : gone) sites.remove(key);
        if (!gone.isEmpty()) setDirty();
        return gone.size();
    }

    // ── Persistence ──

    //? if >=1.21 {
    public static WorksiteRegister load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static WorksiteRegister load(CompoundTag tag) {
    *///?}
        WorksiteRegister data = new WorksiteRegister();
        data.loadedSchemaVersion = tag.contains(KEY_SCHEMA_VERSION) ? tag.getInt(KEY_SCHEMA_VERSION) : 0;
        data.nextId = Math.max(1L, tag.getLong(KEY_NEXT_ID));
        if (!tag.contains(KEY_SITES, Tag.TAG_LIST)) {
            data.nextId = counterAfterLoad(data.nextId, 0L);
            return data;
        }

        long highestLoadedId = 0L;
        ListTag list = tag.getList(KEY_SITES, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ResourceLocation binding = parseRl(entry.getString(KEY_BINDING));
            ResourceLocation dimension = parseRl(entry.getString(KEY_DIM));
            // A malformed row is dropped rather than crashing the load; the place re-registers.
            if (binding == null || dimension == null) continue;
            WorksiteKey key = new WorksiteKey(binding, dimension, entry.getLong(KEY_VALUE));
            long id = entry.getLong(KEY_ID);
            if (id <= 0) continue;
            data.sites.put(key, new Worksite(
                    id, key,
                    entry.getString(KEY_NAME),
                    entry.contains(KEY_VILLAGE) ? entry.getInt(KEY_VILLAGE) : Worksite.NO_VILLAGE,
                    entry.getLong(KEY_CREATED),
                    entry.getLong(KEY_LAST_SEEN)));
            // Absent on older saves, which reads as false: every pre-flag name re-derives, so a
            // stale "Kitchen" heals on load rather than surviving as accidental custom.
            data.sites.get(key).loadNameCustom(entry.getBoolean(KEY_NAME_CUSTOM));
            if (entry.hasUUID(KEY_DRIVER)) data.sites.get(key).setDriver(entry.getUUID(KEY_DRIVER));
            if (entry.contains(KEY_OWNERSHIP_TAG)) {
                List<com.aetherianartificer.townstead.storage.RoomOwner> owners = new ArrayList<>();
                if (entry.contains(KEY_OWNERS, Tag.TAG_LIST)) {
                    ListTag ownerRows = entry.getList(KEY_OWNERS, Tag.TAG_COMPOUND);
                    for (int ownerIndex = 0; ownerIndex < ownerRows.size(); ownerIndex++) {
                        CompoundTag owner = ownerRows.getCompound(ownerIndex);
                        if (!owner.hasUUID(KEY_OWNER_UUID)) continue;
                        owners.add(new com.aetherianartificer.townstead.storage.RoomOwner(
                                owner.getUUID(KEY_OWNER_UUID), owner.getString(KEY_OWNER_NAME),
                                com.aetherianartificer.townstead.storage.RoomOwner.Kind
                                        .parse(owner.getString(KEY_OWNER_KIND))));
                    }
                }
                data.sites.get(key).setOwnership(
                        net.minecraft.core.BlockPos.of(entry.getLong(KEY_OWNERSHIP_TAG)),
                        com.aetherianartificer.townstead.storage.OwnershipScope
                                .parse(entry.getString(KEY_OWNERSHIP_SCOPE)),
                        entry.contains(KEY_OWNERSHIP_PRIVATE)
                                ? entry.getBoolean(KEY_OWNERSHIP_PRIVATE) : !owners.isEmpty(),
                        owners);
            }
            loadOrders(entry, data.sites.get(key));
            highestLoadedId = Math.max(highestLoadedId, id);
        }
        data.nextId = counterAfterLoad(data.nextId, highestLoadedId);
        return data;
    }

    /**
     * Where the id counter has to resume. A save written before a crash can lag the ids actually
     * handed out, and resuming below one would hand a live place's id to a new one — so the counter
     * is whichever is higher, never just what was stored.
     */
    static long counterAfterLoad(long storedNextId, long highestLoadedId) {
        return Math.max(Math.max(1L, storedNextId), highestLoadedId + 1L);
    }

    //? if >=1.21 {
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*@Override
    public CompoundTag save(CompoundTag tag) {
    *///?}
        tag.putInt(KEY_SCHEMA_VERSION, SCHEMA_VERSION);
        tag.putLong(KEY_NEXT_ID, nextId);
        ListTag list = new ListTag();
        for (Worksite site : sites.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong(KEY_ID, site.id());
            entry.putString(KEY_BINDING, site.key().binding().toString());
            entry.putString(KEY_DIM, site.key().dimension().toString());
            entry.putLong(KEY_VALUE, site.key().value());
            entry.putString(KEY_NAME, site.name());
            if (site.nameCustom()) entry.putBoolean(KEY_NAME_CUSTOM, true);
            entry.putInt(KEY_VILLAGE, site.villageId());
            entry.putLong(KEY_CREATED, site.createdGameTime());
            entry.putLong(KEY_LAST_SEEN, site.lastSeenGameTime());
            if (site.driver() != null) entry.putUUID(KEY_DRIVER, site.driver());
            if (site.ownershipTag() != null) {
                entry.putLong(KEY_OWNERSHIP_TAG, site.ownershipTag().asLong());
                entry.putString(KEY_OWNERSHIP_SCOPE, site.ownershipScope().name());
                entry.putBoolean(KEY_OWNERSHIP_PRIVATE, site.ownershipPrivate());
                ListTag owners = new ListTag();
                for (com.aetherianartificer.townstead.storage.RoomOwner roomOwner : site.owners()) {
                    CompoundTag owner = new CompoundTag();
                    owner.putUUID(KEY_OWNER_UUID, roomOwner.uuid());
                    owner.putString(KEY_OWNER_NAME, roomOwner.name());
                    owner.putString(KEY_OWNER_KIND, roomOwner.kind().name());
                    owners.add(owner);
                }
                entry.put(KEY_OWNERS, owners);
            }
            saveOrders(entry, site);
            list.add(entry);
        }
        tag.put(KEY_SITES, list);
        return tag;
    }

    public int loadedSchemaVersion() {
        return loadedSchemaVersion;
    }

    // ── Orders ──

    private static void saveOrders(CompoundTag entry, Worksite site) {
        com.aetherianartificer.townstead.work.order.OrderList orders = site.orders();
        if (orders.isEmpty() && !orders.listOnly()) return;
        entry.putBoolean(KEY_LIST_ONLY, orders.listOnly());
        ListTag rows = new ListTag();
        for (com.aetherianartificer.townstead.work.order.Order order : orders.orders()) {
            CompoundTag row = new CompoundTag();
            row.putString(KEY_ORDER_OUTPUT, order.output().toString());
            if (order.exactProduct()) {
                row.putString(KEY_ORDER_PRODUCT, order.product().toString());
                row.putString(KEY_ORDER_PRODUCT_NAME, order.productName());
            }
            row.putString(KEY_ORDER_MODE, order.mode().name());
            row.putString(KEY_ORDER_KIND, order.kind().name());
            row.putInt(KEY_ORDER_TARGET, order.target());
            row.putString(KEY_ORDER_SCOPE, order.scope().name());
            row.putBoolean(KEY_ORDER_PAUSED, order.paused());
            row.putInt(KEY_ORDER_MIN_RANK, order.minRank());
            row.putInt(KEY_ORDER_PRODUCED, order.produced());
            if (order.workpiece() != null) {
                row.put("workpiece", order.workpiece());
                row.putString("workpieceName", order.workpieceName());
            }
            if (order.profession() != null) row.putString(KEY_ORDER_PROFESSION, order.profession().toString());
            if (order.villager() != null) row.putUUID(KEY_ORDER_VILLAGER, order.villager());
            if (order.operation() != com.aetherianartificer.townstead.work.order.Order.Operation.AUTOMATIC) {
                row.putString("operation", order.operation().name());
            }
            if (order.operator() != null) row.putUUID("operator", order.operator());
            // inProgress is deliberately NOT written: it means "a worker is mid-job right now", and
            // nobody is mid-job across a reload. Persisting it would leave a line permanently
            // claimed by a villager who no longer exists.
            rows.add(row);
        }
        entry.put(KEY_ORDERS, rows);
    }

    private static void loadOrders(CompoundTag entry, @Nullable Worksite site) {
        if (site == null) return;
        site.orders().setListOnly(entry.getBoolean(KEY_LIST_ONLY));
        if (!entry.contains(KEY_ORDERS, Tag.TAG_LIST)) return;
        ListTag rows = entry.getList(KEY_ORDERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < rows.size(); i++) {
            CompoundTag row = rows.getCompound(i);
            ResourceLocation output = parseRl(row.getString(KEY_ORDER_OUTPUT));
            // A line naming an item that no longer parses is dropped; the rest of the list stands.
            if (output == null) continue;
            com.aetherianartificer.townstead.work.order.Order order =
                    new com.aetherianartificer.townstead.work.order.Order(
                            output,
                            // Absent on every save written before activity lines existed, and
                            // absent reads as an item — which is what those all were.
                            com.aetherianartificer.townstead.work.order.Order.Kind
                                    .parse(row.getString(KEY_ORDER_KIND)),
                            com.aetherianartificer.townstead.work.order.Order.Mode
                                    .parse(row.getString(KEY_ORDER_MODE)),
                            row.getInt(KEY_ORDER_TARGET));
            if (row.contains(KEY_ORDER_PRODUCT)) {
                ResourceLocation product = parseRl(row.getString(KEY_ORDER_PRODUCT));
                if (product != null) {
                    order.setProduct(product, row.getString(KEY_ORDER_PRODUCT_NAME));
                }
            }
            order.setScope(com.aetherianartificer.townstead.work.order.Order.CountScope
                    .parse(row.getString(KEY_ORDER_SCOPE)));
            order.setPaused(row.getBoolean(KEY_ORDER_PAUSED));
            order.setMinRank(row.getInt(KEY_ORDER_MIN_RANK));
            order.setProduced(row.getInt(KEY_ORDER_PRODUCED));
            if (row.contains(KEY_ORDER_PROFESSION)) {
                order.setProfession(parseRl(row.getString(KEY_ORDER_PROFESSION)));
            }
            if (row.hasUUID(KEY_ORDER_VILLAGER)) order.setVillager(row.getUUID(KEY_ORDER_VILLAGER));
            if (row.contains("operation")) {
                order.setOperation(com.aetherianartificer.townstead.work.order.Order.Operation
                        .parse(row.getString("operation")));
            }
            if (row.hasUUID("operator")) order.setOperator(row.getUUID("operator"));
            if (row.contains("workpiece")) {
                order.setWorkpiece(row.getCompound("workpiece"), row.getString("workpieceName"));
            }
            site.orders().add(order);
        }
    }

    @Nullable
    private static ResourceLocation parseRl(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        //? if >=1.21 {
        return ResourceLocation.tryParse(raw);
        //?} else {
        /*try {
            return new ResourceLocation(raw);
        } catch (Exception e) {
            return null;
        }
        *///?}
    }
}
