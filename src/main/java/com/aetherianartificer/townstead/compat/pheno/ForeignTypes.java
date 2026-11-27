package com.aetherianartificer.townstead.compat.pheno;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.item.ItemAction;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionContext;
import com.aetherianartificer.townstead.pheno.action.item.ItemActionType;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.item.ItemCondition;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The bridge to the other copy of Pheno. A key this copy does not know is looked up in the other copy's own registry, and
 * its type is wrapped so that it runs here: the context is copied across, the node runs there, and what it changed comes
 * back. Each copy asks only the other's own registry, never its full lookup, so a key neither knows is not asked about
 * twice. Nothing here compiles against the other copy; with it missing, every lookup is empty.
 *
 * <p>Copied across: the entity, the other entity, the origin, the focus block, the item and the impact point. Not copied:
 * reservations, which belong to each copy. A subject, someone asked about rather than in the world, is not bridged: its
 * condition is false and its value 0.</p>
 *
 * <p>The same class, with the two package names swapped, is in the other copy.</p>
 */
public final class ForeignTypes {

    private static final Logger LOGGER = LoggerFactory.getLogger("townstead/ForeignTypes");
    private static volatile String other = "com.aetherianartificer.pheno.";
    private static final String OTHER_NAME = "Pheno";

    private static volatile boolean probed;
    private static boolean ready;
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private static Method actionOwn, actionParse, actionRun, actionFocus, actionItem, actionAt, actionSucceeded;
    private static Constructor<?> actionContext;
    private static Method conditionOwn, conditionParse, conditionTest, conditionAt, conditionItem;
    private static Constructor<?> conditionContext;
    private static Method biOwn, biParse, biTest;
    private static Method itemOwn, itemParse, itemRun;
    private static Constructor<?> itemContext;
    private static Method blockOwn, blockParse, blockRun, blockCanRun, blockItemRole, blockBlockRoles, blockItemRoles,
            blockReturned, blockSucceeded;
    private static Constructor<?> blockContext;
    private static Method valueOwn, valueParse, valueGet;
    private static @Nullable Method blockExtension, blockTest, itemExtension, itemTest;
    private static Constructor<?> selectorContext;

    private ForeignTypes() {}

    /** For tests: bridge to the copy under this package instead, such as this copy itself. */
    static synchronized void pointAt(String base) {
        other = base;
        probed = false;
        ready = false;
        REPORTED.clear();
    }

    public static Optional<ActionType> action(String key) {
        if (key == null || !probe()) return Optional.empty();
        Object type = foreign(actionOwn, key);
        if (type == null) return Optional.empty();
        String k = key;
        return Optional.of(new ActionType() {
            @Override public String key() { return k; }

            @Override public Action parse(JsonObject json) {
                Object node = parseIn(actionParse, type, json);
                if (node == null) return null;
                return ctx -> {
                    try {
                        Object there = toOther(ctx);
                        actionRun.invoke(node, there);
                        if (!Boolean.TRUE.equals(actionSucceeded.invoke(there))) ctx.fail();
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        report(k, e);
                        ctx.fail();
                    }
                };
            }
        });
    }

    public static Optional<ConditionType> condition(String key) {
        if (key == null || !probe()) return Optional.empty();
        Object type = foreign(conditionOwn, key);
        if (type == null) return Optional.empty();
        String k = key;
        return Optional.of(new ConditionType() {
            @Override public String key() { return k; }

            @Override public Condition parse(JsonObject json) {
                Object node = parseIn(conditionParse, type, json);
                if (node == null) return null;
                return ctx -> {
                    LivingEntity entity = ctx.entity();
                    if (entity == null) return false;
                    try {
                        Object there = conditionContext.newInstance(entity, ctx.other(), ctx.origin());
                        BlockPos pos = ctx.pos();
                        if (pos != null && !pos.equals(entity.blockPosition())) there = conditionAt.invoke(there, pos);
                        if (!ctx.item().isEmpty()) there = conditionItem.invoke(there, ctx.item());
                        return Boolean.TRUE.equals(conditionTest.invoke(node, there));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        report(k, e);
                        return false;
                    }
                };
            }
        });
    }

