package com.aetherianartificer.townstead.compat.thirst;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import static org.junit.jupiter.api.Assertions.*;

class LsoManagerReloadTest {
    public interface Manager { int revision(); }
    public static final class Reloadable implements Manager, PreparableReloadListener {
        int revision;
        final CompletableFuture<Void> completion = new CompletableFuture<>();
        public int revision() { return revision; }
        public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resources,
                ProfilerFiller prepare, ProfilerFiller apply, Executor background, Executor game) {
            revision++;
            return completion;
        }
    }
    @Test void wrappedManagerRetainsReloadContractAndOriginalFuture() {
        var delegate = new Reloadable();
        Object proxy = DataDrivenThirstCompat.wrapLsoManager(Manager.class, delegate);
        // This exact cast is performed by LSO when opening a world or reloading packs.
        var listener = assertInstanceOf(PreparableReloadListener.class, proxy);
        assertSame(delegate.completion, listener.reload(null,null,null,null,Runnable::run,Runnable::run));
        assertEquals(1, ((Manager) proxy).revision());
        assertFalse(delegate.completion.isDone());
        delegate.completion.completeExceptionally(new IllegalStateException("native reload failure"));
        assertTrue(listener.reload(null,null,null,null,Runnable::run,Runnable::run).isCompletedExceptionally());
    }
    @Test void laterWrapperCannotRedirectPreviouslyRegisteredListener() {
        var first = new Reloadable(); var second = new Reloadable();
        var a = (PreparableReloadListener) DataDrivenThirstCompat.wrapLsoManager(Manager.class, first);
        var b = (PreparableReloadListener) DataDrivenThirstCompat.wrapLsoManager(Manager.class, second);
        a.reload(null,null,null,null,Runnable::run,Runnable::run);
        assertEquals(1,first.revision); assertEquals(0,second.revision);
        b.reload(null,null,null,null,Runnable::run,Runnable::run);
        assertEquals(1,second.revision);
        assertEquals(a,a); assertNotEquals(a,b); assertEquals(a.hashCode(),a.hashCode());
    }
}
