package no.rutebanken.marduk.domain;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;

public class FileNameAndDigest {

	@JsonProperty("fileName")
	private String fileName;

	@JsonProperty("digest")
	private String digest;

	public FileNameAndDigest() {
	}

	public FileNameAndDigest(String fileName, String digest) {
		this.fileName = fileName;
		this.digest = digest;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getDigest() {
		return digest;
	}

	public void setDigest(String digest) {
		this.digest = digest;
	}


	public static FileNameAndDigest fromString(String string) {
		try {
			ObjectMapper mapper = new ObjectMapper();
			return mapper.readValue(string, FileNameAndDigest.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public String toString() {
		try {
			ObjectMapper mapper = new ObjectMapper();
			StringWriter writer = new StringWriter();
			mapper.writeValue(writer, this);
			return writer.toString();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FileNameAndDigest that = (FileNameAndDigest) o;

		if (fileName != null ? !fileName.equals(that.fileName) : that.fileName != null) return false;
		return digest != null ? digest.equals(that.digest) : that.digest == null;
	}

	@Override
	public int hashCode() {
		int result = fileName != null ? fileName.hashCode() : 0;
		result = 31 * result + (digest != null ? digest.hashCode() : 0);
		return result;
	}
}
