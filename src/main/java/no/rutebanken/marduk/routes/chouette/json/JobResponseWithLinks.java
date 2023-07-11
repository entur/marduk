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

package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JobResponseWithLinks extends JobResponse {

    private List<LinkInfo> links;

    public List<LinkInfo> getLinks() {
        return links;
    }

    public JobResponseWithLinks setLinks(List<LinkInfo> links) {
        this.links = links;
        return this;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkInfo {
        private String rel;
        private String href;

        public String getRel() {
            return rel;
        }

        public LinkInfo setRel(String rel) {
            this.rel = rel;
            return this;
        }

        public String getHref() {
            return href;
        }

        public LinkInfo setHref(String href) {
            this.href = href;
            return this;
        }
    }
}
