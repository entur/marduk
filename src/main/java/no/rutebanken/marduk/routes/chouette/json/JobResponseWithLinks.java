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
public class JobResponseWithLinks extends JobResponse{

 

	public List<LinkInfo> links;
//    @JsonProperty("action_parameters")
//    public ActionParameters actionParameters;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LinkInfo {
        public String rel;
        public String href;
//        public String type;
//        public String method;
    }

//    public static class ActionParameters {
//
//        public String name;
//
//        @JsonProperty("no_save")
//        public boolean noSave;
//
//        @JsonProperty("user_name")
//        public String userName;
//
//        @JsonProperty("organisation_name")
//        public String organisationName;
//
//        @JsonProperty("referential_name")
//        public String referentialName;
//
//        @JsonProperty("object_id_prefix")
//        public String objectIdPrefix;
//
//        @JsonProperty("max_distance_for_commercial")
//        public Integer maxDistanceForCommercial;
//
//        @JsonProperty("ignore_last_word")
//        public boolean ignoreLastWord;
//
//        @JsonProperty("ignore_end_chars")
//        public Integer ignoreEndChars;
//
//        @JsonProperty("max_distance_for_connection_link")
//        @JsonInclude(JsonInclude.Include.ALWAYS)
//        public Integer maxDistanceForConnectionLink;
//
//        @JsonProperty("references_type")
//        @JsonInclude(JsonInclude.Include.ALWAYS)
//        public String referencesType = "";
//
//        @JsonProperty("clean_repository")
//        public boolean cleanRepository;
//
//    }

}
