package no.rutebanken.marduk.pipeline;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.DATASET_REFERENTIAL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MardukMdcTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void bothKeysComeOffTheMessage() {
        MardukMdc.set(new MardukMessage()
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "rb_tst"));

        assertEquals("corr", MDC.get("correlationId"));
        assertEquals("rb_tst", MDC.get("codespace"));
    }

    @Test
    void theDatasetReferentialWinsOverTheChouetteReferential() {
        MardukMdc.set(new MardukMessage()
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(DATASET_REFERENTIAL, "TST")
                .setHeader(CHOUETTE_REFERENTIAL, "rb_tst"));

        assertEquals("TST", MDC.get("codespace"));
    }

    @Test
    void theChouetteReferentialIsUsedWhenThereIsNoDatasetReferential() {
        MardukMdc.set(new MardukMessage().setHeader(CHOUETTE_REFERENTIAL, "rb_tst"));

        assertEquals("rb_tst", MDC.get("codespace"));
    }

    @Test
    void anEmptyValueLeavesTheKeyUnsetRatherThanEmpty() {
        MardukMdc.set(new MardukMessage().setHeader(CORRELATION_ID, "").setHeader(DATASET_REFERENTIAL, ""));

        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("codespace"));
    }

    @Test
    void setReplacesWhateverThePooledThreadWasCarrying() {
        // The failure this prevents: a thread that keeps a previous job's correlation id labels this job's
        // log lines with it, which is the one thing that makes a rollout unreadable.
        MDC.put("correlationId", "previous");
        MDC.put("codespace", "rb_previous");

        MardukMdc.set(new MardukMessage().setHeader(CORRELATION_ID, "current"));

        assertEquals("current", MDC.get("correlationId"));
        assertNull(MDC.get("codespace"), "a stale codespace outlived a message that has none");
    }

    @Test
    void setCodespaceIfMissingDoesNotOverwrite() {
        MardukMdc.setCodespaceIfMissing("first");
        MardukMdc.setCodespaceIfMissing("second");

        assertEquals("first", MDC.get("codespace"));
    }

    @Test
    void withRestoresThePreviousContext() {
        MDC.put("correlationId", "outer");
        MDC.put("codespace", "rb_outer");

        MardukMdc.with(new MardukMessage()
                .setHeader(CORRELATION_ID, "inner")
                .setHeader(DATASET_REFERENTIAL, "rb_inner"), () -> {
            assertEquals("inner", MDC.get("correlationId"));
            assertEquals("rb_inner", MDC.get("codespace"));
        });

        assertEquals("outer", MDC.get("correlationId"));
        assertEquals("rb_outer", MDC.get("codespace"));
    }

    @Test
    void withRestoresThePreviousContextAfterAFailure() {
        MDC.put("correlationId", "outer");

        try {
            MardukMdc.with(new MardukMessage().setHeader(CORRELATION_ID, "inner"), () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException expected) {
            // the point of the test is what the MDC looks like afterwards
        }

        assertEquals("outer", MDC.get("correlationId"));
    }

    @Test
    void withLeavesNothingBehindWhenThereWasNothingBefore() {
        MardukMdc.with(new MardukMessage().setHeader(CORRELATION_ID, "inner"), () -> {
        });

        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("codespace"));
    }

    @Test
    void clearRemovesBothKeys() {
        MDC.put("correlationId", "corr");
        MDC.put("codespace", "rb_tst");

        MardukMdc.clear();

        assertNull(MDC.get("correlationId"));
        assertNull(MDC.get("codespace"));
    }
}
