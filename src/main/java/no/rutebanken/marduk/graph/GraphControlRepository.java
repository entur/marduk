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
import java.util.Optional;

/**
 * Persistent state of the emergency controls of the OTP graph publication.
 * The methods must be called within a transaction, after {@link #lockControlRow()} for the operations that modify the
 * state.
 */
@Repository
public class GraphControlRepository {

    public enum GraphType {
        TRANSIT, BASE
    }

    /**
     * How long a caller waits for the control row lock: the graph builder hooks wait for a control operation to
     * complete, while a control operation requested over HTTP gives up quickly, so that the request is answered
     * (with 409) before the gateway times out.
     */
    public enum LockWait {
        BUILD, OPERATION
    }

    public record PauseState(boolean paused, String pausedBy, Instant pausedAt, String reason) {
    }

    /**
     * @param productionSerializationId the serialization version used in production, that is the serialization version
     *                                  of the latest graph built by the production graph builder, null until the first
     *                                  production build
     */
    public record ControlState(boolean paused, String pausedBy, Instant pausedAt, String pauseReason, String productionSerializationId) {

        public PauseState pauseState() {
            return new PauseState(paused, pausedBy, pausedAt, pauseReason);
        }
    }

    /**
     * The current graph of a serialization version.
     *
     * @param candidate whether the graph was promoted by a candidate build
     */
    public record CurrentGraph(String serializationId, String graphFile, boolean candidate, String updatedBy, Instant updatedAt) {
    }

    public record GraphPin(String serializationId, String graphFile, String pinnedBy, Instant pinnedAt, String reason) {
    }

    public record DeferredBuildRequest(long id, GraphType graphType, String pubsubMessageId, String attributes, Instant receivedAt) {
    }

    public record AuditEntry(String action, String serializationId, String graphFile, String performedBy, Instant performedAt, String reason) {
    }

    private static final RowMapper<ControlState> CONTROL_STATE_MAPPER = (rs, rowNum) -> new ControlState(
            rs.getBoolean("paused"),
            rs.getString("paused_by"),
            toInstant(rs, "paused_at"),
            rs.getString("pause_reason"),
            rs.getString("production_serialization_id"));

    private static final RowMapper<CurrentGraph> CURRENT_GRAPH_MAPPER = (rs, rowNum) -> new CurrentGraph(
            rs.getString("serialization_id"),
            rs.getString("graph_file"),
            rs.getBoolean("candidate"),
            rs.getString("updated_by"),
            toInstant(rs, "updated_at"));

    private static final RowMapper<GraphPin> PIN_MAPPER = (rs, rowNum) -> new GraphPin(
            rs.getString("serialization_id"),
            rs.getString("graph_file"),
            rs.getString("pinned_by"),
            toInstant(rs, "pinned_at"),
            rs.getString("reason"));

    private static final RowMapper<DeferredBuildRequest> DEFERRED_BUILD_REQUEST_MAPPER = (rs, rowNum) -> new DeferredBuildRequest(
            rs.getLong("id"),
            GraphType.valueOf(rs.getString("graph_type")),
            rs.getString("pubsub_message_id"),
            rs.getString("attributes"),
            toInstant(rs, "received_at"));

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
     * @return the control state
     * @throws org.springframework.dao.QueryTimeoutException if the lock is not obtained within the wait of the caller
     */
    public ControlState lockControlRow(LockWait lockWait) {
        return lockingJdbcTemplates.get(lockWait).queryForObject(
                "SELECT paused, paused_by, paused_at, pause_reason, production_serialization_id FROM otp_graph_control WHERE id = 1 FOR UPDATE",
                CONTROL_STATE_MAPPER);
    }

    public ControlState getControlState() {
        return jdbcTemplate.queryForObject(
                "SELECT paused, paused_by, paused_at, pause_reason, production_serialization_id FROM otp_graph_control WHERE id = 1",
                CONTROL_STATE_MAPPER);
    }

    public void updateProductionSerializationId(String serializationId) {
        jdbcTemplate.update("UPDATE otp_graph_control SET production_serialization_id = ? WHERE id = 1", serializationId);
    }

