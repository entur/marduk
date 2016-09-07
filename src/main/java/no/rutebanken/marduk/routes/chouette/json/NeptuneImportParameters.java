package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NeptuneImportParameters extends ChouetteJobParameters{

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("neptune-import")
        public NeptuneImport neptuneImport;

    }

    static class NeptuneImport {

        public String name;

        @JsonProperty("clean_repository")
        public String cleanRepository = "0";

        @JsonProperty("no_save")
        public String noSave = "0";

        @JsonProperty("user_name")
        public String userName;

        @JsonProperty("organisation_name")
        public String organisationName;

        @JsonProperty("referential_name")
        public String referentialName;

        @JsonProperty("object_id_prefix")
        public String objectIdPrefix;

    }

    public static NeptuneImportParameters create(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, boolean cleanRepository,boolean enableValidation) {
        NeptuneImport neptuneImport = new NeptuneImport();
        neptuneImport.name = name;
        neptuneImport.objectIdPrefix = objectIdPrefix;
        neptuneImport.referentialName = referentialName;
        neptuneImport.organisationName = organisationName;
        neptuneImport.userName = userName;
        neptuneImport.cleanRepository = cleanRepository ? "1":"0";
        Parameters parameters = new Parameters();
        parameters.neptuneImport = neptuneImport;
        NeptuneImportParameters neptuneImportParameters = new NeptuneImportParameters();
        neptuneImportParameters.parameters = parameters;
        neptuneImportParameters.enableValidation = enableValidation;
        return neptuneImportParameters;
    }

}
