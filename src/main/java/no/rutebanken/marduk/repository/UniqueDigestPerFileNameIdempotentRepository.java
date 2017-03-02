package no.rutebanken.marduk.repository;


import no.rutebanken.marduk.domain.FileNameAndDigest;
import org.apache.camel.processor.idempotent.jdbc.AbstractJdbcMessageIdRepository;

import javax.sql.DataSource;
import java.sql.Timestamp;

/**
 * Custom impl of JDBC messageId repo requiring unique digest per filename.
 *
 * MessageId format is JSON because camel forces conversion to String.
 */
public class UniqueDigestPerFileNameIdempotentRepository extends AbstractJdbcMessageIdRepository<String> {

	private String queryString = "SELECT COUNT(*) FROM CAMEL_UNIQUE_DIGEST_PER_FILENAME WHERE processorName = ? AND digest=? AND fileName=?";
	private String insertString = "INSERT INTO CAMEL_UNIQUE_DIGEST_PER_FILENAME (processorName, digest,fileName, createdAt) VALUES (?,?, ?, ?)";
	private String deleteString = "DELETE FROM CAMEL_UNIQUE_DIGEST_PER_FILENAME WHERE processorName = ? AND digest = ? and fileName=?";
	private String clearString = "DELETE FROM CAMEL_UNIQUE_DIGEST_PER_FILENAME WHERE processorName = ?";


	public UniqueDigestPerFileNameIdempotentRepository(DataSource dataSource, String processorName) {
		super(dataSource, processorName);
	}


	protected int queryForInt(String keyAsString) {
		FileNameAndDigest key=FileNameAndDigest.fromString(keyAsString);
		return ((Integer) this.jdbcTemplate.queryForObject(this.queryString, Integer.class, new Object[]{this.processorName, key.getDigest(), key.getFileName()})).intValue();
	}

	protected int insert(String keyAsString) {
		FileNameAndDigest key=FileNameAndDigest.fromString(keyAsString);
		return this.jdbcTemplate.update(this.insertString, new Object[]{this.processorName, key.getDigest(), key.getFileName(), new Timestamp(System.currentTimeMillis())});
	}

	protected int delete(String keyAsString) {
		FileNameAndDigest key=FileNameAndDigest.fromString(keyAsString);
		return this.jdbcTemplate.update(this.deleteString, new Object[]{this.processorName, key.getDigest(), key.getFileName()});
	}

	protected int delete() {
		return this.jdbcTemplate.update(this.clearString, new Object[]{this.processorName});
	}
}
