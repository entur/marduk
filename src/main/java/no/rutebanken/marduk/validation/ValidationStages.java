package no.rutebanken.marduk.validation;

import no.rutebanken.marduk.routes.status.JobEvent;

import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_FLEX_POSTVALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_NIGHTLY_VALIDATION;
import static no.rutebanken.marduk.Constants.VALIDATION_STAGE_PREVALIDATION;

/** The job each antu validation stage reports against, on the request side as well as the status side. */
public final class ValidationStages {

    private ValidationStages() {
    }

    /**
     * Two pairs of stages share an action: a FLEX post-validation is reported as the ordinary NeTEx
     * post-validation, and a nightly validation as a pre-validation, which is what it is from the operator's
     * point of view.
     *
     * @return null for a stage this version does not know
     */
    public static JobEvent.TimetableAction actionFor(String stage) {
        return switch (stage) {
            case VALIDATION_STAGE_PREVALIDATION, VALIDATION_STAGE_NIGHTLY_VALIDATION ->
                    JobEvent.TimetableAction.PREVALIDATION;
            case VALIDATION_STAGE_EXPORT_NETEX_POSTVALIDATION, VALIDATION_STAGE_FLEX_POSTVALIDATION ->
                    JobEvent.TimetableAction.EXPORT_NETEX_POSTVALIDATION;
            case VALIDATION_STAGE_EXPORT_NETEX_BLOCKS_POSTVALIDATION ->
                    JobEvent.TimetableAction.EXPORT_NETEX_BLOCKS_POSTVALIDATION;
            case VALIDATION_STAGE_EXPORT_MERGED_POSTVALIDATION ->
                    JobEvent.TimetableAction.EXPORT_NETEX_MERGED_POSTVALIDATION;
            case null, default -> null;
        };
    }
}
