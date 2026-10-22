package com.aetherianartificer.townstead.compat.otectus;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrimeGateCheckTest {
    /** The shape of MCA: Crime's gate the mixin hooks. */
    static final class MatchingGate {
        public static Optional<ServerPlayer> resolveOffender(LivingEntity victim, DamageSource source,
                ServerLevel level, boolean lethal) {
            return Optional.empty();
        }

        public static Optional<ServerPlayer> resolveNonDamageOffender(LivingEntity victim, Entity actor,
                ServerLevel level) {
            return Optional.empty();
        }
    }

    /** The gate after Crime changes a parameter. */
    static final class ReshapedGate {
        public static Optional<ServerPlayer> resolveOffender(LivingEntity victim, DamageSource source,
                ServerLevel level) {
            return Optional.empty();
        }

        public Optional<ServerPlayer> resolveNonDamageOffender(LivingEntity victim, Entity actor, ServerLevel level) {
            return Optional.empty();
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @interface FakeMerged {
        String mixin();
    }

    static final class MergedGate {
        @FakeMerged(mixin = CrimeGateCheck.MIXIN)
        private static void handler() {}
    }

    static final class OtherMergedGate {
        @FakeMerged(mixin = "some.other.Mixin")
        private static void handler() {}
    }

    @Test
    void namesOurMixinClass() {
        assertNotNull(getClass().getClassLoader().getResource(CrimeGateCheck.MIXIN.replace('.', '/') + ".class"));
    }

    @Test
    void seesOnlyOurMixinsMark() {
        assertTrue(CrimeGateCheck.merged(MergedGate.class, FakeMerged.class.getName(), CrimeGateCheck.MIXIN));
        assertFalse(CrimeGateCheck.merged(OtherMergedGate.class, FakeMerged.class.getName(), CrimeGateCheck.MIXIN));
        assertFalse(CrimeGateCheck.merged(MatchingGate.class, FakeMerged.class.getName(), CrimeGateCheck.MIXIN));
    }

    @Test
    void findsTheMethodsTheMixinTargets() {
        assertTrue(CrimeGateCheck.hasStatic(MatchingGate.class, CrimeGateCheck.OFFENDER));
        assertTrue(CrimeGateCheck.hasStatic(MatchingGate.class, CrimeGateCheck.NON_DAMAGE_OFFENDER));
    }

    @Test
    void missesAReshapedOrNonStaticMethod() {
        assertFalse(CrimeGateCheck.hasStatic(ReshapedGate.class, CrimeGateCheck.OFFENDER));
        assertFalse(CrimeGateCheck.hasStatic(ReshapedGate.class, CrimeGateCheck.NON_DAMAGE_OFFENDER));
    }
}
