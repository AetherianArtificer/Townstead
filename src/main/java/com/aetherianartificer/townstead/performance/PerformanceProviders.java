package com.aetherianartificer.townstead.performance;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Provider registry plus per-actor/channel priority arbitration. Safe for reactions and states as well as hangouts. */
public final class PerformanceProviders {
    private record Channel(UUID actor, String channel) {}
    private record Active(int priority, long expiresAt, PerformanceHandle handle) {}

    private static final Map<String, PerformanceProvider> PROVIDERS = new LinkedHashMap<>();
    private static final Map<Channel, Active> ACTIVE = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private PerformanceProviders() {}

    public static synchronized void register(PerformanceProvider provider) {
        Objects.requireNonNull(provider, "provider");
        if (provider.id() == null || provider.id().isBlank()) throw new IllegalArgumentException("provider id is required");
        PerformanceProvider previous = PROVIDERS.putIfAbsent(provider.id(), provider);
        if (previous != null && previous != provider) {
            throw new IllegalStateException("Performance provider already registered: " + provider.id());
        }
    }

    public static synchronized List<PerformanceProvider> all() {
        bootstrap();
        return PROVIDERS.values().stream()
                .sorted(Comparator.comparingInt(PerformanceProvider::priority).reversed()
                        .thenComparing(PerformanceProvider::id))
                .toList();
    }

    public static @Nullable PerformanceHandle play(ServerLevel level, PerformanceRequest request) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(request, "request");
        Channel channel = new Channel(request.actor().getUUID(), request.channel());
        synchronized (PerformanceProviders.class) {
            prune(level.getGameTime());
            Active active = ACTIVE.get(channel);
            if (active != null && active.priority() > request.priority()) return null;
            if (active != null) active.handle().stop();
        }

        PerformanceHandle providerHandle = mapped(level, request);
        if (providerHandle == null) providerHandle = direct(level, request, null);
        if (providerHandle == null) providerHandle = fallback(request);
        if (providerHandle == null) return null;

        PerformanceHandle delegate = providerHandle;
        PerformanceHandle tracked = new PerformanceHandle() {
            private boolean stopped;
            @Override public synchronized void stop() {
                if (stopped) return;
                stopped = true;
                delegate.stop();
                synchronized (PerformanceProviders.class) {
                    Active current = ACTIVE.get(channel);
                    if (current != null && current.handle() == this) ACTIVE.remove(channel);
                }
            }
        };
        synchronized (PerformanceProviders.class) {
            ACTIVE.put(channel, new Active(request.priority(), level.getGameTime() + request.durationTicks(), tracked));
        }
        return tracked;
    }

    private static @Nullable PerformanceHandle mapped(ServerLevel level, PerformanceRequest request) {
        for (PerformanceMappings.Target target : PerformanceMappings.targets(request.performance())) {
            PerformanceRequest mapped = new PerformanceRequest(request.actor(), target.performance(),
                    request.channel(), request.durationTicks(), request.priority(), PerformanceRequest.Fallback.NONE);
            PerformanceHandle handle = direct(level, mapped, target.provider());
            if (handle != null) return handle;
        }
        return null;
    }

    private static @Nullable PerformanceHandle direct(ServerLevel level, PerformanceRequest request,
                                                        @Nullable String onlyProvider) {
        for (PerformanceProvider provider : all()) {
            if (onlyProvider != null && !onlyProvider.equals(provider.id())) continue;
            if (!provider.supports(request)) continue;
            PerformanceHandle handle = provider.play(level, request);
            if (handle != null) return handle;
        }
        return null;
    }

    private static synchronized void bootstrap() {
        if (bootstrapped) return;
        bootstrapped = true;
        register(new ReactionPerformanceProvider());
        register(new VanillaPerformanceProvider());
    }

    public static synchronized void prune(long now) {
        List<PerformanceHandle> expired = new ArrayList<>();
        ACTIVE.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt() > now) return false;
            expired.add(entry.getValue().handle());
            return true;
        });
        expired.forEach(PerformanceHandle::stop);
    }

    private static @Nullable PerformanceHandle fallback(PerformanceRequest request) {
        return switch (request.fallback()) {
            case NONE -> PerformanceHandle.NONE;
            case VANILLA_GESTURE -> {
                request.actor().swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                yield PerformanceHandle.NONE;
            }
            case STAND -> {
                if (request.actor() instanceof Mob mob) mob.getNavigation().stop();
                yield PerformanceHandle.NONE;
            }
        };
    }
}
