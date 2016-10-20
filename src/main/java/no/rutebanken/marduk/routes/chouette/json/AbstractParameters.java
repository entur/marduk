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
