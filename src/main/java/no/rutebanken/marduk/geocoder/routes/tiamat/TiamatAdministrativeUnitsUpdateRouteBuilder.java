package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.geocoder.netex.sosi.SosiTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.repository.BlobStoreRepository;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.file.ZipFileUtils;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.TokenService;
import no.rutebanken.marduk.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class TiamatAdministrativeUnitsUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${kartverket.blobstore.subdirectory:kartverket}")
    private String blobStoreSubdirectoryForKartverket;

    @Value("${tiamat.administrative.units.update.cron.schedule:0+0+23+?+*+MON-FRI}")
    private String cronSchedule;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/services/stop_places/netex}")
    private String tiamatPublicationDeliveryPath;

    @Value("${tiamat.administrative.units.update.directory:files/tiamat/adminUnits}")
    private String localWorkingDirectory;

    @Autowired
    private TopographicPlaceConverter topographicPlaceConverter;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private BlobStoreService blobStore;

    @Override
    public void configure() throws Exception {
        super.configure();

        singletonFrom("quartz2://marduk/tiamatAdministrativeUnitsUpdate?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .autoStartup("{{tiamat.administrative.units.update.autoStartup:false}}")
                .filter(e -> isLeader(e.getFromRouteId()))
                .log(LoggingLevel.INFO, "Quartz triggers Tiamat update of administrative units.")
                .setBody(constant(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START))
                .to("direct:geoCoderStart")
                .routeId("tiamat-admin-units-update-quartz");

        from(TIAMAT_ADMINISTRATIVE_UNITS_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Starting update of administrative units in Tiamat")
                .process(e -> JobEvent.systemJobBuilder(e).startGeocoder(GeoCoderTaskType.TIAMAT_ADMINISTRATIVE_UNITS_UPDATE).build()).to("direct:updateStatus")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:mapAdministrativeUnitsToNetex")
                .to("direct:updateAdministrativeUnitsInTiamat")
                .to("direct:processTiamatAdministrativeUnitsUpdateCompleted")
                .log(LoggingLevel.INFO, "Finished updating administrative units in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-admin-units-update");

        from("direct:mapAdministrativeUnitsToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest administrative units to Netex ...")
                .process(e -> {

                    blobStore.listBlobsInFolder(blobStoreSubdirectoryForKartverket + "/administrativeUnits", e).getFiles().stream()
                            .filter(blob -> blob.getName().endsWith(".zip"))
                            .forEach(blob ->  ZipFileUtils.unzipFile(blobStore.getBlob(blob.getName(), e), localWorkingDirectory));
                    topographicPlaceConverter.toNetexFile(
                            new SosiTopographicPlaceReader(FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"sos"}, true)), localWorkingDirectory + "/admin-units-netex.xml");
                    new File(localWorkingDirectory).delete();
                    e.getIn().setBody(new File(localWorkingDirectory + "/admin-units-netex.xml"));
                })
                .routeId("tiamat-map-admin-units-sosi-to-netex");

        from("direct:updateAdministrativeUnitsInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .process(e -> e.getIn().setHeader("Authorization", "Bearer " + tokenService.getToken()))
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-admin-units-update-start");


        from("direct:processTiamatAdministrativeUnitsUpdateCompleted")
                .setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_START))
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build()).to("direct:updateStatus")
                .routeId("tiamat-admin-units-update-completed");
    }


}
