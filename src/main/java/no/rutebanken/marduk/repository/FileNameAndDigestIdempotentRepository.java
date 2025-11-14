/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 *
 */

package no.rutebanken.marduk.repository;


import no.rutebanken.marduk.domain.FileNameAndDigest;
import no.rutebanken.marduk.exceptions.MardukException;
import org.apache.camel.processor.idempotent.jdbc.AbstractJdbcMessageIdRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Custom impl of JDBC messageId repo considering both file name and digest as unique keys.
 * <p>
 * MessageId format is JSON because camel forces conversion to String.
 */
public class FileNameAndDigestIdempotentRepository extends AbstractJdbcMessageIdRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileNameAndDigestIdempotentRepository.class);

    private static final String QUERY_STRING = "SELECT COUNT(*) FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND (digest = ? or fileName=?)";
    private static final String INSERT_STRING = "INSERT INTO CAMEL_UNIQUE_FILENAME_AND_DIGEST (processorName, digest,fileName, createdAt) VALUES (?,?, ?, ?)";
    private static final String DELETE_STRING = "DELETE FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND digest = ? and fileName=? and createdAt >= ?";
    private static final String CLEAR_STRING = "DELETE FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ?";
    private static final String SELECT_CREATED_AT_BY_FILE_QUERY = "SELECT createdAt FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND fileName = ?";

    /**
     * Max no of seconds transactions may last and still be cleaned up if it fails.
     * <p>
     * If set to a positive number only entries created within the given number of seconds will be removed if exchange fails. To avoid entries not added by the current exchange being removed upon failure.
     */
    private final int maxTransactionSeconds;

    public FileNameAndDigestIdempotentRepository(DataSource dataSource, String processorName, int maxTransactionSeconds) {
        super(dataSource, processorName);
        this.maxTransactionSeconds = maxTransactionSeconds;
    }

    public FileNameAndDigestIdempotentRepository(DataSource dataSource, String processorName) {
        this(dataSource, processorName, -1);
    }

    public LocalDateTime getCreatedAt(String fileName) {
        try {
            LOGGER.info("Running query to get created timestamp for file {}", fileName);
            return this.jdbcTemplate.queryForObject(SELECT_CREATED_AT_BY_FILE_QUERY, LocalDateTime.class, this.processorName, fileName);
        } catch (EmptyResultDataAccessException e) {
            LOGGER.warn("No createdAt timestamp found for file {}", fileName);
            return null;
        } catch (Exception e) {
            throw new MardukException("An unexpected error occured while getting createdAt timestamp for file " + fileName, e);
        }
    }

    protected int queryForInt(String keyAsString) {
        FileNameAndDigest key = FileNameAndDigest.fromString(keyAsString);
        return this.jdbcTemplate.queryForObject(QUERY_STRING, Integer.class, this.processorName, key.getDigest(), key.getFileName());
    }

    protected int insert(String keyAsString) {
        return insert(keyAsString, Instant.now());
    }

    protected int insert(String keyAsString, Instant instant) {
        FileNameAndDigest key = FileNameAndDigest.fromString(keyAsString);
        return this.jdbcTemplate.update(INSERT_STRING, this.processorName, key.getDigest(), key.getFileName(), Timestamp.from(instant));
    }

    protected int delete(String keyAsString) {
        FileNameAndDigest key = FileNameAndDigest.fromString(keyAsString);
        Instant minCreatedAt;
        if (maxTransactionSeconds > 0) {
            minCreatedAt = Instant.now().minus(maxTransactionSeconds, ChronoUnit.SECONDS);
        } else {
            minCreatedAt = Instant.ofEpochMilli(0L);
        }
        return this.jdbcTemplate.update(DELETE_STRING, this.processorName, key.getDigest(), key.getFileName(), Timestamp.from(minCreatedAt));
    }

    protected int delete() {
        return this.jdbcTemplate.update(CLEAR_STRING, this.processorName);
    }

}
