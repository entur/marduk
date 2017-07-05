package no.rutebanken.marduk.geocoder.nabu;

import com.vividsolutions.jts.geom.CoordinateList;
import no.rutebanken.marduk.geocoder.nabu.rest.AdministrativeZone;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceAdapter;
import no.rutebanken.marduk.geocoder.netex.geojson.GeoJsonSingleTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.sosi.SosiTopographicPlaceAdapterReader;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.security.TokenService;
import no.rutebanken.marduk.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.http.common.HttpOperationFailedException;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.wololo.geojson.Polygon;
import org.wololo.jts2geojson.GeoJSONWriter;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;

/**
 * Update admin units in organisation registry. Not part of the geocoder, but placed here as it reuses most of the code from the corresponding Tiamat route.
 */
@Component
public class OrganisationRegisteryAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {


    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${tiamat.countries.geojson.blobstore.subdirectory:geojson/countries}")
    private String blobStoreSubdirectoryCountries;

    @Value("${nabu.rest.service.url}")
    private String organisationRegistryUrl;


    @Value("${tiamat.administrative.units.update.directory:files/orgReg/adminUnits}")
    private String localWorkingDirectory;

    @Value("${kartverket.admin.units.archive.filename:Grensedata_Norge_UTM33_Adm_enheter_SOSI.zip}")
    private String adminUnitsArchiveFileName;

    @Value("${kartverket.admin.units.filename:ADM_enheter_Norge.sos}")
    private String adminUnitsFileName;


    @Value("${organisation.registry.admin.zone.code.space.id:rb}")
    private String adminZoneCodeSpaceId;
    @Value("${organisation.registry.admin.zone.code.space.xmlns:RB}")
    private String adminZoneCodeSpaceXmlns;

    private GeoJSONWriter geoJSONWriter = new GeoJSONWriter();

    @Autowired
    private TokenService tokenService;

    @Autowired
    private BlobStoreService blobStoreService;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:updateAdminUnitsInOrgReg")
                .log(LoggingLevel.INFO, "Starting update of administrative units in Organisation registry")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")

                .to("direct:fetchNeighbouringCountriesForOrgReg")
                .to("direct:updateNeighbouringCountriesInOrgReg")

                .to("direct:fetchAdministrativeUnitsForOrgReg")
                .to("direct:updateAdministrativeUnitsInOrgReg")

                .log(LoggingLevel.INFO, "Finished update of administrative units in Organisation registry")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()
                .routeId("organisation-registry-update-administrative-units");


        from("direct:fetchAdministrativeUnitsForOrgReg")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest administrative units ...")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits/" + adminUnitsArchiveFileName))
                .to("direct:getBlob")
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .routeId("organisation-registry-fetch-admin-units-sosi");

        from("direct:fetchNeighbouringCountriesForOrgReg")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest neighbouring countries ...")

                .process(e -> e.getIn().setBody(blobStoreService.listBlobsInFolder(blobStoreSubdirectoryCountries, e).getFiles().stream().filter(f -> f.getName().endsWith("geojson")).collect(Collectors.toList())))
                .split().body()
                .setHeader(FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(no.rutebanken.marduk.domain.BlobStoreFiles.File.class).getName()).getFileName()))
                .to("direct:getBlob")
                .to("file:" + localWorkingDirectory)
                .routeId("organisation-registry-fetch-neighbouring-countries-geojson");


        from("direct:updateNeighbouringCountriesInOrgReg")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest neighbouring countries to org reg format ...")
                .process(e -> e.getIn().setBody(new GeoJsonSingleTopographicPlaceReader(getGeojsonCountryFiles()).read().stream().map(tpa -> toAdministrativeZone(tpa)).collect(Collectors.toList())))
                .to("direct:updateAdministrativeZonesInOrgReg")
                .routeId("organisation-registry-update-neighbouring-countries");

        from("direct:updateAdministrativeUnitsInOrgReg")
                .process(e -> e.getIn().setBody(new SosiTopographicPlaceAdapterReader(new File(localWorkingDirectory + "/" + adminUnitsFileName)).read().stream().map(tpa -> toAdministrativeZone(tpa)).collect(Collectors.toList())))
                .to("direct:updateAdministrativeZonesInOrgReg")
                .routeId("organisation-registry-update-admin-units");


        from("direct:updateAdministrativeZonesInOrgReg")
                .split().body()
                .setHeader("privateCode", simple("${body.privateCode}"))
                .marshal().json(JsonLibrary.Jackson)
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_JSON))
                .process(e -> e.getIn().setHeader("Authorization", "Bearer " + tokenService.getToken()))

                .doTry()
                .toD(getOrganisationRegistryUrl() + "administrative_zones")

                .doCatch(HttpOperationFailedException.class).onWhen(exchange -> {
            HttpOperationFailedException ex = exchange.getException(HttpOperationFailedException.class);
            return (ex.getStatusCode() == HttpStatus.CONFLICT.value());
        })  // Update if zone already exists
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.PUT))
                .toD(getOrganisationRegistryUrl() + "administrative_zones/" + adminZoneCodeSpaceXmlns + ":AdministrativeZone:${header.privateCode}")
                .end()

                .routeId("organisation-registry-map-to-admin-zones");


    }


    private String getOrganisationRegistryUrl() {
        return organisationRegistryUrl.replaceFirst("https", "https4").replaceFirst("http", "http4");
    }

    private File[] getGeojsonCountryFiles() {
        return FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"geojson"}, false).stream().toArray(File[]::new);
    }

    private AdministrativeZone toAdministrativeZone(TopographicPlaceAdapter topographicPlaceAdapter) {

        com.vividsolutions.jts.geom.Geometry geometry = topographicPlaceAdapter.getDefaultGeometry();

        if (geometry instanceof com.vividsolutions.jts.geom.MultiPolygon) {
            CoordinateList coordinateList = new CoordinateList(geometry.getBoundary().getCoordinates());
            coordinateList.closeRing();
            geometry = geometry.getFactory().createPolygon(coordinateList.toCoordinateArray());
        }

        Polygon geoJsonPolygon = (Polygon) geoJSONWriter.write(geometry);
        AdministrativeZone administrativeZone = new AdministrativeZone(adminZoneCodeSpaceId, topographicPlaceAdapter.getId(), topographicPlaceAdapter.getName(), geoJsonPolygon, toType(topographicPlaceAdapter.getType()));
        return administrativeZone;
    }


    private AdministrativeZone.AdministrativeZoneType toType(TopographicPlaceAdapter.Type type) {
        switch (type) {
            case COUNTRY:
                return AdministrativeZone.AdministrativeZoneType.COUNTRY;
            case COUNTY:
                return AdministrativeZone.AdministrativeZoneType.COUNTY;
            case LOCALITY:
                return AdministrativeZone.AdministrativeZoneType.LOCALITY;
        }

        return AdministrativeZone.AdministrativeZoneType.CUSTOM;
    }
}