    public static Optional<BiEntityConditionType> biEntityCondition(String key) {
        if (key == null || !probe()) return Optional.empty();
        Object type = foreign(biOwn, key);
        if (type == null) return Optional.empty();
        String k = key;
        return Optional.of(new BiEntityConditionType() {
            @Override public String key() { return k; }

            @Override public BiEntityCondition parse(JsonObject json) {
                Object node = parseIn(biParse, type, json);
                if (node == null) return null;
                return (actor, target) -> {
                    try {
                        return Boolean.TRUE.equals(biTest.invoke(node, actor, target));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        report(k, e);
                        return false;
                    }
                };
            }
        });
    }

    public static Optional<ItemActionType> itemAction(String key) {
        if (key == null || !probe()) return Optional.empty();
        Object type = foreign(itemOwn, key);
        if (type == null) return Optional.empty();
        String k = key;
        return Optional.of(new ItemActionType() {
            @Override public String key() { return k; }

            @Override public ItemAction parse(JsonObject json) {
                Object node = parseIn(itemParse, type, json);
                if (node == null) return null;
                return ctx -> {
                    try {
                        itemRun.invoke(node, itemContext.newInstance(ctx.stack(), ctx.holder()));
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        report(k, e);
                    }
                };
            }
        });
    }

    public static Optional<BlockActionType> blockAction(String key) {
        if (key == null || !probe()) return Optional.empty();
        Object type = foreign(blockOwn, key);
        if (type == null) return Optional.empty();
        String k = key;
        return Optional.of(new BlockActionType() {
            @Override public String key() { return k; }

            @Override public BlockAction parse(JsonObject json) {
                Object node = parseIn(blockParse, type, json);
                if (node == null) return null;
                return new BlockAction() {
                    @Override public boolean canRun(BlockActionContext ctx) {
                        try {
                            return Boolean.TRUE.equals(blockCanRun.invoke(node, toOther(ctx)));
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            report(k, e);
                            return false;
                        }
                    }

                    @Override public void run(BlockActionContext ctx) {
                        try {
                            Object there = toOther(ctx);
                            blockRun.invoke(node, there);
                            if (blockItemRoles != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, ItemStack> roles = (Map<String, ItemStack>) blockItemRoles.invoke(there);
                                roles.forEach(ctx::setItemRole);
                            }
                            @SuppressWarnings("unchecked")
                            List<ItemStack> returned = (List<ItemStack>) blockReturned.invoke(there);
                            returned.forEach(ctx::returnItem);
                            if (!Boolean.TRUE.equals(blockSucceeded.invoke(there))) ctx.fail();
                        } catch (ReflectiveOperationException | RuntimeException e) {
                            report(k, e);
                            ctx.fail();
                        }
                    }
                };
            }
        });
    }

    public static Optional<ValueType> value(String key) {
        if (key == null || !probe()) return Optional.empty();
        Object type = foreign(valueOwn, key);
        if (type == null) return Optional.empty();
        String k = key;
        return Optional.of(new ValueType() {
            @Override public String key() { return k; }

            @Override public Value parse(JsonObject json) {
                Object node = parseIn(valueParse, type, json);
                if (node == null) return null;
                return ctx -> {
                    Level level = ctx.level();
                    if (level == null || ctx.subject() != null) return 0;
                    try {
                        Object there = selectorContext.newInstance(ctx.self(), ctx.other(), ctx.origin(), level, ctx.pos());
                        return ((Number) valueGet.invoke(node, there)).doubleValue();
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        report(k, e);
                        return 0;
                    }
                };
            }
        });
    }

    /** The other copy's added block-condition kind for {@code type} (no namespace), wrapped to run here; null if none. */
    public static @Nullable BlockCondition blockCondition(String type, JsonObject json) {
        if (type == null || !probe() || blockExtension == null || blockTest == null) return null;
        Object node = parseIn(blockExtension, null, type, json);
        if (node == null) return null;
        String k = type;
        return (level, pos) -> {
            try {
                return Boolean.TRUE.equals(blockTest.invoke(node, level, pos));
            } catch (ReflectiveOperationException | RuntimeException e) {
                report(k, e);
                return false;
            }
        };
    }

