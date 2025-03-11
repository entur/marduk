package no.rutebanken.marduk.routes.gtfs;

import no.rutebanken.marduk.domain.Provider;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.JobEvent;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.springframework.stereotype.Component;

import java.util.*;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.Constants.FILE_NAME;

@Component
public class NextGtfsBasicMergedExportRouteBuilder extends BaseRouteBuilder {

    private static final String STATUS_MERGE_OK = "ok";
    private static final String STATUS_MERGE_STARTED = "started";
    private static final String STATUS_MERGE_FAILED = "failed";
    private static final String STATUS_HEADER = "status";

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:exportMergedGtfsNext")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Start export of merged GTFS file: ${header." + FILE_NAME + "}")
                .to("direct:createListOfGtfsFiles")
                .convertBodyTo(String.class, "UTF-8")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Triggering merging and aggregation of GTFS files ${body} in damu")
                .setHeader(GTFS_ROUTE_DISPATCHER_HEADER_NAME, simple(GTFS_ROUTE_DISPATCHER_AGGREGATION_HEADER_VALUE))
                .to("google-pubsub:{{marduk.pubsub.project.id}}:GtfsRouteDispatcherTopic")
                .id("damuAggregateGtfsNext")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Done sending message on pubsub")
                .routeId("gtfs-export-merged-route-next");

        from("direct:reportExportMergedGtfsOKNext")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .routeId("gtfs-export-merged-report-ok-next");

        from("direct:createListOfGtfsFiles")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Creating list of gtfs files for aggregation")
                .process(e -> e.getIn().setBody(String.join(",", getAggregatedGtfsFiles(getProviderBlackList(e), getProviderWhiteList(e)))))
                .routeId("gtfs-export-list-files-route");

        from("google-pubsub:{{marduk.pubsub.project.id}}:MardukAggregateGtfsStatusQueue")
                .choice()
                    .when(header(STATUS_HEADER).isEqualTo(STATUS_MERGE_OK))
                        .log(LoggingLevel.INFO, correlation() + "Received status OK from damu aggregation")
                        .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).state(JobEvent.State.OK).correlationId(e.getIn().getHeader(CORRELATION_ID, String.class)).action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED).build())
                        .to(ExchangePattern.InOnly, "direct:updateStatus")
                    .when(header(STATUS_HEADER).isEqualTo(STATUS_MERGE_STARTED))
                        .log(LoggingLevel.INFO, correlation() + "Received status STARTED from damu aggregation")
                        .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).state(JobEvent.State.STARTED).action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED).newCorrelationId().build())
                        .to(ExchangePattern.InOnly, "direct:updateStatus")
                    .when(header(STATUS_HEADER).isEqualTo(STATUS_MERGE_FAILED))
                        .log(LoggingLevel.INFO, correlation() + "Received status FAILED from damu aggregation")
                        .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).state(JobEvent.State.FAILED).action(JobEvent.TimetableAction.EXPORT_GTFS_MERGED).correlationId(e.getIn().getHeader(CORRELATION_ID, String.class)).build())
                        .to(ExchangePattern.InOnly, "direct:updateStatus")
                .end()
                .routeId("gtfs-aggregate-status-route");
    }

    private List<String> getAggregatedGtfsFiles(Collection<String> providerBlackList, Collection<String> providerWhiteList) {
        return getProviderRepository().getProviders().stream()
                .filter(p -> p.getChouetteInfo().getMigrateDataToProvider() == null)
                .filter(p -> isMatch(p, providerBlackList, providerWhiteList))
                .map(p -> p.getChouetteInfo().getReferential() + "-" + CURRENT_AGGREGATED_GTFS_FILENAME)
                .toList();
    }

    private boolean isMatch(Provider p, Collection<String> providerBlackList, Collection<String> providerWhiteList) {
        if (providerWhiteList == null) {
            return providerBlackList.stream().noneMatch(blacklisted -> blacklisted.equalsIgnoreCase(p.getChouetteInfo().getReferential()));
        }
        return providerWhiteList.stream().anyMatch(whiteListed -> whiteListed.equalsIgnoreCase(p.getChouetteInfo().getReferential()));
    }

    private Collection<String> getProviderBlackList(Exchange e) {
        return Optional.ofNullable(e.getProperty(PROVIDER_BLACK_LIST, Collection.class)).orElse(Collections.emptyList());
    }

    private Collection<String> getProviderWhiteList(Exchange e) {
        return e.getProperty(PROVIDER_WHITE_LIST, Collection.class);
    }
}
