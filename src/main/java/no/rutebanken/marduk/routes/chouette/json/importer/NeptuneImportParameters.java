package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;
import no.rutebanken.marduk.routes.chouette.json.ChouetteJobParameters;

public class NeptuneImportParameters extends ChouetteJobParameters {

    public Parameters parameters;

	static class Parameters {

        @JsonProperty("neptune-import")
        public Neptune neptuneImport;

    }

    static class Neptune extends AbstractImportParameters { }

    public static NeptuneImportParameters create(String name, String referentialName, String organisationName, String userName, boolean cleanRepository,boolean enableValidation) {
        Neptune neptuneImport = new Neptune();
        neptuneImport.name = name;
        neptuneImport.referentialName = referentialName;
        neptuneImport.organisationName = organisationName;
        neptuneImport.userName = userName;
        neptuneImport.cleanRepository = cleanRepository ? "1":"0";
        neptuneImport.updateStopPlaces = false;
        Parameters parameters = new Parameters();
        parameters.neptuneImport = neptuneImport;
        NeptuneImportParameters neptuneImportParameters = new NeptuneImportParameters();
        neptuneImportParameters.parameters = parameters;
        neptuneImportParameters.enableValidation = enableValidation;
        return neptuneImportParameters;
    }

}