    /** The other copy's added item-condition kind for {@code type} (no namespace), wrapped to run here; null if none. */
    public static @Nullable ItemCondition itemCondition(String type, JsonObject json) {
        if (type == null || !probe() || itemExtension == null || itemTest == null) return null;
        Object node = parseIn(itemExtension, null, type, json);
        if (node == null) return null;
        String k = type;
        return (level, stack) -> {
            try {
                return Boolean.TRUE.equals(itemTest.invoke(node, level, stack));
            } catch (ReflectiveOperationException | RuntimeException e) {
                report(k, e);
                return false;
            }
        };
    }

    private static Object toOther(ActionContext ctx) throws ReflectiveOperationException {
        LivingEntity entity = ctx.entity();
        Object there = actionContext.newInstance(entity, ctx.other(), ctx.origin() == null ? entity : ctx.origin());
        if (ctx.focusBlock() != null) there = actionFocus.invoke(there, ctx.focusBlock());
        if (!ctx.item().isEmpty()) there = actionItem.invoke(there, ctx.item());
        if (ctx.at() != null && actionAt != null) there = actionAt.invoke(there, ctx.at());
        return there;
    }

    private static Object toOther(BlockActionContext ctx) throws ReflectiveOperationException {
        Object there = blockContext.newInstance(ctx.level(), ctx.pos(), ctx.cause());
        for (var role : ctx.itemRoles().entrySet()) blockItemRole.invoke(there, role.getKey(), role.getValue());
        blockBlockRoles.invoke(there, ctx.blockRoles());
        return there;
    }

