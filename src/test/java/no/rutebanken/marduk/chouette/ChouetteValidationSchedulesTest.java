package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.leader.LeaderElection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChouetteValidationSchedulesTest {

    private final ChouetteValidationTriggers triggers = mock(ChouetteValidationTriggers.class);

    private ChouetteValidationSchedules schedules(boolean leader, boolean antuOn, boolean level2On) {
        return new ChouetteValidationSchedules(triggers, () -> leader, antuOn, level2On);
    }

    @Test
    void theLeaderRunsBothSweeps() {
        schedules(true, true, true).triggerAntuValidationOnSchedule();
        schedules(true, true, true).validateLevel2OnSchedule();

        verify(triggers).triggerAntuValidationForAllProviders();
        verify(triggers).validateLevel2ForAllProviders();
    }

    @Test
    void aFollowerRunsNeither() {
        // Every pod firing the sweep would submit the same Chouette jobs several times over.
        schedules(false, true, true).triggerAntuValidationOnSchedule();
        schedules(false, true, true).validateLevel2OnSchedule();

        verify(triggers, never()).triggerAntuValidationForAllProviders();
        verify(triggers, never()).validateLevel2ForAllProviders();
    }

    @Test
    void theAutoStartupFlagsSwitchOffTheirOwnSchedule() {
        schedules(true, false, true).triggerAntuValidationOnSchedule();
        schedules(true, true, false).validateLevel2OnSchedule();

        verify(triggers, never()).triggerAntuValidationForAllProviders();
        verify(triggers, never()).validateLevel2ForAllProviders();
    }

    @Test
    void aFailingSweepDoesNotLeaveItsCorrelationIdOnTheThread() {
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(triggers).validateLevel2ForAllProviders();

        try {
            schedules(true, true, true).validateLevel2OnSchedule();
        } catch (IllegalStateException expected) {
            // the scheduler's error handler logs it
        }

        assertEquals(null, org.slf4j.MDC.get("correlationId"));
    }
}
