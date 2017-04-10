package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.geocoder.featurejson.FeatureJSONFilter;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.geocoder.netex.kartverket.GeoJsonTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;
import static no.rutebanken.marduk.routes.status.SystemStatus.Entity.ADMINISTRATIVE_UNITS;
import static no.rutebanken.marduk.routes.status.SystemStatus.System.GC;
import static no.rutebanken.marduk.routes.status.SystemStatus.System.TIAMAT;

@Component
public class TiamatAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${tiamat.administrative.units.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/jersey/publication_delivery}")
    private String tiamatPublicationDeliveryPath;

    @Value("${kartverket.download.directory:files/kartverket}")
    private String localWorkingDirectory;

    @Value("${kartverket.admin.units.archive.filename:Grensedata_Norge_WGS84_Adm_enheter_geoJSON.zip}")
    private String adminUnitsArchiveFileName;

    @Value("#{'${kartverket.admin.units.geojson.filenames:fylker.geojson,kommuner.geojson}'.split(',')}")
    private List<String> adminUnitsFileNames;

    @Autowired
    private TopographicPlaceConverter topographicPlaceConverter;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/tiamatTAdministrativeUnitsUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.administrative.units.update.autoStartup:false}}")
                .log(LoggingLevel.INFO, "Quartz triggers Tiamat update of administrative units.")
                .setBody(constant(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START))
                .to("direct:geoCoderStart")
                .routeId("tiamat-admin-units-update-quartz");

        from(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Starting update of administrative units in Tiamat")
                .process(e -> SystemStatus.builder(e).start(GeoCoderTaskType.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE)
                                      .action(SystemStatus.Action.UPDATE).source(GC).target(TIAMAT)
                                      .entity(ADMINISTRATIVE_UNITS).build()).to("direct:updateSystemStatus")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchAdministrativeUnits")
                .to("direct:filterAdministrativeUnitsExclaves")
                .to("direct:mapAdministrativeUnitsToNetex")
                .to("direct:updateAdministrativeUnitsInTiamat")
                .to("direct:processTiamatAdministrativeUnitsUpdateCompleted")
                .log(LoggingLevel.INFO, "Finished updating administrative units in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-admin-units-update");

        from("direct:fetchAdministrativeUnits")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest administrative units ...")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForKartverket + "/administrativeUnits/" + adminUnitsArchiveFileName))
                .to("direct:getBlob")
                .process(e -> ZipFileUtils.unzipFile(e.getIn().getBody(InputStream.class), localWorkingDirectory))
                .routeId("tiamat-fetch-admin-units-geojson");

        from("direct:filterAdministrativeUnitsExclaves")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Removing exclaves from administrative units ...")
                .process(e -> new FeatureJSONFilter(localWorkingDirectory + "/abas/fylker.geojson", localWorkingDirectory + "/fylker.geojson", "fylkesnr", "area").filter())
                .process(e -> new FeatureJSONFilter(localWorkingDirectory + "/abas/kommuner.geojson", localWorkingDirectory + "/kommuner.geojson", "komm", "area").filter())
                .process(e -> new FeatureJSONFilter(localWorkingDirectory + "/abas/grunnkretser.geojson", localWorkingDirectory + "/grunnkretser.geojson", "grunnkrets", "area").filter())
                .routeId("tiamat-filter-geojson-exclaves");


        from("direct:mapAdministrativeUnitsToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest administrative units to Netex ...")
                .process(e -> topographicPlaceConverter.toNetexFile(
                        new GeoJsonTopographicPlaceReader(geoJsonFiles(e)),
                        localWorkingDirectory + "/admin-units-netex.xml"))
                .process(e -> e.getIn().setBody(new File(localWorkingDirectory + "/admin-units-netex.xml")))
                .routeId("tiamat-map-admin-units-geojson-to-netex");

        from("direct:updateAdministrativeUnitsInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
//                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-admin-units-update-start");


        from("direct:processTiamatAdministrativeUnitsUpdateCompleted")
                .setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_START))
                .process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
                .routeId("tiamat-admin-units-update-completed");
    }


    private File[] geoJsonFiles(Exchange e) {
        return adminUnitsFileNames.stream().map(f -> new File(localWorkingDirectory + "/" + f)).toArray(File[]::new);
    }

}
