package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AbstractImportParameters {

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

    @JsonProperty("references_type")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String referencesType = "";
}
