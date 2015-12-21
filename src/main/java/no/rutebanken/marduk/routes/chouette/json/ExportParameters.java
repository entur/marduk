package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class ExportParameters {

    /*

    {
  "parameters": {
    "gtfs-export": {
      "name": "TestGTFSImport_1",
      "user_name": "Chouette",
      "organisation_name": "Rutebanken",
      "referential_name": "RuterDS",
      "references_type": "",
      "start_date": "2015-09-02T00:00:00Z",
      "end_date": "2015-12-07T00:00:00Z",
      "add_metadata": true,
      "object_id_prefix": "rds",
      "time_zone": "Europe\/Paris"
    }
  }
}

     */

    public Parameters parameters;

    public ExportParameters(Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("gtfs-export")
        public GtfsExport gtfsExport;

        public Parameters(GtfsExport gtfsExport) {
            this.gtfsExport = gtfsExport;
        }

    }

    public static class GtfsExport {

        public String name;

//        @JsonProperty("no_save")
//        public String noSave = "0";

        @JsonProperty("user_name")
        public String userName;

        @JsonProperty("organisation_name")
        public String organisationName;

        @JsonProperty("referential_name")
        public String referentialName;

        @JsonProperty("object_id_prefix")
        public String objectIdPrefix;

//        @JsonProperty("max_distance_for_commercial")
//        public String maxDistanceForCommercial = "0";
//
//        @JsonProperty("ignore_last_word")
//        public String ignoreLastWord = "0";
//
//        @JsonProperty("ignore_end_chars")
//        public String ignoreEndChars = "0";
//
//        @JsonProperty("max_distance_for_connection_link")
//        @JsonInclude(JsonInclude.Include.ALWAYS)
//        public String maxDistanceForConnectionLink = "0";

        @JsonProperty("references_type")
        @JsonInclude(JsonInclude.Include.ALWAYS)
        public String referencesType = "";

        @JsonProperty("add_metadata")
        public boolean addMetadata;// = true;

        @JsonProperty("time_zone")
        public String timeZone = "Europe/Oslo";  //  "time_zone": "Europe\/Paris"

        @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("start_date")
        public Date startDate;

        @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd'T'HH:mm:ss")
        @JsonProperty("end_date")
        public Date endDate;


        public GtfsExport(String name, String objectIdPrefix, String referentialName, String organisationName, String userName, Date startDate, Date endDate) {
            this.name = name;
            this.objectIdPrefix = objectIdPrefix;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.endDate = endDate;
            this.startDate = startDate;
        }

    }


}
