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

package no.rutebanken.marduk.graph;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Database side of the emergency controls of the OTP graph publication: the lock serializing the graph promotions and
 * the control operations across the cluster, and the audit trail. The state of the publication itself lives in the
 * graph bucket (see {@link Otp2GraphStateStore}).
 * The methods must be called within a transaction.
 */
@Repository
public class GraphControlRepository {

    /**
     * How long a caller waits for the control row lock: the graph builder waits for a control operation to complete,
     * while a control operation requested over HTTP gives up quickly, so that the request is answered (with 409)
     * before the gateway times out.
     */
    public enum LockWait {
        BUILD, OPERATION
    }

    public record AuditEntry(String action, String serializationId, String graphFile, String performedBy, Instant performedAt, String reason) {
    }

    private static final RowMapper<AuditEntry> AUDIT_ENTRY_MAPPER = (rs, rowNum) -> new AuditEntry(
            rs.getString("action"),
            rs.getString("serialization_id"),
            rs.getString("graph_file"),
            rs.getString("performed_by"),
            toInstant(rs, "performed_at"),
            rs.getString("reason"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * Templates used for locking the control row, with a query timeout so that a caller does not wait forever for a
     * lock held by a long operation. They share the data source, and thus the transaction, of the main template.
     */
    private final Map<LockWait, JdbcTemplate> lockingJdbcTemplates = new EnumMap<>(LockWait.class);

    public GraphControlRepository(DataSource dataSource,
                                  @Value("${otp2.graph.control.lock.timeout.seconds:120}") int buildLockTimeoutSeconds,
                                  @Value("${otp2.graph.control.operation.lock.timeout.seconds:10}") int operationLockTimeoutSeconds) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        lockingJdbcTemplates.put(LockWait.BUILD, lockingJdbcTemplate(dataSource, buildLockTimeoutSeconds));
        lockingJdbcTemplates.put(LockWait.OPERATION, lockingJdbcTemplate(dataSource, operationLockTimeoutSeconds));
    }

    private static JdbcTemplate lockingJdbcTemplate(DataSource dataSource, int lockTimeoutSeconds) {
        JdbcTemplate template = new JdbcTemplate(dataSource);
        template.setQueryTimeout(lockTimeoutSeconds);
        return template;
    }

    /**
     * Lock the control row until the end of the current transaction, so that the graph promotions and the control
     * operations are applied one at a time across the cluster.
     *
     * @throws org.springframework.dao.QueryTimeoutException if the lock is not obtained within the wait of the caller
     */
    public void lockControlRow(LockWait lockWait) {
        lockingJdbcTemplates.get(lockWait).queryForObject("SELECT id FROM otp_graph_control WHERE id = 1 FOR UPDATE", Integer.class);
    }

    public void insertAuditEntry(AuditEntry entry) {
        jdbcTemplate.update("INSERT INTO otp_graph_control_audit (action, serialization_id, graph_file, performed_by, performed_at, reason) VALUES (?, ?, ?, ?, ?, ?)",
                entry.action(), entry.serializationId(), entry.graphFile(), entry.performedBy(), toOffsetDateTime(entry.performedAt()), entry.reason());
    }

    public List<AuditEntry> findLatestAuditEntries(String action, int limit) {
        return jdbcTemplate.query("SELECT action, serialization_id, graph_file, performed_by, performed_at, reason FROM otp_graph_control_audit WHERE action = ? ORDER BY performed_at DESC, id DESC FETCH FIRST ? ROWS ONLY",
                AUDIT_ENTRY_MAPPER, action, limit);
    }

    public List<AuditEntry> findLatestAuditEntriesExcluding(String excludedAction, int limit) {
        return jdbcTemplate.query("SELECT action, serialization_id, graph_file, performed_by, performed_at, reason FROM otp_graph_control_audit WHERE action <> ? ORDER BY performed_at DESC, id DESC FETCH FIRST ? ROWS ONLY",
                AUDIT_ENTRY_MAPPER, excludedAction, limit);
    }

    public int deleteAuditEntriesBefore(String action, Instant before) {
        return jdbcTemplate.update("DELETE FROM otp_graph_control_audit WHERE action = ? AND performed_at < ?", action, toOffsetDateTime(before));
    }

    private static Instant toInstant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