    public Optional<CurrentGraph> findCurrentGraph(String serializationId) {
        return jdbcTemplate.query("SELECT serialization_id, graph_file, candidate, updated_by, updated_at FROM otp_graph_current WHERE serialization_id = ?",
                CURRENT_GRAPH_MAPPER, serializationId).stream().findFirst();
    }

    public List<CurrentGraph> findCurrentGraphs() {
        return jdbcTemplate.query("SELECT serialization_id, graph_file, candidate, updated_by, updated_at FROM otp_graph_current ORDER BY serialization_id",
                CURRENT_GRAPH_MAPPER);
    }

    public void saveCurrentGraph(CurrentGraph currentGraph) {
        jdbcTemplate.update("DELETE FROM otp_graph_current WHERE serialization_id = ?", currentGraph.serializationId());
        jdbcTemplate.update("INSERT INTO otp_graph_current (serialization_id, graph_file, candidate, updated_by, updated_at) VALUES (?, ?, ?, ?, ?)",
                currentGraph.serializationId(), currentGraph.graphFile(), currentGraph.candidate(), currentGraph.updatedBy(), toOffsetDateTime(currentGraph.updatedAt()));
    }

    public void updatePauseState(PauseState pauseState) {
        jdbcTemplate.update("UPDATE otp_graph_control SET paused = ?, paused_by = ?, paused_at = ?, pause_reason = ? WHERE id = 1",
                pauseState.paused(), pauseState.pausedBy(), toOffsetDateTime(pauseState.pausedAt()), pauseState.reason());
    }

    public List<GraphPin> findPins() {
        return jdbcTemplate.query("SELECT serialization_id, graph_file, pinned_by, pinned_at, reason FROM otp_graph_pin ORDER BY serialization_id",
                PIN_MAPPER);
    }

    public Optional<GraphPin> findPin(String serializationId) {
        return jdbcTemplate.query("SELECT serialization_id, graph_file, pinned_by, pinned_at, reason FROM otp_graph_pin WHERE serialization_id = ?",
                PIN_MAPPER, serializationId).stream().findFirst();
    }

    public void savePin(GraphPin pin) {
        deletePin(pin.serializationId());
        jdbcTemplate.update("INSERT INTO otp_graph_pin (serialization_id, graph_file, pinned_by, pinned_at, reason) VALUES (?, ?, ?, ?, ?)",
                pin.serializationId(), pin.graphFile(), pin.pinnedBy(), toOffsetDateTime(pin.pinnedAt()), pin.reason());
    }

    /**
     * @return true if a pin was removed
     */
    public boolean deletePin(String serializationId) {
        return jdbcTemplate.update("DELETE FROM otp_graph_pin WHERE serialization_id = ?", serializationId) > 0;
    }

    public boolean deferredBuildRequestExists(String pubsubMessageId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM otp_graph_deferred_build_request WHERE pubsub_message_id = ?",
                Integer.class, pubsubMessageId);
        return count != null && count > 0;
    }

    public void insertDeferredBuildRequest(GraphType graphType, String pubsubMessageId, String attributes, Instant receivedAt) {
        jdbcTemplate.update("INSERT INTO otp_graph_deferred_build_request (graph_type, pubsub_message_id, attributes, received_at) VALUES (?, ?, ?, ?)",
                graphType.name(), pubsubMessageId, attributes, toOffsetDateTime(receivedAt));
    }

    public List<DeferredBuildRequest> findDeferredBuildRequests() {
        return jdbcTemplate.query("SELECT id, graph_type, pubsub_message_id, attributes, received_at FROM otp_graph_deferred_build_request ORDER BY id",
                DEFERRED_BUILD_REQUEST_MAPPER);
    }

    public int countDeferredBuildRequests(GraphType graphType) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM otp_graph_deferred_build_request WHERE graph_type = ?",
                Integer.class, graphType.name());
        return count == null ? 0 : count;
    }

    public void deleteDeferredBuildRequest(long id) {
        jdbcTemplate.update("DELETE FROM otp_graph_deferred_build_request WHERE id = ?", id);
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
