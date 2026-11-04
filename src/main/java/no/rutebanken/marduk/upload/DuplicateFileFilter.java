package no.rutebanken.marduk.upload;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.repository.IdempotentRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER;
import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY;
import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;

/**
 * Rejects a file that has been uploaded before.
 *
 * <p>The real guard is the unique index behind {@link IdempotentRepository}: two pods uploading the same
 * file at the same time both reach the insert, and exactly one wins. This class decides what to compare on
 * and what to report.
 *
 * <p>Replaces {@code IdempotentFileFilterRoute} and Camel's {@code idempotentConsumer}.
 */
@Component
public class DuplicateFileFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateFileFilter.class);

    private final IdempotentRepository repository;
    private final JobEventPublisher jobEvents;

    public DuplicateFileFilter(IdempotentRepository fileNameAndDigestIdempotentRepository,
                               JobEventPublisher jobEvents) {
        this.repository = fileNameAndDigestIdempotentRepository;
        this.jobEvents = jobEvents;
    }

    /**
     * The outcome of claiming a file name.
     *
     * @param key       what was written to the repository, or {@code null} when the filter did not apply
     * @param duplicate whether this file has been seen before, in which case the caller must stop
     */
    public record Claim(String key, boolean duplicate) {

        static Claim notFiltered() {
            return new Claim(null, false);
        }
    }

    /**
     * Registers the file as seen.
     *
     * <p>A duplicate is reported as a failed file transfer here rather than by the caller, because that
     * report is the only trace a rejected upload leaves.
     */
    public Claim claim(MardukMessage message, String fileName, Path content) {
        if (!Boolean.TRUE.equals(message.getHeader(FILE_APPLY_DUPLICATES_FILTER, Boolean.class))) {
            return Claim.notFiltered();
        }
        LOGGER.info("Checking duplicate on file {}", fileName);
        String key = new FileNameAndDigest(fileName, digest(message, fileName, content)).toString();
        if (repository.add(key)) {
            return new Claim(key, false);
        }
        LOGGER.info("Detected {} as duplicate.", fileName);
        reportDuplicate(message);
        return new Claim(key, true);
    }

    /**
     * Takes the registration back because the work that followed it failed.
     *
     * <p>Without this a failed upload would block the same file from ever being retried. Only a claim this
     * caller actually created is removed: a duplicate's key belongs to the earlier upload that still holds
     * it, so releasing it would let the duplicate through on the next attempt.
     */
    public void release(Claim claim) {
        if (claim.key() == null || claim.duplicate()) {
            return;
        }
        if (repository.remove(claim.key())) {
            LOGGER.info("Removed from repository as the upload failed: {}", claim.key());
        }
    }

    /**
     * On the name alone when asked, otherwise on the content too.
     *
     * <p>Name-only is what the web upload uses: an operator re-uploading a corrected file under the same
     * name means to replace it, and comparing the content would let both versions through. The
     * machine-to-machine API compares the content, so a client retrying the same request is idempotent.
     */
    private static String digest(MardukMessage message, String fileName, Path content) {
        if (Boolean.TRUE.equals(message.getHeader(FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY, Boolean.class))) {
            return DigestUtils.md5Hex(fileName.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream bytes = Files.newInputStream(content)) {
            return DigestUtils.md5Hex(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + fileName + " to check for a duplicate", e);
        }
    }

    /**
     * {@code FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES} is read but never set anywhere in marduk. Kept because
     * it is a documented header an upstream service could send, not because anything uses it today.
     */
    private void reportDuplicate(MardukMessage message) {
        if (Boolean.TRUE.equals(message.getHeader(FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES, Boolean.class))) {
            return;
        }
        message.setHeader(Constants.JOB_ERROR_CODE, JobEvent.JOB_ERROR_DUPLICATE_FILE);
        jobEvents.reportProviderJob(message, builder -> builder
                .timetableAction(JobEvent.TimetableAction.FILE_TRANSFER).state(JobEvent.State.FAILED));
    }
}
