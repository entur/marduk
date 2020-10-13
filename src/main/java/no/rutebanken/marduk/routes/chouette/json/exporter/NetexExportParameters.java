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

package no.rutebanken.marduk.routes.chouette.json.exporter;

import com.fasterxml.jackson.annotation.JsonProperty;

public class NetexExportParameters {

    public NetexExportParameters.Parameters parameters;

    public NetexExportParameters(NetexExportParameters.Parameters parameters) {
        this.parameters = parameters;
    }

    public static class Parameters {

        @JsonProperty("netexprofile-export")
        public NetexExport netexExport;

        public Parameters(NetexExport netexExport) {
            this.netexExport = netexExport;
        }

    }

    public static class NetexExport extends AbstractExportParameters {

        @JsonProperty("projection_type")
        public String projectionType;

        @JsonProperty("add_extension")
        private boolean addExtension = false;

        @JsonProperty("export_stops")
        public boolean exportStops = false;

        @JsonProperty("export_blocks")
        public boolean exportBlocks = false;

        @JsonProperty("default_codespace_prefix")
        public String defaultCodespacePrefix = null;

        public NetexExport(String name, String referentialName, String organisationName, String userName, String projectionType, boolean exportStops, boolean exportBlocks, String defaultCodespacePrefix) {
            this.name = name;
            this.projectionType = projectionType;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.startDate = DateUtils.startDateFor(2L);
			this.endDate = DateUtils.endDateFor(365L);
            this.exportStops = exportStops;
            this.exportBlocks = exportBlocks;
            this.defaultCodespacePrefix = defaultCodespacePrefix;
        }

    }
}
