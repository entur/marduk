package no.rutebanken.marduk.routes.chouette.json.exporter;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import no.rutebanken.marduk.routes.chouette.json.AbstractParameters;

import java.util.Date;
import java.util.List;

public class AbstractExportParameters extends AbstractParameters {

    @JsonProperty("references_type")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public String referencesType;

    @JsonProperty("reference_ids")
    public List<Long> ids;

    @JsonProperty("start_date")
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd", timezone="CET")
    public Date startDate;

    @JsonProperty("end_date")
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd", timezone="CET")
    public Date endDate;

    @JsonProperty("add_metadata")
    public boolean addMetadata = true;

    @JsonProperty("validate_after_export")
    public boolean validateAfterExport = true;

}
