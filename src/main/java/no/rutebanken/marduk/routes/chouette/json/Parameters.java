package no.rutebanken.marduk.routes.chouette.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.chouette.json.exporter.GtfsExportParameters;
import no.rutebanken.marduk.routes.chouette.json.exporter.NeptuneExportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.GtfsImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NeptuneImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.NetexImportParameters;
import no.rutebanken.marduk.routes.chouette.json.importer.RegtoppImportParameters;
import no.rutebanken.marduk.routes.file.FileType;

import java.io.IOException;
import java.io.StringWriter;

public class Parameters {

    public static String createImportParameters(String fileName, String fileType, boolean cleanRepository, Provider provider) {
        if (FileType.REGTOPP.name().equals(fileType)){
            return getRegtoppImportParameters(fileName, provider, cleanRepository);
        } else if (FileType.GTFS.name().equals(fileType)) {
            return getGtfsImportParameters(fileName, provider, cleanRepository);
        } else if (FileType.NETEXPROFILE.name().equals(fileType)) {
            return getNetexImportParameters(fileName, provider, cleanRepository);
        } else if (FileType.NEPTUNE.name().equals(fileType)) {
            return getNeptuneImportParameters(fileName, provider, cleanRepository);
        } else {
            throw new IllegalArgumentException("Cannot create import parameters from file type '" + fileType + "'");
        }
    }

    static String getRegtoppImportParameters(String importName, Provider provider, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        if (!chouetteInfo.usesRegtopp()){
            throw new IllegalArgumentException("Could not get regtopp information about provider '" + provider.id + "'.");
        }
        RegtoppImportParameters regtoppImportParameters = RegtoppImportParameters.create(importName, chouetteInfo.prefix,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user, chouetteInfo.regtoppVersion,
                chouetteInfo.regtoppCoordinateProjection,chouetteInfo.regtoppCalendarStrategy,cleanRepository,chouetteInfo.enableValidation,false,true);
        return regtoppImportParameters.toJsonString();
    }

    static String getGtfsImportParameters(String importName, Provider provider, boolean cleanRepository) {
        ChouetteInfo chouetteInfo = provider.chouetteInfo;
        GtfsImportParameters gtfsImportParameters = GtfsImportParameters.create(importName, chouetteInfo.prefix,
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user,cleanRepository,chouetteInfo.enableValidation);
        return gtfsImportParameters.toJsonString();
    }

    static String getNetexImportParameters(String importName, Provider provider, boolean cleanRepository) {
        NetexImportParameters netexImportParameters = NetexImportParameters.create(importName, provider.name,
                provider.chouetteInfo.organisation, provider.chouetteInfo.user, cleanRepository, provider.chouetteInfo.enableValidation);
        return netexImportParameters.toJsonString();
    }

    public static String getGtfsExportParameters(Provider provider) {
        try {
            ChouetteInfo chouetteInfo = provider.chouetteInfo;
            GtfsExportParameters.GtfsExport gtfsExport = new GtfsExportParameters.GtfsExport("export",
                    chouetteInfo.prefix, chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user,true);
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

    public static String getNeptuneImportParameters(String importName, Provider provider, boolean cleanRepository) {
        NeptuneImportParameters neptuneImportParameters = NeptuneImportParameters.create(importName,
                provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user,
                cleanRepository, provider.chouetteInfo.enableValidation);
        return neptuneImportParameters.toJsonString();
    }

    public static String getNeptuneExportParameters(Provider provider) {
        try {
            NeptuneExportParameters.NeptuneExport gtfsExport = new NeptuneExportParameters.NeptuneExport("export",
                    provider.name, provider.chouetteInfo.organisation, provider.chouetteInfo.user);
            NeptuneExportParameters.Parameters parameters = new NeptuneExportParameters.Parameters(gtfsExport);
            NeptuneExportParameters importParameters = new NeptuneExportParameters(parameters);
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
