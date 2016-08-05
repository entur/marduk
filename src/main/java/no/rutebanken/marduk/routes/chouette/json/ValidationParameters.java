package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ValidationParameters extends ChouetteJobParameters{

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("validate")
        public Validate validate;

    }
/*
 *     "validate": {
        "name": "manual",
        "references_type": "",
        "reference_ids": "",
        "user_name": "Rutebanken admin",
        "organisation_name": "Rutebanken",
        "referential_name": "Hedmark / Hedmark-Trafikk"
    },

 * */
    static class Validate {

        public String name;

        @JsonProperty("user_name")
        public String userName;

        @JsonProperty("organisation_name")
        public String organisationName;

        @JsonProperty("referential_name")
        public String referentialName;

        @JsonProperty("references_type")
        @JsonInclude(JsonInclude.Include.ALWAYS)
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
