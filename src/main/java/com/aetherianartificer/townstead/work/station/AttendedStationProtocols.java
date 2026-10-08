package com.aetherianartificer.townstead.work.station;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime state for workstation-v2 attended processes; foreign state remains authoritative. */
public final class AttendedStationProtocols {
    public enum Phase { OBSERVING, RESPONDED, INCIDENT_BLOCKED, TIMED_OUT, SAFE_STOPPED, CLEANED }
    public record Diagnostic(ResourceLocation definition, UUID owner, Phase phase,
                             @Nullable String incident, int attempts, int solved,
                             long startedAt, long observedAt, String detail) {}

    private static final class State {
        final ResourceLocation definition;
        final UUID owner;
        final long startedAt;
        long nextPoll;
        int solved;
        final Map<String, Integer> attempts = new LinkedHashMap<>();
        volatile Diagnostic diagnostic;

        State(ResourceLocation definition, UUID owner, long now) {
            this.definition = definition;
            this.owner = owner;
            this.startedAt = now;
            this.nextPoll = now;
            this.diagnostic = new Diagnostic(definition, owner, Phase.OBSERVING, null,
                    0, 0, now, now, "attendance_started");
        }
    }

    private static final Map<String, State> ACTIVE = new ConcurrentHashMap<>();

    private AttendedStationProtocols() {}

    /** Returns false only while a due incident remains unresolved or the authored deadline elapsed. */
    public static boolean observe(ServerLevel level, VillagerEntityMCA worker, BlockPos pos,
                                  WorkstationV2Def def) {
        if (level == null || worker == null || pos == null || def == null || def.attendance() == null) {
            return true;
        }
        long now = level.getGameTime();
        String key = key(level, pos);
        State state = ACTIVE.compute(key, (ignored, current) -> current == null
                || !current.owner.equals(worker.getUUID()) || !current.definition.equals(def.id())
                ? new State(def.id(), worker.getUUID(), now) : current);
        WorkstationV2Def.Attendance attendance = def.attendance();
        if (now - state.startedAt > attendance.timeoutTicks()) {
            state.diagnostic = diagnostic(state, Phase.TIMED_OUT, null, 0, now,
                    "attendance_timeout");
            runLifecycle(level, worker, pos, def, attendance.safeStop(), "safe_stop");
            return false;
        }
        if (now < state.nextPoll) return true;
        state.nextPoll = now + attendance.pollInterval();

        for (WorkstationV2Def.Attendance.Incident incident : attendance.incidents()) {
            BlockPos observed = pos;
            if (incident.target() != null) {
                var targets = DataDrivenStationAdapter.targetResolution(level, pos, def);
                var role = targets.role(incident.target());
                if (role.size() != 1) {
                    state.diagnostic = diagnostic(state, Phase.INCIDENT_BLOCKED, incident.id(), 0,
                            now, "incident_target_unavailable:" + incident.target());
                    return false;
                }
                observed = role.get(0);
            }
            if (!incident.when().test(level, observed)) continue;
            int attempts = state.attempts.getOrDefault(incident.id(), 0);
            if (attempts >= incident.maxAttempts()) {
                state.diagnostic = diagnostic(state, Phase.INCIDENT_BLOCKED, incident.id(), attempts,
                        now, "incident_attempt_cap");
                return false;
            }
            attempts++;
            state.attempts.put(incident.id(), attempts);
            boolean succeeded = DataDrivenStationAdapter.executeProtocolActions(
                    level, worker, pos, def, incident.response());
            if (succeeded) {
                state.solved++;
                state.attempts.remove(incident.id());
                state.diagnostic = diagnostic(state, Phase.RESPONDED, incident.id(), attempts, now,
                        "native_response_observed");
                return true;
            }
            state.diagnostic = diagnostic(state, Phase.INCIDENT_BLOCKED, incident.id(), attempts, now,
                    "native_response_refused");
            return false;
        }
        state.diagnostic = diagnostic(state, Phase.OBSERVING, null, 0, now, "no_incident_due");
        return true;
    }

    /** Safe interruption/reload boundary: only authored real interactions run; no machine data is forged. */
    public static void end(ServerLevel level, UUID owner, BlockPos pos) {
        if (level == null || owner == null || pos == null) return;
        String key = key(level, pos);
        State state = ACTIVE.get(key);
        if (state == null || !state.owner.equals(owner)) return;
        WorkstationV2Def def = Workstations.v2ByState(level.getBlockState(pos));
        net.minecraft.world.entity.Entity entity = level.getEntity(owner);
        if (def == null || def.attendance() == null || !(entity instanceof VillagerEntityMCA worker)) {
            ACTIVE.remove(key, state);
            return;
        }
        long now = level.getGameTime();
        runLifecycle(level, worker, pos, def, def.attendance().safeStop(), "safe_stop");
        state.diagnostic = diagnostic(state, Phase.SAFE_STOPPED, null, 0, now, "safe_stop_complete");
        runLifecycle(level, worker, pos, def, def.attendance().cleanup(), "cleanup");
        state.diagnostic = diagnostic(state, Phase.CLEANED, null, 0, now, "cleanup_complete");
        ACTIVE.remove(key, state);
    }

    public static @Nullable Diagnostic diagnostic(ServerLevel level, BlockPos pos) {
        State state = level == null || pos == null ? null : ACTIVE.get(key(level, pos));
        return state == null ? null : state.diagnostic;
    }

    static boolean mayAttempt(int attempts, int maxAttempts) {
        return attempts >= 0 && maxAttempts >= 1 && attempts < maxAttempts;
    }

    private static void runLifecycle(ServerLevel level, VillagerEntityMCA worker, BlockPos pos,
                                     WorkstationV2Def def, @Nullable com.google.gson.JsonElement actions,
                                     String stage) {
        if (actions != null && !DataDrivenStationAdapter.executeProtocolActions(
                level, worker, pos, def, actions)) {
            org.slf4j.LoggerFactory.getLogger("townstead/AttendedStationProtocols")
                    .warn("Attended protocol {} {} refused at {}", def.id(), stage, pos);
        }
    }

    private static Diagnostic diagnostic(State state, Phase phase, @Nullable String incident,
                                         int attempts, long now, String detail) {
        return new Diagnostic(state.definition, state.owner, phase, incident, attempts,
                state.solved, state.startedAt, now, detail);
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }
}
