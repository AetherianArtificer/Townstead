package com.aetherianartificer.townstead.block.haze;

import com.aetherianartificer.townstead.Townstead;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The loaded haze kinds, indexed by their position in id order. That index is what a haze
 * block's {@code kind} property stores, so the server and every client must agree on it: the
 * server sorts on load, and clients take the server's list as sent.
 *
 * <p>Two instances, because an integrated server and its client share statics: the server
 * reads from {@link #server()}, rendering reads from {@link #client()}.</p>
 */
public final class HazeKinds {

    private static final HazeKinds SERVER = new HazeKinds();
    private static final HazeKinds CLIENT = new HazeKinds();

    private volatile List<HazeKind> ordered = List.of();

    private HazeKinds() {}

    public static HazeKinds server() { return SERVER; }
    public static HazeKinds client() { return CLIENT; }

    public static HazeKinds forSide(boolean clientSide) {
        return clientSide ? CLIENT : SERVER;
    }

    public List<HazeKind> all() {
        return ordered;
    }

    @Nullable
    public HazeKind byIndex(int index) {
        List<HazeKind> kinds = ordered;
        return index >= 0 && index < kinds.size() ? kinds.get(index) : null;
    }

    /** The kind's block-state index, or -1 when no such kind is loaded. */
    public int indexOf(ResourceLocation id) {
        List<HazeKind> kinds = ordered;
        for (int i = 0; i < kinds.size(); i++) {
            if (kinds.get(i).id().equals(id)) return i;
        }
        return -1;
    }

    public void replaceAll(List<HazeKind> next) {
        ordered = List.copyOf(next);
    }

    /** Loads {@code data/<ns>/haze/*.json} into {@link #server()}. */
    public static final class Loader extends SimpleJsonResourceReloadListener {

        public Loader() {
            super(new Gson(), "haze");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                             ProfilerFiller profiler) {
            List<HazeKind> kinds = new ArrayList<>();
            for (Map.Entry<ResourceLocation, JsonElement> file : new TreeMap<>(files).entrySet()) {
                if (kinds.size() > HazeBlock.MAX_KIND) {
                    Townstead.LOGGER.warn("[Haze] More than {} haze kinds; {} and later are ignored",
                            HazeBlock.MAX_KIND + 1, file.getKey());
                    break;
                }
                try {
                    kinds.add(HazeKind.parse(file.getKey(),
                            GsonHelper.convertToJsonObject(file.getValue(), "haze")));
                } catch (RuntimeException error) {
                    Townstead.LOGGER.warn("[Haze] Skipping {}: {}", file.getKey(), error.getMessage());
                }
            }
            SERVER.replaceAll(kinds);
        }
    }
}
