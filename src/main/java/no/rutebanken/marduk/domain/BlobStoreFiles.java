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
		public  enum Format { NETEX,GTFS,GRAPH, UNKOWN}
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

		public Date getCreated() {
			return created;
		}

		public void setCreated(Date created) {
			this.created = created;
		}

		public String getReferential() {
			return referential;
		}

		public void setReferential(String referential) {
			this.referential = referential;
		}

		public Format getFormat() {
			return format;
		}

		public void setFormat(Format format) {
			this.format = format;
		}

		public String getUrl() {
			return url;
		}

		public void setUrl(String url) {
			this.url = url;
		}

		public Long getProviderId() {
			return providerId;
		}

		public void setProviderId(Long providerId) {
			this.providerId = providerId;
		}

		public File(String name, Date created, Date updated, Long fileSize) {
			super();
			this.name = name;
			this.created = created;
			this.updated = updated;
			this.fileSize = fileSize;
		}

		@JsonProperty(required = true)
		private String name;

		@JsonProperty(required = false)
		private Date created;

		@JsonProperty(required = false)
		private Date updated;

		@JsonProperty(required = false)
		private Long fileSize;

		@JsonProperty(required = false)
		private String referential;

		@JsonProperty(required = false)
		private Long providerId;

		@JsonProperty(required = false)
		private Format format;

		@JsonProperty(required = false)
		private String url;

		@Override
		public String toString() {
			return "File [name=" + name + ", created=" + created + ", updated=" + updated + ", fileSize=" + fileSize + "]";
		}

}
}
