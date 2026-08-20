package no.rutebanken.marduk.upload;

import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.pipeline.MardukMessage;
import no.rutebanken.marduk.pubsub.MardukQueues;
import no.rutebanken.marduk.pubsub.RecordingPubSubPublisher;
import no.rutebanken.marduk.repository.IdempotentRepository;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.routes.status.JobEventPublisher;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static no.rutebanken.marduk.Constants.CORRELATION_ID;
import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER;
import static no.rutebanken.marduk.Constants.FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY;
import static no.rutebanken.marduk.Constants.FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuplicateFileFilterTest {

    /**
     * An in-memory stand-in for the key store. Deliberately only compares whole keys: rejecting a file
     * whose <em>name or</em> digest was seen before is the real repository's job, and
     * {@code FileNameAndDigestIdempotentRepositoryTest} is where that is pinned. What this test is about is
     * which key the filter builds and what it does with the answer.
     */
    private static final class Keys implements IdempotentRepository {
        private final Set<String> keys = new HashSet<>();

        @Override
        public boolean add(String key) {
            return keys.add(key);
        }

        @Override
        public boolean contains(String key) {
            return keys.contains(key);
        }

        @Override
        public boolean remove(String key) {
            return keys.remove(key);
        }

        @Override
        public void clear() {
            keys.clear();
        }
    }

    @TempDir
    Path uploads;

    private Keys keys;
    private RecordingPubSubPublisher publisher;
    private DuplicateFileFilter filter;

    @BeforeEach
    void setUp() {
        keys = new Keys();
        publisher = new RecordingPubSubPublisher();
        filter = new DuplicateFileFilter(keys, new JobEventPublisher(publisher));
    }

    private static MardukMessage upload(boolean applyFilter) {
        return new MardukMessage()
                .setHeader(PROVIDER_ID, 2L)
                .setHeader(CORRELATION_ID, "corr")
                .setHeader(FILE_APPLY_DUPLICATES_FILTER, applyFilter);
    }

    private Path fileContaining(String content) throws IOException {
        Path file = Files.createTempFile(uploads, "upload-", ".zip");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private JobEvent reported() {
        return JobEvent.fromString(publisher.publishedTo(MardukQueues.JOB_EVENT_QUEUE).getLast().body());
    }

    @Test
    void aFileSeenForTheFirstTimeIsNotADuplicate() throws IOException {
        DuplicateFileFilter.Claim claim = filter.claim(upload(true), "netex.zip", fileContaining("one"));

        assertFalse(claim.duplicate());
        assertTrue(keys.contains(claim.key()));
    }

    @Test
    void theSameNameAndContentTwiceIsADuplicate() throws IOException {
        filter.claim(upload(true), "netex.zip", fileContaining("one"));

        assertTrue(filter.claim(upload(true), "netex.zip", fileContaining("one")).duplicate());
    }

    @Test
    void theKeyIsTheFileNameAndTheDigestOfItsContent() throws IOException {
        DuplicateFileFilter.Claim claim = filter.claim(upload(true), "netex.zip", fileContaining("one"));

        FileNameAndDigest key = FileNameAndDigest.fromString(claim.key());
        assertEquals("netex.zip", key.getFileName());
        assertEquals(DigestUtils.md5Hex("one"), key.getDigest());
    }

    @Test
    void nameOnlyComparisonNeverLooksAtTheContent() throws IOException {
        // The web upload uses this: an operator re-uploading a corrected file under the same name means to
        // replace it, and comparing the content would let both versions through.
        MardukMessage nameOnly = upload(true).setHeader(FILE_APPLY_DUPLICATES_FILTER_ON_NAME_ONLY, true);

        DuplicateFileFilter.Claim first = filter.claim(nameOnly, "netex.zip", fileContaining("one"));
        keys.clear();
        DuplicateFileFilter.Claim second = filter.claim(nameOnly, "netex.zip", fileContaining("other bytes"));

        assertEquals(first.key(), second.key());
        assertEquals(DigestUtils.md5Hex("netex.zip"), FileNameAndDigest.fromString(first.key()).getDigest());
    }

    @Test
    void aFilterThatIsSwitchedOffRegistersNothing() throws IOException {
        DuplicateFileFilter.Claim claim = filter.claim(upload(false), "netex.zip", fileContaining("one"));

        assertFalse(claim.duplicate());
        assertTrue(publisher.published().isEmpty());
        assertFalse(filter.claim(upload(false), "netex.zip", fileContaining("one")).duplicate(),
                "the same file was rejected even though the filter is off");
    }

    @Test
    void aDuplicateIsReportedAsAFailedFileTransfer() throws IOException {
        // That report is the only trace a rejected upload leaves.
        filter.claim(upload(true), "netex.zip", fileContaining("one"));
        publisher.clear();

        filter.claim(upload(true), "netex.zip", fileContaining("one"));

        JobEvent event = reported();
        assertEquals("FILE_TRANSFER", event.getAction());
        assertEquals(JobEvent.State.FAILED, event.getState());
        assertEquals(JobEvent.JOB_ERROR_DUPLICATE_FILE, event.getErrorCode());
    }

    @Test
    void theDuplicateReportCanBeSuppressed() throws IOException {
        filter.claim(upload(true), "netex.zip", fileContaining("one"));
        publisher.clear();

        MardukMessage quiet = upload(true).setHeader(FILE_SKIP_STATUS_UPDATE_FOR_DUPLICATES, true);
        assertTrue(filter.claim(quiet, "netex.zip", fileContaining("one")).duplicate());

        assertTrue(publisher.published().isEmpty());
    }

    @Test
    void releasingAClaimLetsTheFileBeUploadedAgain() throws IOException {
        // Without this a failed upload would block that file from ever being retried.
        DuplicateFileFilter.Claim claim = filter.claim(upload(true), "netex.zip", fileContaining("one"));

        filter.release(claim);

        assertFalse(filter.claim(upload(true), "netex.zip", fileContaining("one")).duplicate());
    }

    @Test
    void releasingADuplicateLeavesTheEarlierUploadsKeyAlone() throws IOException {
        // The key belongs to the upload that won; releasing it would let the duplicate through next time.
        filter.claim(upload(true), "netex.zip", fileContaining("one"));
        DuplicateFileFilter.Claim duplicate = filter.claim(upload(true), "netex.zip", fileContaining("one"));

        filter.release(duplicate);

        assertTrue(filter.claim(upload(true), "netex.zip", fileContaining("one")).duplicate());
    }

    @Test
    void releasingAClaimFromASwitchedOffFilterDoesNothing() throws IOException {
        DuplicateFileFilter.Claim claim = filter.claim(upload(false), "netex.zip", fileContaining("one"));

        filter.release(claim);

        assertEquals(0, publisher.published().size());
    }
}
