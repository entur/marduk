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

import java.time.LocalDateTime;

/**
 * Metadata for prevalidated NeTEx files stored in last-prevalidated-files/.
 * This metadata is stored alongside the NeTEx file to track the original upload timestamp
 * for use during nightly validation runs.
 */
public class PrevalidatedFileMetadata {

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("originalFileName")
    private String originalFileName;

    public PrevalidatedFileMetadata() {
    }

    public PrevalidatedFileMetadata(LocalDateTime createdAt, String originalFileName) {
        this.createdAt = createdAt;
        this.originalFileName = originalFileName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }
}
