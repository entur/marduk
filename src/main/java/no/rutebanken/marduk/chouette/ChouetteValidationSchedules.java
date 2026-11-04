package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.leader.LeaderElection;
import no.rutebanken.marduk.pipeline.MardukMdc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The two nightly validation sweeps.
 *
 * <p>Replaces the {@code quartz://marduk/nightlyValidation} and {@code quartz://marduk/chouetteValidateLevel2}
 * triggers. Leader-gated, because a sweep started by every pod would submit the same jobs several times.
 *
 * <p>As with the Chouette job cleanup, the {@code autoStartup} flags gate the schedule and nothing else: the
 * admin endpoints still trigger either sweep on demand.
 */
@Component
public class ChouetteValidationSchedules {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChouetteValidationSchedules.class);

    private final ChouetteValidationTriggers triggers;
    private final LeaderElection leaderElection;
    private final boolean antuValidationScheduled;
    private final boolean level2ValidationScheduled;

    public ChouetteValidationSchedules(
            ChouetteValidationTriggers triggers,
            LeaderElection leaderElection,
            @Value("${antu.validate.autoStartup:true}") boolean antuValidationScheduled,
            @Value("${chouette.validate.level2.autoStartup:false}") boolean level2ValidationScheduled) {
        this.triggers = triggers;
        this.leaderElection = leaderElection;
        this.antuValidationScheduled = antuValidationScheduled;
        this.level2ValidationScheduled = level2ValidationScheduled;
    }

    @Scheduled(cron = "${antu.validate.cron:0 30 23 ? * MON-FRI}", zone = "Europe/Oslo")
    void triggerAntuValidationOnSchedule() {
        if (!shouldRun(antuValidationScheduled, "antu validation")) {
            return;
        }
        LOGGER.info("Triggering validation in antu for all providers in Chouette.");
        run(triggers::triggerAntuValidationForAllProviders);
    }

    @Scheduled(cron = "${chouette.validate.level2.cron:0 30 21 ? * MON-FRI}", zone = "Europe/Oslo")
    void validateLevel2OnSchedule() {
        if (!shouldRun(level2ValidationScheduled, "Chouette level 2 validation")) {
            return;
        }
        LOGGER.info("Triggering validation of Level2 for all providers in Chouette.");
        run(triggers::validateLevel2ForAllProviders);
    }

    private boolean shouldRun(boolean scheduled, String what) {
        if (!scheduled) {
            LOGGER.debug("The scheduled {} is switched off", what);
            return false;
        }
        if (!leaderElection.isLeader()) {
            LOGGER.debug("Not the leader, skipping the scheduled {}", what);
            return false;
        }
        return true;
    }

    private void run(Runnable sweep) {
        MardukMdc.clear();
        MardukMdc.setCorrelationId(UUID.randomUUID().toString());
        try {
            sweep.run();
        } finally {
            MardukMdc.clear();
        }
    }
}
