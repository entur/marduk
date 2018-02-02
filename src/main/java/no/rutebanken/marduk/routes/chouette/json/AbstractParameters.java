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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AbstractParameters {

    @JsonProperty("name")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String name;

    @JsonProperty("user_name")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String userName;

    @JsonProperty("organisation_name")
    public String organisationName;

    @JsonProperty("referential_name")
    public String referentialName;

    @JsonProperty("test")
    public boolean test = false;

}
