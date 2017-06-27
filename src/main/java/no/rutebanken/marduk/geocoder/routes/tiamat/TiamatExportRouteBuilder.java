package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.geocoder.routes.tiamat.xml.ExportJob;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.BLOBSTORE_MAKE_BLOB_PUBLIC;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;

/**
 * Common functionality shared between tiamat exports.
 */
@Component
public class TiamatExportRouteBuilder extends BaseRouteBuilder {


    @Value("${tiamat.url}")
    private String tiamatUrl;

    @Value("${tiamat.publication.delivery.path:/jersey/publication_delivery}")
    private String tiamatPublicationDeliveryPath;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:tiamatExport")
                .log(LoggingLevel.INFO, "Start Tiamat export")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .setBody(constant(null))
                .toD(tiamatUrl + tiamatPublicationDeliveryPath + "/async/${header." + Constants.QUERY_STRING + "}")
                .convertBodyTo(ExportJob.class)
                .setHeader(Constants.JOB_ID, simple("${body.id}"))
                .setHeader(Constants.JOB_STATUS_URL, simple(tiamatPublicationDeliveryPath + "/${body.jobUrl}"))
                .log(LoggingLevel.INFO, "Started Tiamat export of file: ${body.fileName}")
                .setProperty(GEOCODER_NEXT_TASK, constant(TIAMAT_EXPORT_POLL))
                .end()
                .routeId("tiamat-export");

        from("direct:tiamatExportMoveFileToMardukBlobStore")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching tiamat export file ...")
                .toD(tiamatUrl + "/${header." + Constants.JOB_STATUS_URL + "}/content")
                .setHeader(BLOBSTORE_MAKE_BLOB_PUBLIC, constant(true))
                .to("direct:uploadBlob")
                .routeId("tiamat-export-move-file");

    }


}
