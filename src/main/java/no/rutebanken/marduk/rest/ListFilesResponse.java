package no.rutebanken.marduk.rest;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ListFilesResponse {

	@JsonProperty("files")
	private List<File> files = new ArrayList<File>();

	public void add(File filename) {
		files.add(filename);
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

		private String name;

		private Date updated;

		@Override
		public String toString() {
			return "File [name=" + name + ", updated=" + updated + ", fileSize=" + fileSize + "]";
		}

		private Long fileSize;
	}
}
