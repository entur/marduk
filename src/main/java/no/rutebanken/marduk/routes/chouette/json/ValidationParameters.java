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

import com.fasterxml.jackson.annotation.JsonProperty;

public class ValidationParameters extends ChouetteJobParameters{

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("validate")
        public Validate validate;

    }

    /**
     * JSON mapping for validation parameters.
     * JSON Structure:
     * "validate": {
     *         "name": "manual",
     *         "references_type": "",
     *         "reference_ids": "",
     *         "user_name": "Rutebanken admin",
     *         "organisation_name": "Rutebanken",
     *         "referential_name": "Hedmark / Hedmark-Trafikk"
     *         }
     */
    static class Validate {

        public String name;

        @JsonProperty("user_name")
        public String userName;

        @JsonProperty("organisation_name")
        public String organisationName;

        @JsonProperty("referential_name")
        public String referentialName;

        @JsonProperty("references_type")
        public String referencesType = "";


    }

    public static ValidationParameters create(String name, String referentialName, String organisationName, String userName) {
        Validate validate = new Validate();
        validate.name = name;

        validate.referentialName = referentialName;
        validate.organisationName = organisationName;
        validate.userName = userName;

        Parameters parameters = new Parameters();
        parameters.validate = validate;
        ValidationParameters validateParameters = new ValidationParameters();
        validateParameters.parameters = parameters;

        return validateParameters;
    }

}
