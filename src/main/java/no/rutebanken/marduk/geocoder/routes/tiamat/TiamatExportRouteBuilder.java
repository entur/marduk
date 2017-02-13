package no.rutebanken.marduk.geocoder.routes.tiamat;


import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.geocoder.tiamat.xml.ExportJob;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TiamatExportRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${tiamat.export.cron.schedule:0+*+*/23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${tiamat.url}")
	private String tiamatUrl;

	private String tiamatExportPath = "jersey/publication_delivery/async";

	@Override
	public void configure() throws Exception {
		super.configure();

		from("quartz2://marduk/tiamatExport?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.log(LoggingLevel.INFO, "Quartz triggers Tiamat export.")
				.to("activemq:queue:TiamatExportQueue")
				.routeId("tiamat-export-quartz");

		singletonFrom("activemq:queue:TiamatExportQueue?transacted=true&messageListenerContainerFactoryRef=batchListenerContainerFactory")
				.autoStartup("{{tiamat.export.autoStartup:false}}")
				.transacted()
				.log(LoggingLevel.INFO, "Start Tiamat export")
				.setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
				.setBody(constant(null))
				.to(tiamatUrl + tiamatExportPath)
				.convertBodyTo(ExportJob.class)
				.setHeader(Constants.JOB_ID, simple("${body.id}"))
				.setHeader(Constants.JOB_STATUS_URL, simple("${body.jobUrl}"))
				.setHeader(Constants.JOB_STATUS_ROUTING_DESTINATION, constant("direct:processTiamatExportResults"))
				.log(LoggingLevel.INFO, "Started Tiamat export of file: ${body.fileName}")
				.to("activemq:queue:TiamatPollStatusQueue")
				.end()
				.routeId("tiamat-export");


		from("direct:processTiamatExportResults")
				.to("activemq:queue:PeliasUpdateQueue")
				.routeId("tiamat-export-results");
	}


}
