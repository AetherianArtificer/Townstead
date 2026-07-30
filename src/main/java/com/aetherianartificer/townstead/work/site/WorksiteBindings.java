package com.aetherianartificer.townstead.work.site;

import com.aetherianartificer.townstead.Townstead;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The ways a {@link Worksite} can be located in the world, as a registry rather than a fixed list —
 * the same seam as station adapters, job-site providers and fluid readers.
 *
 * <p>A binding answers two questions: which key covers this position, and does that key still refer
 * to anything. Everything mod-specific lives behind it, so the register itself never learns what MCA
 * is or which generation of it is installed. The anchor binding ships here because it needs nothing
 * but a block position; the MCA room binding registers itself from compat.</p>
 */
public final class WorksiteBindings {

    /** Places identified by a block: a smoker, a Field Post, a lone workbench in a field. */
    //? if >=1.21 {
    public static final ResourceLocation ANCHOR = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "anchor");
    //?} else {
    /*public static final ResourceLocation ANCHOR = new ResourceLocation(Townstead.MOD_ID, "anchor");
    *///?}

    public interface Binding {

        ResourceLocation id();

        /**
         * The key for the place covering {@code pos}, or null when this binding does not recognise
         * anywhere here. Called on resolution paths, so it must be cheap and must not register
         * anything.
         */
        @Nullable
        WorksiteKey keyAt(ServerLevel level, BlockPos pos);

        /**
         * Whether this key still refers to something real. Pruning reads it, so answering "no"
         * retires a worksite — return true when unsure (an unloaded chunk is not a demolished
         * kitchen).
         */
        boolean stillExists(ServerLevel level, WorksiteKey key);

        /**
         * The blocks this key's place covers, derived from the world.
         *
         * <p>On the binding because only the binding knows what its key's {@code value} means. A
         * caller that reads {@code key.pos()} for a room binding gets a BlockPos unpacked from a
         * building id, which is a real position somewhere near the origin and silently wrong.</p>
         */
        java.util.Set<Long> extentOf(ServerLevel level, WorksiteKey key);

        /** A starting name for a freshly registered site. The player renames it from there. */
        default String defaultName(ServerLevel level, WorksiteKey key) {
            return "Worksite";
        }
    }

    private static final Map<ResourceLocation, Binding> BINDINGS = new ConcurrentHashMap<>();

    private WorksiteBindings() {}

    public static void register(Binding binding) {
        if (binding == null || binding.id() == null) return;
        BINDINGS.put(binding.id(), binding);
    }

    @Nullable
    public static Binding byId(@Nullable ResourceLocation id) {
        return id == null ? null : BINDINGS.get(id);
    }

    @Nullable
    public static Binding forKey(@Nullable WorksiteKey key) {
        return key == null ? null : byId(key.binding());
    }

    public static Collection<Binding> all() {
        return List.copyOf(BINDINGS.values());
    }

    /** Ships the bindings that need nothing but vanilla. Compat registers its own. */
    public static void bootstrap() {
        register(new AnchorBinding());
    }

    /**
     * A place that is wherever its block is. The block's existence is the binding: break the smoker
     * and the yard stops being a place, which is the same bargain the Field Post already makes.
     */
    static final class AnchorBinding implements Binding {

        @Override
        public ResourceLocation id() {
            return ANCHOR;
        }

        @Override
        public WorksiteKey keyAt(ServerLevel level, BlockPos pos) {
            return WorksiteKey.at(ANCHOR, level.dimension().location(), pos);
        }

        @Override
        public boolean stillExists(ServerLevel level, WorksiteKey key) {
            BlockPos pos = key.pos();
            // An unloaded chunk is not an answer; only a loaded, empty spot retires the site.
            if (!level.isLoaded(pos)) return true;
            return !level.getBlockState(pos).isAir();
        }

        @Override
        public java.util.Set<Long> extentOf(ServerLevel level, WorksiteKey key) {
            return com.aetherianartificer.townstead.work.WorkSiteBounds.workAreaAround(level, key.pos());
        }
    }
}
