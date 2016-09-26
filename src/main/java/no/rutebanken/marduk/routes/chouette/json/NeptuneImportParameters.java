package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NeptuneImportParameters extends ChouetteJobParameters{

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("neptune-import")
        public NeptuneImport neptuneImport;

    }

    static class NeptuneImport extends AbstractImportParameters { }

    public static NeptuneImportParameters create(String name,String referentialName, String organisationName, String userName, boolean cleanRepository,boolean enableValidation) {
        NeptuneImport neptuneImport = new NeptuneImport();
        neptuneImport.name = name;
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
