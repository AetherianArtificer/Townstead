package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.types.CollectionGeneType;
import com.aetherianartificer.townstead.origin.gene.types.ReachHook;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read/write access to {@code collection} gene stores, backed by {@link CollectionSavedData} so
 * membership persists across reload. Keyed by holder UUID then collection gene id, holding member
 * elements (canonical strings, see {@link CollectionElement}) in insertion order with a per-member
 * tally and expiry game-time ({@link Long#MAX_VALUE} = permanent). A plain set is the tally-is-one
 * case: {@link #addElement} keeps members at one, {@link #changeCount} does arithmetic. The generic
 * string ops serve every {@code of}; entity/block/item helpers encode at the boundary. on_add/
 * on_remove/on_reach hooks and the dead-member purge are entity-only (the member is an entity to run
 * an action on).
 */
public final class CollectionValues {

    private CollectionValues() {}

    // --- entity members ---

    public static boolean add(LivingEntity holder, ResourceLocation id, LivingEntity member, @Nullable Integer ttlTicks) {
        return member != null && addElement(holder, id, CollectionElement.ofEntity(member), ttlTicks);
    }

    public static boolean remove(LivingEntity holder, ResourceLocation id, LivingEntity member) {
        return member != null && removeElement(holder, id, CollectionElement.ofEntity(member));
    }

    public static boolean contains(LivingEntity holder, ResourceLocation id, LivingEntity member) {
        return member != null && containsElement(holder, id, CollectionElement.ofEntity(member));
    }

    public static int changeCountEntity(LivingEntity holder, ResourceLocation id, LivingEntity member,
                                        int amount, boolean set, @Nullable Integer ttlTicks) {
        return member == null ? 0 : changeCount(holder, id, CollectionElement.ofEntity(member), amount, set, ttlTicks);
    }

    public static int countEntity(LivingEntity holder, ResourceLocation id, LivingEntity member) {
        return member == null ? 0 : count(holder, id, CollectionElement.ofEntity(member));
    }

    // --- block / item members ---

    public static boolean addBlock(LivingEntity holder, ResourceLocation id, BlockPos pos, @Nullable Integer ttlTicks) {
        return addElement(holder, id, CollectionElement.ofBlock(pos), ttlTicks);
    }

    public static boolean addItem(LivingEntity holder, ResourceLocation id, ItemStack stack, @Nullable Integer ttlTicks) {
        return addElement(holder, id, CollectionElement.ofItem(stack), ttlTicks);
    }

    // --- generic string members ---

    /** Ensure {@code element} is present (tally one for a fresh member), refreshing its TTL. */
    public static boolean addElement(LivingEntity holder, ResourceLocation id, String element, @Nullable Integer ttlTicks) {
        if (holder.level().isClientSide) return false;
        CollectionGeneType.Instance inst = instanceOf(id);
        CollectionSavedData data = data(holder);
        if (inst == null || data == null) return false;
        LinkedHashMap<String, CollectionMember> store = set(data, holder, id, true);
        CollectionMember existing = store.get(element);
        if (existing != null) {
            existing.expiry = expiryFor(holder, inst, ttlTicks);
            data.setDirty();
            return false;
        }
        if (inst.bounded() && store.size() >= inst.max() && !evictOldest(holder, inst, store)) return false;
        store.put(element, new CollectionMember(1, expiryFor(holder, inst, ttlTicks)));
        data.setDirty();
        fireAdd(holder, inst, element);
        return true;
    }

    /**
     * Adjust {@code element}'s tally ({@code set} replaces, else adds {@code amount}), creating it on
     * first appearance and dropping it when the tally falls to zero. Returns the new tally. Fires
     * on_add on appearance, on_remove on drop, and on_reach for every threshold the rise crosses.
     */
    public static int changeCount(LivingEntity holder, ResourceLocation id, String element,
                                  int amount, boolean set, @Nullable Integer ttlTicks) {
        if (holder.level().isClientSide) return 0;
        CollectionGeneType.Instance inst = instanceOf(id);
        CollectionSavedData data = data(holder);
        if (inst == null || data == null) return 0;
        LinkedHashMap<String, CollectionMember> store = set(data, holder, id, true);
        CollectionMember member = store.get(element);
        int prev = member == null ? 0 : member.count;
        int next = set ? amount : prev + amount;
        if (next <= 0) {
            if (member != null) {
                store.remove(element);
                data.setDirty();
                if (prev > 0) fireRemove(holder, inst, element);
            }
            return 0;
        }
        boolean appearing = member == null;
        if (appearing) {
            if (inst.bounded() && store.size() >= inst.max() && !evictOldest(holder, inst, store)) return prev;
            member = new CollectionMember(next, 0L);
            store.put(element, member);
        } else {
            member.count = next;
        }
        member.expiry = expiryFor(holder, inst, ttlTicks);
        data.setDirty();
        if (appearing) fireAdd(holder, inst, element);
        fireReach(holder, inst, element, prev, next);
        return next;
    }

    public static boolean removeElement(LivingEntity holder, ResourceLocation id, String element) {
        if (holder.level().isClientSide) return false;
        CollectionSavedData data = data(holder);
        LinkedHashMap<String, CollectionMember> store = data == null ? null : set(data, holder, id, false);
        if (store == null || store.remove(element) == null) return false;
        data.setDirty();
        fireRemove(holder, instanceOf(id), element);
        return true;
    }

    public static boolean containsElement(LivingEntity holder, ResourceLocation id, String element) {
        CollectionMember member = member(holder, id, element);
        return member != null && member.count > 0;
    }

    public static int count(LivingEntity holder, ResourceLocation id, String element) {
        CollectionMember member = member(holder, id, element);
        return member == null ? 0 : member.count;
    }

    public static void clearOne(LivingEntity holder, ResourceLocation id) {
        if (holder.level().isClientSide) return;
        CollectionSavedData data = data(holder);
        LinkedHashMap<String, CollectionMember> store = data == null ? null : set(data, holder, id, false);
        if (store == null || store.isEmpty()) return;
        CollectionGeneType.Instance inst = instanceOf(id);
        for (String element : new ArrayList<>(store.keySet())) fireRemove(holder, inst, element);
        store.clear();
        data.setDirty();
    }

    public static int size(LivingEntity holder, ResourceLocation id) {
        CollectionSavedData data = data(holder);
        LinkedHashMap<String, CollectionMember> store = data == null ? null : set(data, holder, id, false);
        return store == null ? 0 : store.size();
    }

    public static List<String> elements(LivingEntity holder, ResourceLocation id) {
        CollectionSavedData data = data(holder);
        LinkedHashMap<String, CollectionMember> store = data == null ? null : set(data, holder, id, false);
        return store == null ? List.of() : new ArrayList<>(store.keySet());
    }

    /** Sweep expired entries across the holder's collections, firing on_remove for each. */
    public static void tick(LivingEntity holder) {
        CollectionSavedData data = data(holder);
        if (data == null) return;
        Map<ResourceLocation, LinkedHashMap<String, CollectionMember>> byId = data.store().get(holder.getUUID());
        if (byId == null) return;
        long now = holder.level().getGameTime();
        boolean changed = false;
        for (Map.Entry<ResourceLocation, LinkedHashMap<String, CollectionMember>> e : byId.entrySet()) {
            LinkedHashMap<String, CollectionMember> store = e.getValue();
            if (store.isEmpty()) continue;
            CollectionGeneType.Instance inst = instanceOf(e.getKey());
            Iterator<Map.Entry<String, CollectionMember>> it = store.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, CollectionMember> entry = it.next();
                if (now < entry.getValue().expiry) continue;
                String element = entry.getKey();
                it.remove();
                changed = true;
                fireRemove(holder, inst, element);
            }
        }
        if (changed) data.setDirty();
    }

    /**
     * On a non-player's permanent death: drop its own collections and purge its entity element from
     * every other holder's set. Silent, since the member is gone. Players are excluded by the caller.
     */
    public static void onDeath(LivingEntity dead) {
        CollectionSavedData data = data(dead);
        if (data == null) return;
        String element = CollectionElement.ofEntity(dead);
        boolean changed = data.store().remove(dead.getUUID()) != null;
        for (Map<ResourceLocation, LinkedHashMap<String, CollectionMember>> byId : data.store().values()) {
            for (LinkedHashMap<String, CollectionMember> store : byId.values()) {
                if (store.remove(element) != null) changed = true;
            }
        }
        if (changed) data.setDirty();
    }

    /** Resolve an entity element to its live entity, or null when it is gone or unloaded. */
    @Nullable
    public static LivingEntity resolveMember(LivingEntity holder, String element) {
        UUID uuid = CollectionElement.toUuid(element);
        if (uuid == null) return null;
        MinecraftServer server = holder.getServer();
        if (server == null) return null;
        for (ServerLevel level : server.getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof LivingEntity le) return le;
        }
        return null;
    }

    /** Effective expiry game-time for a touch: explicit TTL, else the gene's forget_after, else permanent. */
    private static long expiryFor(LivingEntity holder, CollectionGeneType.Instance inst, @Nullable Integer ttlTicks) {
        int ttl = ttlTicks != null ? ttlTicks : inst.forgetAfter();
        return ttl > 0 ? holder.level().getGameTime() + ttl : Long.MAX_VALUE;
    }

    /** Make room for a new member in a bounded store: reject (no room) or evict the oldest. */
    private static boolean evictOldest(LivingEntity holder, CollectionGeneType.Instance inst,
                                       LinkedHashMap<String, CollectionMember> store) {
        if (inst.onFull() == CollectionGeneType.OnFull.REJECT) return false;
        Iterator<String> oldest = store.keySet().iterator();
        if (oldest.hasNext()) {
            String evicted = oldest.next();
            oldest.remove();
            fireRemove(holder, inst, evicted);
        }
        return true;
    }

    private static void fireAdd(LivingEntity holder, @Nullable CollectionGeneType.Instance inst, String element) {
        if (inst != null) fire(holder, inst, element, inst.onAdd(), inst.onAddBlock());
    }

    private static void fireRemove(LivingEntity holder, @Nullable CollectionGeneType.Instance inst, String element) {
        if (inst != null) fire(holder, inst, element, inst.onRemove(), inst.onRemoveBlock());
    }

    /** Run any on_reach hook the rise from {@code prev} to {@code next} crosses (entity/key only). */
    private static void fireReach(LivingEntity holder, CollectionGeneType.Instance inst, String element,
                                  int prev, int next) {
        if (inst.onReach().isEmpty()) return;
        LivingEntity member = inst.of() == CollectionGeneType.Of.ENTITY ? resolveMember(holder, element) : null;
        if (inst.of() == CollectionGeneType.Of.ENTITY && member == null) return;
        for (ReachHook hook : inst.onReach()) {
            if (!hook.crossed(prev, next)) continue;
            hook.action().run(member != null ? new ActionContext(holder, member) : new ActionContext(holder));
        }
    }

    /** Runs an on_add/on_remove hook in the form the element's kind calls for. */
    private static void fire(LivingEntity holder, CollectionGeneType.Instance inst, String element,
                             @Nullable Action entityHook, @Nullable BlockAction blockHook) {
        switch (inst.of()) {
            case ENTITY -> {
                if (entityHook == null) return;
                LivingEntity member = resolveMember(holder, element);
                if (member != null) entityHook.run(new ActionContext(holder, member));
            }
            case KEY -> {
                if (entityHook != null) entityHook.run(new ActionContext(holder));
            }
            case BLOCK -> {
                BlockPos pos = blockHook == null ? null : CollectionElement.toBlock(element);
                if (pos != null && holder.level() instanceof ServerLevel level) {
                    blockHook.run(new BlockActionContext(level, pos, holder));
                }
            }
            case ITEM -> { }
        }
    }

    @Nullable
    private static CollectionMember member(LivingEntity holder, ResourceLocation id, String element) {
        CollectionSavedData data = data(holder);
        LinkedHashMap<String, CollectionMember> store = data == null ? null : set(data, holder, id, false);
        return store == null ? null : store.get(element);
    }

    @Nullable
    private static CollectionSavedData data(LivingEntity holder) {
        MinecraftServer server = holder.getServer();
        return server == null ? null : CollectionSavedData.get(server);
    }

    @Nullable
    private static LinkedHashMap<String, CollectionMember> set(CollectionSavedData data, LivingEntity holder,
                                                               ResourceLocation id, boolean create) {
        Map<ResourceLocation, LinkedHashMap<String, CollectionMember>> byId = data.store().get(holder.getUUID());
        if (byId == null) {
            if (!create) return null;
            byId = new java.util.concurrent.ConcurrentHashMap<>();
            data.store().put(holder.getUUID(), byId);
        }
        LinkedHashMap<String, CollectionMember> store = byId.get(id);
        if (store == null && create) {
            store = new LinkedHashMap<>();
            byId.put(id, store);
        }
        return store;
    }

    @Nullable
    private static CollectionGeneType.Instance instanceOf(ResourceLocation id) {
        Gene gene = GeneRegistry.byId(id);
        return gene != null && gene.instance() instanceof CollectionGeneType.Instance i ? i : null;
    }
}
