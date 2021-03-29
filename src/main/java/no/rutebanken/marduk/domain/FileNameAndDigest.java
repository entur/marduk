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

package no.rutebanken.marduk.domain;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;

public class FileNameAndDigest {

	private static final ObjectReader OBJECT_READER = ObjectMapperFactory.getObjectMapper().readerFor(FileNameAndDigest.class);
	private static final ObjectWriter OBJECT_WRITER = ObjectMapperFactory.getObjectMapper().writerFor(FileNameAndDigest.class);

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
			return OBJECT_READER.readValue(string);
		} catch (IOException e) {
			throw new MardukException(e);
		}
	}

	public String toString() {
		try {
			StringWriter writer = new StringWriter();
			OBJECT_WRITER.writeValue(writer, this);
			return writer.toString();
		} catch (IOException e) {
			throw new MardukException(e);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FileNameAndDigest that = (FileNameAndDigest) o;

		if (!Objects.equals(fileName, that.fileName)) return false;
		return Objects.equals(digest, that.digest);
	}

	@Override
	public int hashCode() {
		int result = fileName != null ? fileName.hashCode() : 0;
		result = 31 * result + (digest != null ? digest.hashCode() : 0);
		return result;
	}
}
