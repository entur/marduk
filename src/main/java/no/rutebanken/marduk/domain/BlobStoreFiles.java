package no.rutebanken.marduk.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BlobStoreFiles {

	@JsonProperty("files")
	private List<File> files = new ArrayList<File>();

	public void add(File file) {
		files.add(file);
	}

	public void add(Collection<File> files) {
		this.files.addAll(files);
	}

	public List<File> getFiles() {
		return files;
	}

	public static class File {
		public String getName() {
			return name;
		}

		public File() {
		}

		public void setName(String name) {
			this.name = name;
		}

		public void setUpdated(Date updated) {
			this.updated = updated;
		}

		public void setFileSize(Long fileSize) {
			this.fileSize = fileSize;
		}

		public Date getUpdated() {
			return updated;
		}

		public Long getFileSize() {
			return fileSize;
		}

		public File(String name, Date updated, Long fileSize) {
			super();
			this.name = name;
			this.updated = updated;
			this.fileSize = fileSize;
		}

		@JsonProperty(required = true)
		private String name;

		@JsonProperty(required = false)
		private Date updated;

		@JsonProperty(required = false)
		private Long fileSize;

		@Override
		public String toString() {
			return "File [name=" + name + ", updated=" + updated + ", fileSize=" + fileSize + "]";
		}

}
}
