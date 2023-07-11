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

public record TransferExportParameters(
        no.rutebanken.marduk.routes.chouette.json.exporter.TransferExportParameters.Parameters parameters) {

    public static class Parameters {

        @JsonProperty("transfer-export")
        public TransferExport transferExport;

        public Parameters(TransferExport transferExport) {
            this.transferExport = transferExport;
        }
    }

    public static class TransferExport extends AbstractExportParameters {


        @JsonProperty("dest_referential_name")
        public String destReferentialName;


        public TransferExport(String name, String referentialName, String organisationName, String userName, String destReferentialName) {
            this.name = name;
            this.referentialName = referentialName;
            this.organisationName = organisationName;
            this.userName = userName;
            this.startDate = DateUtils.startDateFor(2L);
            this.endDate = DateUtils.endDateFor(365L);
            this.destReferentialName = destReferentialName;
        }

    }

}
