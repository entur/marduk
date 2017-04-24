package no.rutebanken.marduk.geocoder.routes.tiamat;

import no.rutebanken.marduk.geocoder.netex.geojson.GeoJsonSingleTopographicPlaceReader;
import no.rutebanken.marduk.geocoder.netex.TopographicPlaceConverter;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.services.BlobStoreService;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.MediaType;
import java.io.File;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

@Component
public class TiamatCountryUpdateRouteBuilder extends BaseRouteBuilder {

    @Value("${tiamat.countries.geojson.blobstore.subdirectory:tiamat/countries}")
    private String blobStoreSubdirectory;

    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/jersey/publication_delivery}")
    private String tiamatPublicationDeliveryPath;

    @Value("${tiamat.neighbouring.countries.update.directory:files/tiamat/countries}")
    private String localWorkingDirectory;

    @Autowired
    private TopographicPlaceConverter topographicPlaceConverter;

    @Autowired
    private BlobStoreService blobStoreService;


    @Override
    public void configure() throws Exception {
        super.configure();

        from(TIAMAT_NEIGHBOURING_COUNTRIES_UPDATE_START.getEndpoint())
                .log(LoggingLevel.INFO, "Starting update of neighbouring countries in Tiamat")

                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .doTry()
                .to("direct:cleanUpLocalDirectory")
                .to("direct:fetchNeighbouringCountries")
                .to("direct:mapNeighbouringCountriesToNetex")
                .to("direct:updateNeighbouringCountriesInTiamat")
                .to("direct:processTiamatNeighbouringCountriesUpdateCompleted")
                .log(LoggingLevel.INFO, "Finished updating neighbouring countries in Tiamat")
                .doFinally()
                .to("direct:cleanUpLocalDirectory")
                .end()

                .routeId("tiamat-neighbouring-countries-update");

        from("direct:fetchNeighbouringCountries")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching latest neighbouring countries ...")

                .process(e -> e.getIn().setBody(blobStoreService.listBlobsInFolder(blobStoreSubdirectory, e).getFiles().stream().filter(f -> f.getName().endsWith("geojson")).collect(Collectors.toList())))
                .split().body()
                .setHeader(FILE_HANDLE, simple("${body.name}"))
                .process(e -> e.getIn().setHeader(Exchange.FILE_NAME, Paths.get(e.getIn().getBody(no.rutebanken.marduk.domain.BlobStoreFiles.File.class).getName()).getFileName()))
                .to("direct:getBlob")
                .to("file:" + localWorkingDirectory)
                .routeId("tiamat-fetch-neighbouring-countries");

        from("direct:mapNeighbouringCountriesToNetex")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Mapping latest neighbouring countries to Netex ...")
                .process(e -> topographicPlaceConverter.toNetexFile(
                        new GeoJsonSingleTopographicPlaceReader(getGeojsonCountryFiles()),
                        localWorkingDirectory + "/neighbouring-countries-netex.xml"))
                .process(e -> e.getIn().setBody(new File(localWorkingDirectory + "/neighbouring-countries-netex.xml")))
                .routeId("tiamat-map-neighbouring-countries-to-netex");

        from("direct:updateNeighbouringCountriesInTiamat")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, simple(MediaType.APPLICATION_XML))
                .to(tiamatUrl + tiamatPublicationDeliveryPath)
                .routeId("tiamat-neighbouring-countries-update-start");


        from("direct:processTiamatNeighbouringCountriesUpdateCompleted")
                .setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_START))
                .routeId("tiamat-neighbouring-countries-update-completed");
    }

    private File[] getGeojsonCountryFiles() {
        return FileUtils.listFiles(new File(localWorkingDirectory), new String[]{"geojson"}, false).stream().toArray(File[]::new);
    }
}
