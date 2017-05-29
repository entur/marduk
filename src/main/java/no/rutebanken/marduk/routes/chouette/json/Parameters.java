package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.exporter.GtfsExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.NetexExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.TransferExportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NetexImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RegtoppImportParameters;
import no.rutebanken.marduk.routes.file.FileType;

import java.io.IOException;
import java.io.StringWriter;

public class Parameters {

    public static String createImportParameters(String fileName, String fileType, boolean cleanRepository, Provider provider) {
        if (FileType.REGTOPP.name().equals(fileType)) {
            return getRegtoppImportParameters(fileName, provider, cleanRepository);
        } else if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParameters(fileName, provider, cleanRepository);
        } else if (FileType.NETEXPROFILE.name().equals(fileType)) {
            return getNetexImportParameters(fileName, provider, cleanRepository);
        } else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    static String getRegtoppImportParameters(String importName, Provider provider, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        if (!chouetteInfo.usesRegtopp()) {
            throw new IllegalArgumentException("Could not get regtopp information about provider '" + provider.id + "'.");
        }
        RegtoppImportParameters regtoppImportParameters = RegtoppImportParameters.create(importName, chouetteInfo.xmlns,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.regtoppVersion,
                chouetteInfo.regtoppCoordinateProjection, chouetteInfo.regtoppCalendarStrategy, cleanRepository, chouetteInfo.enableValidation, chouetteInfo.enableStopPlaceUpdate,false, true);
        return regtoppImportParameters.toJsonString();
    }

    static String getGtfsImportParameters(String importName, Provider provider, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(importName, chouetteInfo.xmlns,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, cleanRepository, chouetteInfo.enableValidation, chouetteInfo.enableStopPlaceUpdate);
        return gtfsImportParameters.toJsonString();
    }

    static String getNetexImportParameters(String importName, Provider provider, boolean cleanRepository) {
        NetexImportParameters netexImportParameters = NetexImportParameters.create(importName, provider.name,
                provider.chouetteInfo.organisation, provider.chouetteInfo.user, cleanRepository, provider.chouetteInfo.enableValidation, provider.chouetteInfo.enableStopPlaceUpdate, provider.chouetteInfo.xmlns, provider.chouetteInfo.xmlnsurl);
        return netexImportParameters.toJsonString();
    }

    public static String getGtfsExportParameters(Provider provider) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            GtfsExportParameters.GtfsExport gtfsExport = new GtfsExportParameters.GtfsExport("for journey planning",
                                                                                                    chouetteInfo.xmlns, chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, true);
            GtfsExportParameters.Parameters parameters = new GtfsExportParameters.Parameters(gtfsExport);
            GtfsExportParameters importParameters = new GtfsExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getNetexExportProvider(Provider provider, boolean exportStops) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            String projectionType = null;
            String validCodespaces = Joiner.on(",").join(chouetteInfo.xmlns, chouetteInfo.xmlnsurl);
            NetexExportParameters.NetexExport netexExport = new NetexExportParameters.NetexExport("for journey planning", chouetteInfo.referential, chouetteInfo.organisation, validCodespaces, chouetteInfo.user, projectionType, exportStops);
            NetexExportParameters.Parameters parameters = new NetexExportParameters.Parameters(netexExport);
            NetexExportParameters exportParameters = new NetexExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, exportParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getTransferExportParameters(Provider provider, Provider destProvider) {
        try {
            TransferExportParameters.TransferExport transferExport = new TransferExportParameters.TransferExport("data transfer",
                                                                                                                        provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user, destProvider.chouetteInfo.referential);
            TransferExportParameters.Parameters parameters = new TransferExportParameters.Parameters(transferExport);
            TransferExportParameters importParameters = new TransferExportParameters(parameters);
            ObjectMapper mapper = new ObjectMapper();
            StringWriter writer = new StringWriter();
            mapper.writeValue(writer, importParameters);
            return writer.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getValidationParameters(Provider provider) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;

        ValidationParameters validationParameters = ValidationParameters.create("Automatisk",
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user);
        validationParameters.enableValidation = true;
        return validationParameters.toJsonString();
    }


}
