package no.rutebanken.marduk.routes.backup;

import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.geocoder.routes.pelias.babylon.StartFile;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.http4.HttpMethods;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Trigger a backup of database data. Intended to be run once a day.
 * It starts a (self-contained) pod which takes care of backing
 * up database to a separate volume.
 */
@Component
public class BackupDatabaseRouteBuilder extends BaseRouteBuilder {
    @Value("${babylon.url:http4://babylon/babylon/api}")
    private String babylonUrl;

    @Value("${database.backup.job:database-backup-pod.yaml}")
    private String databaseBackupFilename;

    @Value("${fetch.osm.cron.schedule:0+*+3+?+*+*}")
    private String cronSchedule;

    @Override
    public void configure() throws Exception {
        super.configure();

        onException(MardukException.class)
                .log(LoggingLevel.ERROR, "Failed while fetching OSM file.")
                .handled(true);


        from("direct:runDatabaseBackup")
                .log(LoggingLevel.INFO, "Requesting Babylon to start database backup")
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .setHeader(Exchange.HTTP_METHOD, constant(HttpMethods.POST))
                .setBody(constant(new StartFile(databaseBackupFilename)))
                .marshal().json(JsonLibrary.Jackson)
                .to(babylonUrl + "/run")
                .log(LoggingLevel.INFO, "Invocation of backup code complete")
                .routeId("backup-database-by-called");


        singletonFrom("quartz2://marduk/triggerDatabaseBackup?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
                .log(LoggingLevel.INFO, "Quartz triggers database backup.")
                .to("direct:runDatabaseBackup")
                .log(LoggingLevel.INFO, "Quartz processing done.")
                .routeId("backup-database-by-trigger");
    }

}
