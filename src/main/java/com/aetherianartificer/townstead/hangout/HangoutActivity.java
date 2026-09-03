package com.aetherianartificer.townstead.hangout;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.performance.PerformanceRequest;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** One shared activity in a session; roles and animation refs are deliberately open vocabularies. */
public record HangoutActivity(ResourceLocation id, Kind kind, int minimumParticipants,
                              int maximumParticipants, int durationTicks,
                              Map<String, Integer> roles, Set<ResourceLocation> postures,
                              @Nullable Condition startWhen, @Nullable Condition continueWhen,
                              @Nullable Condition serviceWhen,
                              @Nullable Action onStart, @Nullable Action onTick,
                              @Nullable Action onFinish, @Nullable Action onServiceAccepted,
                              @Nullable Action onServiceRefused, @Nullable Action onServiceMissing,
                              List<ServiceCourse> serviceCourses, @Nullable Performance performance) {
    public enum Kind { SOCIALIZE, EAT, DRINK, MIXED }

    public record Performance(ResourceLocation id, String channel, int durationTicks,
                              int priority, PerformanceRequest.Fallback fallback) {
        public Performance {
            Objects.requireNonNull(id, "id");
            if (channel == null || channel.isBlank()) throw new IllegalArgumentException("performance channel is required");
            if (durationTicks < 1) throw new IllegalArgumentException("performance duration must be positive");
            fallback = fallback == null ? PerformanceRequest.Fallback.VANILLA_GESTURE : fallback;
        }
    }

    /** One ordered hosted-service course. Role is an open name such as bartender or server. */
    public record ServiceCourse(String id, Kind kind, String role, int atTicks, int leaseTicks) {
        public ServiceCourse {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("service course id is required");
            kind = kind == null ? Kind.MIXED : kind;
            if (role == null || role.isBlank()) throw new IllegalArgumentException("service course role is required");
            if (atTicks < 0) throw new IllegalArgumentException("service course at_ticks cannot be negative");
            if (leaseTicks < 1) throw new IllegalArgumentException("service course lease_ticks must be positive");
        }
    }

    public HangoutActivity {
        Objects.requireNonNull(id, "id");
        kind = kind == null ? Kind.SOCIALIZE : kind;
        roles = roles == null || roles.isEmpty() ? Map.of("participant", minimumParticipants) : Map.copyOf(roles);
        postures = postures == null ? Set.of() : Set.copyOf(postures);
        serviceCourses = serviceCourses == null ? List.of() : List.copyOf(serviceCourses);
        if (minimumParticipants < 1) throw new IllegalArgumentException("minimum_participants must be positive");
        if (maximumParticipants < minimumParticipants) {
            throw new IllegalArgumentException("maximum_participants must be >= minimum_participants");
        }
        if (durationTicks < 20) throw new IllegalArgumentException("duration_ticks must be at least 20");
    }
}
