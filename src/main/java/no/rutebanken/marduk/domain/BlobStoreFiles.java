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

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BlobStoreFiles {

    @JsonProperty("files")
    private List<File> files = new ArrayList<>();

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
        public enum Format {NETEX, GTFS, GRAPH, UNKOWN}

        public String getName() {
            return name;
        }

        public File() {
        }

        public void setName(String name) {
            this.name = name;
        }

        public void setUpdated(Instant updated) {
            this.updated = updated;
        }

        public void setFileSize(Long fileSize) {
            this.fileSize = fileSize;
        }

        public Instant getUpdated() {
            return updated;
        }

        public Long getFileSize() {
            return fileSize;
        }

        public Instant getCreated() {
            return created;
        }

        public void setCreated(Instant created) {
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

        public File(String name, Instant created, Instant updated, Long fileSize) {
            super();
            this.name = name;
            this.created = created;
            this.updated = updated;
            this.fileSize = fileSize;
        }

        @JsonProperty(required = true)
        private String name;

        @JsonProperty(required = false)
        // Clients (Ninkasi, Bel) expect the instant to be formatted as epoch milliseconds
        @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
        private Instant created;

        @JsonProperty(required = false)
        // Clients (Ninkasi, Bel) expect the instant to be formatted as epoch milliseconds
        @JsonFormat(without = JsonFormat.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
        private Instant updated;

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

        @JsonIgnore
        public String getFileNameOnly() {
            if (name == null || name.endsWith("/")) {
                return null;
            }

            return Paths.get(name).getFileName().toString();
        }

        @Override
        public String toString() {
            return "File [name=" + name + ", created=" + created + ", updated=" + updated + ", fileSize=" + fileSize + "]";
        }

    }
}
