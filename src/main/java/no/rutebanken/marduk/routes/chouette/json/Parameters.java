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

package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectWriter;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.json.ObjectMapperFactory;
import no.rutebanken.marduk.routes.chouette.json.exporter.GtfsExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.NetexExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.TransferExportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NetexImportParameters;
import no.rutebanken.marduk.routes.file.FileType;

import java.io.IOException;

public class Parameters {

    private static final ObjectWriter OBJECT_WRITER_FOR_GTFS_EXPORT_PARAMETERS = ObjectMapperFactory.getSharedObjectMapper().writerFor(GtfsExportParameters.class);
    private static final ObjectWriter OBJECT_WRITER_FOR_TRANSFER_EXPORT_PARAMETERS = ObjectMapperFactory.getSharedObjectMapper().writerFor(TransferExportParameters.class);
    private static final ObjectWriter OBJECT_WRITER_FOR_NETEX_EXPORT_PARAMETERS = ObjectMapperFactory.getSharedObjectMapper().writerFor(NetexExportParameters.class);

    public static String createImportParameters(String fileName, String fileType, Provider provider, boolean enablePreValidation, boolean allowUpdatingStopPlace) {
        if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParameters(fileName, provider);
        } else if (FileType.NETEXPROFILE.name().equals(fileType)) {
            return getNetexImportParameters(fileName, provider, enablePreValidation, allowUpdatingStopPlace);
        } else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    static String getGtfsImportParameters(String importName, Provider provider) {
        ChouetteInfo chouetteInfo = provider.getChouetteInfo();
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(importName, chouetteInfo.getXmlns(),
                chouetteInfo.getReferential(), chouetteInfo.getOrganisation(), chouetteInfo.getUser(), true,
                true, chouetteInfo.isAllowCreateMissingStopPlace(), false, chouetteInfo.getGenerateMissingServiceLinksForModes());
        return gtfsImportParameters.toJsonString();
    }

    static String getNetexImportParameters(String importName, Provider provider, boolean enablePreValidation, boolean allowUpdatingStopPlace) {
        ChouetteInfo chouetteInfo = provider.getChouetteInfo();
        NetexImportParameters netexImportParameters = NetexImportParameters.create(importName, provider.getName(),
                chouetteInfo.getOrganisation(), chouetteInfo.getUser(), true, true,
                chouetteInfo.isAllowCreateMissingStopPlace(), false, chouetteInfo.getXmlns(), chouetteInfo.getGenerateMissingServiceLinksForModes(), enablePreValidation, enablePreValidation, allowUpdatingStopPlace);
        return netexImportParameters.toJsonString();
    }

    public static String getGtfsExportParameters(Provider provider) {
        try {
            ChouetteInfo chouetteInfo = provider.getChouetteInfo();
            GtfsExportParameters.GtfsExport gtfsExport = new GtfsExportParameters.GtfsExport("for journey planning",
                    chouetteInfo.getXmlns(), chouetteInfo.getReferential(), chouetteInfo.getOrganisation(), chouetteInfo.getUser(), true);
            GtfsExportParameters.Parameters parameters = new GtfsExportParameters.Parameters(gtfsExport);
            GtfsExportParameters importParameters = new GtfsExportParameters(parameters);
            return OBJECT_WRITER_FOR_GTFS_EXPORT_PARAMETERS.writeValueAsString(importParameters);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    public static String getDefaultNetexExportParameters(Provider provider, boolean exportStops, boolean validateAfterExport) {
        return getNetexExportParameters(provider, exportStops, false, "for journey planning", validateAfterExport);
    }

    public static String getNetexBlocksExportParameters(Provider provider, boolean exportStops, boolean validateAfterExport) {
        return getNetexExportParameters(provider, exportStops, true, "with blocks", validateAfterExport);
    }

    private static String getNetexExportParameters(Provider provider, boolean exportStops, boolean exportBlocks, String name, boolean validateAfterExport) {
        try {
            ChouetteInfo chouetteInfo = provider.getChouetteInfo();
            String projectionType = null;
            NetexExportParameters.NetexExport netexExport = new NetexExportParameters.NetexExport(
                    name,
                    chouetteInfo.getReferential(),
                    chouetteInfo.getOrganisation(),
                    chouetteInfo.getUser(),
                    projectionType,
                    exportStops,
                    exportBlocks,
                    chouetteInfo.getXmlns(),
                    validateAfterExport);
            NetexExportParameters.Parameters parameters = new NetexExportParameters.Parameters(netexExport);
            NetexExportParameters exportParameters = new NetexExportParameters(parameters);
            return OBJECT_WRITER_FOR_NETEX_EXPORT_PARAMETERS.writeValueAsString(exportParameters);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    public static String getTransferExportParameters(Provider provider, Provider destProvider) {
        try {
            TransferExportParameters.TransferExport transferExport = new TransferExportParameters.TransferExport("data transfer",
                    provider.getName(), provider.getChouetteInfo().getOrganisation(), provider.getChouetteInfo().getUser(), destProvider.getChouetteInfo().getReferential());
            TransferExportParameters.Parameters parameters = new TransferExportParameters.Parameters(transferExport);
            TransferExportParameters importParameters = new TransferExportParameters(parameters);
            return OBJECT_WRITER_FOR_TRANSFER_EXPORT_PARAMETERS.writeValueAsString(importParameters);
        } catch (IOException e) {
            throw new MardukException(e);
        }
    }

    public static String getValidationParameters(Provider provider) {
        ChouetteInfo chouetteInfo = provider.getChouetteInfo();

        ValidationParameters validationParameters = ValidationParameters.create("Automatisk",
                chouetteInfo.getReferential(), chouetteInfo.getOrganisation(), chouetteInfo.getUser());
        validationParameters.enableValidation = true;
        return validationParameters.toJsonString();
    }


}
