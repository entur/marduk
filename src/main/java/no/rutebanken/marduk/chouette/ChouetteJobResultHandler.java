package no.rutebanken.marduk.chouette;

import no.rutebanken.marduk.pipeline.MardukMessage;

/**
 * Handles the outcome of a Chouette job once {@link ChouetteJobPoller} has seen it finish.
 *
 * <p>Which handler runs is decided by the {@code RutebankenChouetteJobStatusRoutingDestination} header the
 * submitting step set, so a poll request already on the queue when a pod restarts still reaches the same
 * place. That is why {@link #destination()} keeps the {@code direct:} spelling: the value is on the wire, and
 * pinned by {@code WireContractTest}. The steps not yet converted are still Camel routes under those names,
 * and the poller falls back to sending to them.
 */
public interface ChouetteJobResultHandler {

    /** The routing destination value this handler answers to, e.g. {@code direct:processImportResult}. */
    String destination();

    void handle(MardukMessage message);
}
