package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.BlobStoreFiles.File;
import no.rutebanken.marduk.geocoder.routes.control.GeoCoderTaskType;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.routes.status.JobEvent;
import no.rutebanken.marduk.security.AuthorizationClaim;
import no.rutebanken.marduk.security.AuthorizationService;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.rutebanken.helper.organisation.AuthorizationConstants;
import org.rutebanken.helper.organisation.NotAuthenticatedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import javax.ws.rs.NotFoundException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static no.rutebanken.marduk.Constants.*;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {


    private static final String JSON = "application/json";
    private static final String X_OCTET_STREAM = "application/x-octet-stream";
    private static final String PLAIN = "text/plain";

    @Value("${server.admin.port}")
    public String port;

    @Value("${server.admin.host}")
    public String host;

    @Autowired
    private AuthorizationService authorizationService;

    @Override
    public void configure() throws Exception {
        super.configure();

        RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
        corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
        corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

        restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));


        onException(AccessDeniedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotAuthenticatedException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(401))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        onException(NotFoundException.class)
                .handled(true)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404))
                .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
                .transform(exceptionMessage());

        restConfiguration()
                .component("jetty")
                .bindingMode(RestBindingMode.json)
                .endpointProperty("filtersRef", "keycloakPreAuthActionsFilter,keycloakAuthenticationProcessingFilter")
                .endpointProperty("sessionSupport", "true")
                .endpointProperty("matchOnUriPrefix", "true")
                .endpointProperty("enablemulti-partFilter","true")
                .enableCORS(true)
                .dataFormatProperty("prettyPrint", "true")
                .host(host)
                .port(port)
                .apiContextPath("/api-doc")
                .apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")

                .contextPath("/admin");

        rest("")
                .apiDocs(false)
                .description("Wildcard definitions necessary to get Jetty to match authorization filters to endpoints with path params")
                .get().route().routeId("admin-route-authorize-get").throwException(new NotFoundException()).endRest()
                .post().route().routeId("admin-route-authorize-post").throwException(new NotFoundException()).endRest()
                .put().route().routeId("admin-route-authorize-put").throwException(new NotFoundException()).endRest()
                .delete().route().routeId("admin-route-authorize-delete").throwException(new NotFoundException()).endRest();

        rest("/application")
                .post("/filestores/clean")
                .description("Clean unique filename and digest Idempotent Stores")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-application-clean-unique-filename-and-digest-idempotent-repos")
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .to("direct:cleanIdempotentFileStore")
                .setBody(constant(null))
                .endRest()
                .post("/idempotent/download/clean")
                .description("Clean Idempotent repo for downloads")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-application-clean-idempotent-download-repos")
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .to("direct:cleanIdempotentDownloadRepo")
                .setBody(constant(null))
                .endRest();

        rest("/services/chouette")
                .get("/jobs")
                .description("List Chouette jobs for all providers. Filters defaults to status=SCHEDULED,STARTED")
                .param()
                .required(Boolean.FALSE)
                .name("status")
                .type(RestParamType.query)
                .description("Chouette job statuses")
                .allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name("action")
                .type(RestParamType.query)
                .description("Chouette job types")
                .allowableValues("importer", "exporter", "validator")
                .endParam()
                .outType(ProviderAndJobs[].class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.DEBUG, correlation() + "Get chouette active jobs all providers")
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setHeader("status", e.getIn().getHeader("status") != null ? e.getIn().getHeader("status") : Arrays.asList("STARTED", "SCHEDULED")))
                .to("direct:chouetteGetJobsAll")
                .routeId("admin-chouette-list-jobs-all")
                .endRest()
                .delete("/jobs")
                .description("Cancel all Chouette jobs for all providers")
                .responseMessage().code(200).message("All jobs canceled").endResponseMessage()
                .responseMessage().code(500).message("Could not cancel all jobs").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs for all providers")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCancelAllJobsForAllProviders")
                .routeId("admin-chouette-cancel-all-jobs-all")
                .setBody(constant(null))
                .endRest()
                .post("/clean/{filter}")
                .description("Triggers the clean ALL dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations) or stop places")
                .param()
                .required(Boolean.TRUE)
                .name("filter")
                .type(RestParamType.path)
                .description("Optional filter to clean only level 1, level 2 or all spaces (no parameter value)")
                .allowableValues("all", "level1", "level2")

                .endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette clean all dataspaces")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCleanAllReferentials")
                .setBody(constant(null))
                .routeId("admin-chouette-clean-all")
                .endRest()
                .post("/stop_places/clean")
                .description("Triggers the cleaning of ALL stop places in Chouette")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, correlation() + "Chouette clean all stop places")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCleanStopPlaces")
                .setBody(constant(null))
                .routeId("admin-chouette-clean-stop-places")
                .endRest()

                .get("/lineStats/{filter}")
                .description("List stats about data in chouette for multiple providers")
                .param().name("providerIds")
                .type(RestParamType.query).dataType("long")
                .required(Boolean.FALSE)
                .description("Comma separated list of id for providers to fetch line stats for")
                .endParam()
                .param()
                .name("filter")
                .required(Boolean.TRUE)
                .type(RestParamType.path)
                .description("Filter to fetch statistics for only level 1, level 2 or all spaces")
                .allowableValues("all", "level1", "level2")
                .endParam()
                .bindingMode(RestBindingMode.off)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, correlation() + "get stats for multiple providers")
                .removeHeaders("CamelHttp*")
                .choice()
                .when(simple("${header.providerIds}"))
                .process(e -> e.getIn().setHeader(PROVIDER_IDS, e.getIn().getHeader("providerIds", "", String.class).split(",")))
                .end()
                .to("direct:chouetteGetStats")
                .routeId("admin-chouette-stats-multiple-providers")
                .endRest()

                .get("/export/files")
                .description("List files containing exported time table data and graphs")
                .outType(BlobStoreFiles.class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route()
                .to("direct:authorizeRequest")
                .log(LoggingLevel.INFO, correlation() + "get time table and graph files")
                .removeHeaders("CamelHttp*")
                .to("direct:listTimetableExportAndGraphBlobs")
                .routeId("admin-chouette-timetable-files-get")

                .endRest();


        rest("/services/chouette/{providerId}")
                .post("/import")
                .description("Triggers the import->validate->export process in Chouette for each blob store file handle. Use /files call to obtain available files. Files are imported in the same order as they are provided")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .type(BlobStoreFiles.class)
                .outType(String.class)
                .consumes(JSON)
                .produces(PLAIN)
                .responseMessage().code(200).message("Job accepted").endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .removeHeaders("CamelHttp*")
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .split(method(ImportFilesSplitter.class, "splitFiles"))

                .process(e -> e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND
                                                                       + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
                                                                       + "/" + e.getIn().getBody(String.class)))
                .process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
                .log(LoggingLevel.INFO, correlation() + "Chouette start import fileHandle=${body}")

                .process(e -> {
                    String fileNameForStatusLogging = "reimport-" + e.getIn().getBody(String.class);
                    e.getIn().setHeader(Constants.FILE_NAME, fileNameForStatusLogging);
                })
                .setBody(constant(null))

                .inOnly("activemq:queue:ProcessFileQueue")
                .routeId("admin-chouette-import")
                .endRest()
                .get("/files")
                .description("List files available for reimport into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .outType(BlobStoreFiles.class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "blob store get files")
                .removeHeaders("CamelHttp*")
                .to("direct:listBlobsFlat")
                .routeId("admin-chouette-import-list")
                .endRest()
                .post("/files")
                .description("Upload file for import into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .consumes(MULTIPART_FORM_DATA)
                .produces(PLAIN)
                .bindingMode(RestBindingMode.off)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .streamCaching()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL,getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))))
                .log(LoggingLevel.INFO, correlation() + "upload files and start import pipeline")
                .removeHeaders("CamelHttp*")
                .to("direct:uploadFilesAndStartImport")
                .routeId("admin-chouette-upload-file")
                .endRest()

                .get("/files/{fileName}")
                .description("Download file for reimport into Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .param().name("fileName").type(RestParamType.path).description("Name of file to fetch").dataType("String").endParam()
                .consumes(PLAIN)
                .produces(X_OCTET_STREAM)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid fileName").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .process(e -> e.getIn().setHeader("fileName", URLDecoder.decode(e.getIn().getHeader("fileName", String.class), "utf-8")))
                .process(e -> e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND
                                                                       + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
                                                                       + "/" + e.getIn().getHeader("fileName", String.class)))
                .log(LoggingLevel.INFO, correlation() + "blob store download file by name")
                .removeHeaders("CamelHttp*")
                .to("direct:getBlob")
                .choice().when(simple("${body} == null")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).endChoice()
                .routeId("admin-chouette-file-download")
                .endRest()
                .get("/lineStats")
                .description("List stats about data in chouette for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .bindingMode(RestBindingMode.off)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "get stats")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteGetStatsSingleProvider")
                .routeId("admin-chouette-stats")
                .endRest()
                .get("/jobs")
                .description("List Chouette jobs for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .param()
                .required(Boolean.FALSE)
                .name("status")
                .type(RestParamType.query)
                .description("Chouette job statuses")
                .allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
                .endParam()
                .param()
                .required(Boolean.FALSE)
                .name("action")
                .type(RestParamType.query)
                .description("Chouette job types")
                .allowableValues("importer", "exporter", "validator")
                .endParam()
                .outType(JobResponse[].class)
                .consumes(PLAIN)
                .produces(JSON)
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Invalid providerId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Get chouette jobs status=${header.status} action=${header.action}")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteGetJobsForProvider")
                .routeId("admin-chouette-list-jobs")
                .endRest()
                .delete("/jobs")
                .description("Cancel all Chouette jobs for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Job deleted").endResponseMessage()
                .responseMessage().code(500).message("Invalid jobId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCancelAllJobsForProvider")
                .routeId("admin-chouette-cancel-all-jobs")
                .endRest()
                .delete("/jobs/{jobId}")
                .description("Cancel a Chouette job for a given provider")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .param().name("jobId").type(RestParamType.path).description("Job id as returned in any of the /jobs GET calls").dataType("int").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Job deleted").endResponseMessage()
                .responseMessage().code(500).message("Invalid jobId").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .setHeader(Constants.CHOUETTE_JOB_ID, header("jobId"))
                .log(LoggingLevel.INFO, correlation() + "Cancel chouette job")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCancelJob")
                .routeId("admin-chouette-cancel-job")
                .endRest()
                .post("/export")
                .description("Triggers the export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start export")
                .removeHeaders("CamelHttp*")
                .inOnly("activemq:queue:ChouetteExportQueue")
                .routeId("admin-chouette-export")
                .endRest()
                .post("/validate")
                .description("Triggers the validate->export process in Chouette")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .to("direct:authorizeRequest")
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette start validation")
                .removeHeaders("CamelHttp*")
                .setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(JobEvent.TimetableAction.VALIDATION_LEVEL_1.name()))
                .inOnly("activemq:queue:ChouetteValidationQueue")
                .routeId("admin-chouette-validate")
                .endRest()
                .post("/clean")
                .description("Triggers the clean dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations)")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .log(LoggingLevel.INFO, correlation() + "Chouette clean dataspace")
                .removeHeaders("CamelHttp*")
                .to("direct:chouetteCleanReferential")
                .routeId("admin-chouette-clean")
                .endRest()
                .post("/transfer")
                .description("Triggers transfer of data from one dataspace to the next")
                .param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .setHeader(PROVIDER_ID, header("providerId"))
                .log(LoggingLevel.INFO, correlation() + "Chouette transfer dataspace")
                .removeHeaders("CamelHttp*")
                .setHeader(PROVIDER_ID, header("providerId"))
                .validate(e -> getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)) != null)
                .inOnly("activemq:queue:ChouetteTransferExportQueue")
                .routeId("admin-chouette-transfer")
                .endRest();


        rest("/services/graph")
                .post("/build")
                .description("Triggers building of the OTP graph using existing gtfs and map data")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "OTP build graph")
                .removeHeaders("CamelHttp*")
                .setBody(simple(""))
                .inOnly("activemq:queue:OtpGraphQueue")
                .routeId("admin-build-graph")
                .endRest();

        rest("/services/fetch")
                .post("/osm")
                .description("Triggers downloading of the latest OSM data")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .log(LoggingLevel.INFO, "OSM update map data")
                .removeHeaders("CamelHttp*")
                .to("direct:considerToFetchOsmMapOverNorway")
                .routeId("admin-fetch-osm")
                .endRest();

        rest("/services/organisation_admin/administrative_zones")
                .post("/update")
                .description("Update administrative zones in the organisation registry")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ORGANISATION_EDIT)))
                .removeHeaders("CamelHttp*")
                .to("direct:updateAdminUnitsInOrgReg")
                .setBody(simple("done"))
                .routeId("admin-org-reg-import-admin-zones")
                .endRest();


        rest("/services/export/stop_places")
                .post("/full")
                .description("Trigger export from Stop Place Registry (NSR) for all existing configurations")
                .consumes(PLAIN)
                .produces(PLAIN)
                .responseMessage().code(200).message("Command accepted").endResponseMessage()
                .route()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .removeHeaders("CamelHttp*")
                .removeHeaders("Authorization")
                .to("direct:startFullTiamatPublishExport")
                .setBody(simple("done"))
                .routeId("admin-tiamat-publish-export-full")
                .endRest();


        rest("geocoder")
                .post("/start")

                .param().name("task")
                .type(RestParamType.query)
                .allowableValues(Arrays.asList(GeoCoderTaskType.values()).stream().map(GeoCoderTaskType::name).collect(Collectors.toList()))
                .required(Boolean.TRUE)
                .description("Tasks to be executed")
                .endParam()
                .description("Update geocoder tasks")
                .responseMessage().code(200).endResponseMessage()
                .responseMessage().code(500).message("Internal error").endResponseMessage()
                .route().routeId("admin-geocoder-update")
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN)))
                .validate(header("task").isNotNull())
                .removeHeaders("CamelHttp*")
                .process(e -> e.getIn().setBody(geoCoderTaskTypesFromString(e.getIn().getHeader("task", Collection.class))))
                .inOnly("direct:geoCoderStartBatch")
                .setBody(constant(null))
                .endRest();

        from("direct:authorizeRequest")
                .doTry()
                .process(e -> authorizationService.verifyAtLeastOne(new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_ADMIN),
                        new AuthorizationClaim(AuthorizationConstants.ROLE_ROUTE_DATA_EDIT, e.getIn().getHeader(PROVIDER_ID, Long.class))))
                .routeId("admin-authorize-request");


    }

    private Set<GeoCoderTaskType> geoCoderTaskTypesFromString(Collection<String> typeStrings) {
        return typeStrings.stream().map(s -> GeoCoderTaskType.valueOf(s)).collect(Collectors.toSet());
    }

    public static class ImportFilesSplitter {
        public List<String> splitFiles(@Body BlobStoreFiles files) {
            return files.getFiles().stream().map(File::getName).collect(Collectors.toList());
        }
    }
}


