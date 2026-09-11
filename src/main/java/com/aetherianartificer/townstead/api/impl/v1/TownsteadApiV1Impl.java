package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.CalendarApi;
import com.aetherianartificer.townstead.api.v1.ChroniclesApi;
import com.aetherianartificer.townstead.api.v1.EventsApi;
import com.aetherianartificer.townstead.api.v1.HangoutsApi;
import com.aetherianartificer.townstead.api.v1.RegistriesApi;
import com.aetherianartificer.townstead.api.v1.SchedulesApi;
import com.aetherianartificer.townstead.api.v1.WorkApi;
import com.aetherianartificer.townstead.api.v1.ProfessionsApi;
import com.aetherianartificer.townstead.api.v1.SocialApi;
import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.VillagersApi;
import com.aetherianartificer.townstead.api.v1.VillagesApi;

/**
 * The live v1 implementation. Constructed reflectively by {@code TownsteadApiV1.get()}.
 *
 * <p>{@link #API_REVISION} increments on every additive change to {@code api.v1}; keep
 * {@code docs/API.md} in step.
 */
public final class TownsteadApiV1Impl implements TownsteadApiV1 {
    private static final int API_VERSION = 1;
    private static final int API_REVISION = 1;

    private final VillagersApi villagers = new VillagersImpl();
    private final VillagesApi villages = new VillagesImpl();
    private final ProfessionsApi professions = new ProfessionsImpl();
    private final CalendarApi calendar = new CalendarImpl();
    private final SocialApi social = new SocialImpl();
    private final ChroniclesApi chronicles = new ChroniclesImpl();
    private final EventsApi events = new EventsImpl();
    private final WorkApi work = new WorkImpl();
    private final HangoutsApi hangouts = new HangoutsImpl();
    private final SchedulesApi schedules = new SchedulesImpl();
    private final RegistriesApi registries = new RegistriesImpl();

    @Override
    public int getApiVersion() {
        return API_VERSION;
    }

    @Override
    public int getApiRevision() {
        return API_REVISION;
    }

    @Override
    public String getModVersion() {
        return ApiSupport.modVersion();
    }

    @Override
    public VillagersApi villagers() {
        return villagers;
    }

    @Override
    public VillagesApi villages() {
        return villages;
    }

    @Override
    public ProfessionsApi professions() {
        return professions;
    }

    @Override
    public CalendarApi calendar() {
        return calendar;
    }

    @Override
    public SocialApi social() {
        return social;
    }

    @Override
    public ChroniclesApi chronicles() {
        return chronicles;
    }

    @Override
    public EventsApi events() {
        return events;
    }

    @Override
    public WorkApi work() {
        return work;
    }

    @Override
    public HangoutsApi hangouts() {
        return hangouts;
    }

    @Override
    public SchedulesApi schedules() {
        return schedules;
    }

    @Override
    public RegistriesApi registries() {
        return registries;
    }
}
