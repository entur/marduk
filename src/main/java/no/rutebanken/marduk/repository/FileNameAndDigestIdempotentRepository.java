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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Idempotent repository treating both file name and digest as unique keys, so a re-upload of the same
 * timetable file is rejected whether the name or the content matches.
 * <p>
 * The key is JSON because Camel's idempotent consumer required a String; the format is kept because the
 * rows are persistent state shared with every previous version of marduk.
 * <p>
 * This used to extend Camel's {@code AbstractJdbcMessageIdRepository}. The transaction handling below is
 * that class's, reproduced: one {@code PROPAGATION_REQUIRED} transaction per operation over a
 * {@code DataSourceTransactionManager} of its own, at the database's default isolation.
 * <p>
 * Note what actually makes {@code add} safe. The read-then-write inside it is not atomic at read-committed,
 * so two pods can both see no row and both insert. The primary key on
 * {@code (processorname, filename)} and the unique index on {@code (processorname, digest)} are the real
 * guard: the loser's insert fails and the exception propagates rather than being reported as "already
 * present". That is the behaviour the callers have always seen - the exchange fails, the message is
 * redelivered, and the retry finds the row and reports a duplicate.
 */
public class FileNameAndDigestIdempotentRepository implements IdempotentRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileNameAndDigestIdempotentRepository.class);

    // The table name predates this class and is persistent state; renaming it would need a migration and
    // would break a rollback to any earlier version.
    private static final String QUERY_STRING = "SELECT COUNT(*) FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND (digest = ? or fileName=?)";
    private static final String INSERT_STRING = "INSERT INTO CAMEL_UNIQUE_FILENAME_AND_DIGEST (processorName, digest,fileName, createdAt) VALUES (?,?, ?, ?)";
    private static final String DELETE_STRING = "DELETE FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND digest = ? and fileName=? and createdAt >= ?";
    private static final String CLEAR_STRING = "DELETE FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ?";
    private static final String SELECT_CREATED_AT_BY_FILE_QUERY = "SELECT createdAt FROM CAMEL_UNIQUE_FILENAME_AND_DIGEST WHERE processorName = ? AND fileName = ?";

    protected final JdbcTemplate jdbcTemplate;
    protected final String processorName;
    private final TransactionTemplate transactionTemplate;

    /**
     * Max no of seconds transactions may last and still be cleaned up if it fails.
     * <p>
     * If set to a positive number only entries created within the given number of seconds will be removed if the
     * work fails, to avoid removing entries that the current unit of work did not add.
     */
    private final int maxTransactionSeconds;

    public FileNameAndDigestIdempotentRepository(DataSource dataSource, String processorName, int maxTransactionSeconds) {
        this(newJdbcTemplate(dataSource), newTransactionTemplate(dataSource), processorName, maxTransactionSeconds);
    }

    public FileNameAndDigestIdempotentRepository(DataSource dataSource, String processorName) {
        this(dataSource, processorName, -1);
    }

    /**
     * Takes both templates directly, so a test can drive the queries against a double. Camel's base class
     * offered the same constructor.
     */
    public FileNameAndDigestIdempotentRepository(
            JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, String processorName, int maxTransactionSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.processorName = processorName;
        this.maxTransactionSeconds = maxTransactionSeconds;
    }

    private static JdbcTemplate newJdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.afterPropertiesSet();
        return jdbcTemplate;
    }

    private static TransactionTemplate newTransactionTemplate(DataSource dataSource) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        return transactionTemplate;
    }

    @Override
    public boolean add(String key) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status ->
                queryForInt(key) == 0 && insert(key) != 0));
    }

    @Override
    public boolean contains(String key) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> queryForInt(key) > 0));
    }

    @Override
    public boolean remove(String key) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> delete(key) > 0));
    }

    @Override
    public void clear() {
        transactionTemplate.execute(status -> delete());
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
