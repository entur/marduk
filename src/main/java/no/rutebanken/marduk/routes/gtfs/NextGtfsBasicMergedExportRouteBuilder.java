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

    @Override
    public void configure() throws Exception {
        super.configure();

        from("direct:exportMergedGtfsNext")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Start export of merged GTFS file: ${header." + FILE_NAME + "}")
                .process(e -> JobEvent.systemJobBuilder(e).jobDomain(JobEvent.JobDomain.TIMETABLE_PUBLISH).action(e.getIn().getHeader(JOB_ACTION, String.class)).state(JobEvent.State.STARTED).newCorrelationId().build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .to("direct:createListOfGtfsFiles")
                .to("google-pubsub:{{damu.pubsub.project.id}}:DamuAggregateGtfsQueue")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Triggering merging and aggregation of GTFS file ${header." + FILE_NAME + "} in damu")
                .routeId("gtfs-export-merged-route");

        from("direct:reportExportMergedGtfsOKNext")
                .process(e -> JobEvent.systemJobBuilder(e).state(JobEvent.State.OK).build())
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .routeId("gtfs-export-merged-report-ok");

        from("direct:createListOfGtfsFiles")
                .log(LoggingLevel.INFO, getClass().getName(), correlation() + "Creating list of gtfs files for aggregation")
                .process(e -> e.getIn().setBody(getAggregatedGtfsFiles(getProviderBlackList(e), getProviderWhiteList(e))))
                .routeId("gtfs-export-list-files-route");

        from("google-pubsub:{{marduk.pubsub.project.id}}:MardukAggregateGtfsStatusQueue")
                .choice()
                .when(body().isEqualTo(STATUS_MERGE_OK))
                .to("direct:reportExportMergedGtfsOKNext")
                .otherwise()
                .to(ExchangePattern.InOnly, "direct:updateStatus")
                .end()
                .routeId("gtfs-export-merged-report-ok");
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
