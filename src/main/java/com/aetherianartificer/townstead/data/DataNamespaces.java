package com.aetherianartificer.townstead.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The data namespaces present in the current server data reload, for the {@code "namespace"}
 * clause of {@link ModGate}. A namespace is present when any enabled data pack or mod ships data
 * under it, so a pack can gate content on another pack being installed without knowing its file
 * or folder name.
 *
 * <p>{@link ResourceManager#getNamespaces()} is not trusted alone: on 1.20.1 Forge a data-only
 * namespace can be missing from it (see {@link DataPackLang}). The index also lists the
 * directories Townstead data commonly lives in and takes their namespaces.</p>
 *
 * <p>{@link #record} runs at the start of every server data reload, before any loader applies,
 * and again from loaders that receive the manager directly. It is cached per manager instance so
 * repeated calls in one reload are free.</p>
 */
public final class DataNamespaces {

    /** Directories scanned in addition to {@code getNamespaces()}. */
    private static final String[] SCAN_DIRECTORIES = {
            "root", "origin", "species", "ancestry", "lineage", "heritage", "gene", "rig", "attachment",
            "lang", "patch", "townstead", "collection", "trait", "chronotype", "culture", "profession",
            "founding_profile", "career_provider", "tags"
    };

    private static volatile Set<String> present = Set.of();
    private static WeakReference<ResourceManager> recordedFor = new WeakReference<>(null);

    private DataNamespaces() {}

    /** Whether {@code namespace} ships data in the current reload. */
    public static boolean isPresent(@Nullable String namespace) {
        return namespace != null && present.contains(namespace);
    }

    public static Set<String> all() {
        return present;
    }

    /** Index the namespaces of {@code manager}, unless this manager was already indexed. */
    public static synchronized void record(ResourceManager manager) {
        if (manager == null || recordedFor.get() == manager) return;
        Set<String> found = new HashSet<>(manager.getNamespaces());
        for (String directory : SCAN_DIRECTORIES) {
            try {
                for (ResourceLocation id : manager.listResources(directory, loc -> true).keySet()) {
                    found.add(id.getNamespace());
                }
            } catch (RuntimeException ignored) {
                // A broken pack must not stop the index; its own loader will report it.
            }
        }
        present = Set.copyOf(found);
        recordedFor = new WeakReference<>(manager);
    }

    /** Replace the index directly. Tests only. */
    static void setForTesting(Set<String> namespaces) {
        present = Set.copyOf(namespaces);
        recordedFor = new WeakReference<>(null);
    }

    /** Indexes namespaces during preparation, so the index is ready before any loader applies. */
    public static final class Recorder implements PreparableReloadListener {
        @Override
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                                              ProfilerFiller prepareProfiler, ProfilerFiller applyProfiler,
                                              Executor background, Executor game) {
            return CompletableFuture.runAsync(() -> record(manager), background).thenCompose(barrier::wait);
        }
    }
}
