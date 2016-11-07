package no.rutebanken.marduk.routes.chouette.json.importer;

import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;

public class AbstractImportParameters extends AbstractParameters {

    @JsonProperty("clean_repository")
    public String cleanRepository = "0";

    @JsonProperty("no_save")
    public String noSave = "0";

    @JsonProperty("update_stop_places")
    public boolean updateStopPlaces = true;

}