    /** The other copy's own type for a key, or null with no such type. Only after {@link #probe()} has passed. */
    private static @Nullable Object foreign(@Nullable Method own, String key) {
        try {
            Optional<?> type = (Optional<?>) own.invoke(null, key);
            return type.orElse(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            report(key, e);
            return null;
        }
    }

    /** Parses with the other copy's type. Its own parse errors come through as they are, so the data loader reports them. */
    private static @Nullable Object parseIn(Method parse, Object type, JsonObject json) {
        try {
            return parse.invoke(type, json);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** As {@link #parseIn(Method, Object, JsonObject)}, for a static lookup that takes the type's name too. */
    private static @Nullable Object parseIn(Method lookup, @Nullable Object owner, String type, JsonObject json) {
        try {
            return lookup.invoke(owner, type, json.deepCopy());
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void report(String key, Throwable e) {
        if (REPORTED.add(key)) LOGGER.warn("{} type '{}' failed through the bridge: {}", OTHER_NAME, key, e.toString());
    }

    /** A registry's own-only lookup. A copy from before the bridge has only {@code get}, which is own-only there. */
    private static Method ownLookup(Class<?> registry) throws NoSuchMethodException {
        try {
            return registry.getMethod("own", String.class);
        } catch (NoSuchMethodException e) {
            return registry.getMethod("get", String.class);
        }
    }

    /** A method an older copy may not have yet; null when it is missing. */
    private static @Nullable Method optional(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static boolean probe() {
        if (probed) return ready;
        synchronized (ForeignTypes.class) {
            if (probed) return ready;
            try {
                Class<?> actionCtx = Class.forName(other + "action.ActionContext");
                actionOwn = ownLookup(Class.forName(other + "action.ActionTypes"));
                actionParse = Class.forName(other + "action.ActionType").getMethod("parse", JsonObject.class);
                actionRun = Class.forName(other + "action.Action").getMethod("run", actionCtx);
                actionContext = actionCtx.getConstructor(LivingEntity.class, LivingEntity.class, LivingEntity.class);
                actionFocus = actionCtx.getMethod("withFocusBlock", BlockPos.class);
                actionItem = actionCtx.getMethod("withItem", ItemStack.class);
                actionAt = optional(actionCtx, "withAt", Vec3.class);
                actionSucceeded = actionCtx.getMethod("succeeded");

                Class<?> conditionCtx = Class.forName(other + "condition.ConditionContext");
                conditionOwn = ownLookup(Class.forName(other + "condition.ConditionTypes"));
                conditionParse = Class.forName(other + "condition.ConditionType").getMethod("parse", JsonObject.class);
                conditionTest = Class.forName(other + "condition.Condition").getMethod("test", conditionCtx);
                conditionContext = conditionCtx.getConstructor(LivingEntity.class, LivingEntity.class, LivingEntity.class);
                conditionAt = conditionCtx.getMethod("at", BlockPos.class);
                conditionItem = conditionCtx.getMethod("withItem", ItemStack.class);

                biOwn = ownLookup(Class.forName(other + "condition.bientity.BiEntityConditionTypes"));
                biParse = Class.forName(other + "condition.bientity.BiEntityConditionType").getMethod("parse", JsonObject.class);
                biTest = Class.forName(other + "condition.bientity.BiEntityCondition").getMethod("test", LivingEntity.class, LivingEntity.class);

                Class<?> itemCtx = Class.forName(other + "action.item.ItemActionContext");
                itemOwn = ownLookup(Class.forName(other + "action.item.ItemActionTypes"));
                itemParse = Class.forName(other + "action.item.ItemActionType").getMethod("parse", JsonObject.class);
                itemRun = Class.forName(other + "action.item.ItemAction").getMethod("run", itemCtx);
                itemContext = itemCtx.getConstructor(ItemStack.class, LivingEntity.class);

                Class<?> blockCtx = Class.forName(other + "action.block.BlockActionContext");
                Class<?> blockAction = Class.forName(other + "action.block.BlockAction");
                blockOwn = ownLookup(Class.forName(other + "action.block.BlockActionTypes"));
                blockParse = Class.forName(other + "action.block.BlockActionType").getMethod("parse", JsonObject.class);
                blockRun = blockAction.getMethod("run", blockCtx);
                blockCanRun = blockAction.getMethod("canRun", blockCtx);
                blockContext = blockCtx.getConstructor(ServerLevel.class, BlockPos.class, LivingEntity.class);
                blockItemRole = blockCtx.getMethod("setItemRole", String.class, ItemStack.class);
                blockBlockRoles = blockCtx.getMethod("withBlockRoles", Map.class);
                blockItemRoles = optional(blockCtx, "itemRoles");
                blockReturned = blockCtx.getMethod("returnedItems");
                blockSucceeded = blockCtx.getMethod("succeeded");

                Class<?> selectorCtx = Class.forName(other + "selector.SelectorContext");
                valueOwn = ownLookup(Class.forName(other + "value.ValueTypes"));
                valueParse = Class.forName(other + "value.ValueType").getMethod("parse", JsonObject.class);
                valueGet = Class.forName(other + "value.Value").getMethod("get", selectorCtx);
                selectorContext = selectorCtx.getConstructor(LivingEntity.class, LivingEntity.class, LivingEntity.class, Level.class, Vec3.class);

                blockExtension = optional(Class.forName(other + "condition.block.BlockConditions"), "extension", String.class, JsonObject.class);
                Class<?> blockCondition = Class.forName(other + "condition.block.BlockCondition");
                blockTest = optional(blockCondition, "test", Level.class, net.minecraft.core.BlockPos.class);
                itemExtension = optional(Class.forName(other + "condition.item.ItemConditions"), "extension", String.class, JsonObject.class);
                itemTest = optional(Class.forName(other + "condition.item.ItemCondition"), "test", Level.class, ItemStack.class);

                ready = true;
                LOGGER.info("{} is installed; types this copy does not know are looked up in it", OTHER_NAME);
            } catch (ClassNotFoundException e) {
                ready = false;
            } catch (ReflectiveOperationException | LinkageError e) {
                ready = false;
                LOGGER.warn("{} is installed but its types cannot be bridged ({}); only this copy's types run", OTHER_NAME, e.toString());
            }
            probed = true;
            return ready;
        }
    }
}
