# Marduk - AI Assistant Guide

## Project Overview

Marduk is a Spring Boot application that orchestrates the timetable data import pipeline for Entur (Norwegian
public transport). It manages the entire workflow from receiving timetable data files through validation,
import, transformation, and publication to updating journey planner graphs.

### Core Purpose
- **Primary Role**: Backend integration orchestrator for public transport timetable data
- **Data Formats**: NeTEx (primary), GTFS (legacy, being migrated)
- **Architecture Pattern**: Event-driven orchestration over Google Cloud PubSub, using plain Spring
  components. Apache Camel was removed; see `docs/camel-removal.md` for the mapping from the old routes and
  for the behaviour changes that were deliberate.

## Technology Stack

- **Language**: Java 25
- **Framework**: Spring Boot 4
- **Build Tool**: Maven, parent `org.entur.ror:superpom` (sets the Spring Boot version)
- **Database**: PostgreSQL (with Flyway migrations)
- **Messaging**: Google Cloud PubSub, via `entur-google-pubsub`
- **Storage**: Google Cloud Storage (GCS)
- **REST**: Spring MVC `@RestController`, OpenAPI via springdoc
- **Scheduling**: Spring `@Scheduled`
- **Deployment**: Kubernetes (Helm charts in `helm/`)

Versions live in pom.xml; do not trust versions written in this file.

## Project Structure

```
src/main/java/no/rutebanken/marduk/
├── App.java                    # Main Spring Boot application
├── Constants.java              # Application-wide constants (headers, paths, file names)
├── Utils.java                  # Common utility functions
├── pipeline/                   # The message abstraction the pipeline passes around
│   ├── MardukMessage.java      # Headers plus body; replaces Camel's Exchange
│   ├── MardukMdc.java          # Puts the correlation id on the logging context
│   └── RetryPolicy.java        # In-process retry with backoff
├── pubsub/                     # PubSub plumbing
│   ├── MardukPubSubConsumer.java   # Base class for every consumer
│   ├── MardukPubSubPublisher.java  # Interface; PubSubTemplatePublisher publishes for real
│   ├── MardukQueues.java       # Every queue and topic name, and which project owns it
│   ├── PubSubAttributes.java   # Header <-> attribute mapping, including the exclusions
│   ├── PubSubPublishTargets.java   # Creates the publish-only destinations locally
│   └── InFlightWork.java, PubSubDrain.java  # The shutdown drain
├── leader/                     # Kubernetes Lease leader election, for work that runs once per cluster
├── batch/                      # Durable request batching in Postgres
│   ├── BatchedRequests.java    # record / claim / complete / release
│   └── BatchRunner.java        # Leader-gated tick that serves a claimed batch
├── config/                     # Spring configuration classes
│   ├── SchedulingConfig.java   # The taskScheduler the @Scheduled jobs share
│   ├── OAuth2Config.java, AuthorizationConfig.java
│   ├── IdempotentRepositoryConfig.java
│   └── *BlobStoreRepositoryConfig.java  # one per profile: gcs, in-memory, local-disk
├── domain/                     # Domain model entities
│   ├── Provider.java           # Transport data provider entity
│   ├── ChouetteInfo.java       # Chouette integration config per provider
│   ├── BlobStoreFiles.java     # File metadata
│   └── FileNameAndDigest.java  # For idempotency checking
├── exceptions/                 # Custom exception classes
├── inbound/                    # Receiving files from queues
├── upload/                     # HTTP upload and the duplicate-file filter
├── file/                       # File classification
├── validation/                 # Asking antu to validate a dataset
├── antu/                       # Handling antu's verdict
├── chouette/                   # Chouette integration: submit, poll, handle each job result
├── netex/                      # NeTEx merge and publication
├── flexlines/                  # Flexible transport integration
├── gtfs/                       # GTFS export handling
├── otp/                        # OpenTripPlanner graph building
├── osm/                        # OpenStreetMap data fetching
├── experimental/               # The ashur/servicelinker import path
├── graph/                      # OTP graph related logic
├── json/                       # JSON serialization helpers
├── kubernetes/                 # Kubernetes integration
├── repository/                 # Data access layer
├── rest/                       # REST API endpoints
│   ├── AdminRestController.java
│   ├── AdminExternalRestController.java
│   ├── HealthController.java
│   └── OpenApiDocController.java   # The API title, and the second path it is served at
├── routes/                     # No routes left: the DTOs, processors and enums the pipeline still uses
│   ├── file/FileType.java      # Enum: GTFS, NETEXPROFILE, etc.
│   ├── chouette/json/          # Chouette request and response payloads
│   └── status/JobEvent.java    # The job status events nabu consumes
├── security/                   # OAuth2 and authorization
└── services/                   # Business logic services
    ├── *BlobStoreService.java  # Storage abstractions
    └── IdempotentRepositoryService.java

src/main/resources/
├── db/migration/              # Flyway: V1__Base_version.sql, V2__Batched_requests.sql
├── logback.xml                # Logging configuration
└── openapi/                   # API specifications
```

## Key Concepts

### 1. Consumers

Each step of the pipeline is a `@Component` extending `MardukPubSubConsumer`, which supplies two methods:

- `destination()`: the unqualified queue name, always a `MardukQueues` constant
- `handle(MardukMessage)`: the work

`MardukQueues` decides which GCP project a name resolves to, so a consumer never spells out a project. The
base class also sets the MDC, tracks the message for the shutdown drain, and turns the payload and attribute
map into a `MardukMessage`. It does not expose a logger: declare one per class.

Failure handling is the library's: an exception escaping `handle` nacks the message and PubSub redelivers it
after the subscription's `minimum_backoff`. Retry *inside* `handle` with `RetryPolicy`, around the one call
worth retrying, rather than around the whole message, which would repeat side effects that already happened.

### 2. Data Import Workflow (9 Steps)

1. **Pre-validation**: NeTEx XML validation via Antu service
2. **Import**: Load data into provider's work-in-progress database (Chouette)
3. **Validation**: Run validation rules on imported data
4. **Transfer**: Copy validated data to central timetable database
5. **Validation**: Re-validate transferred data
6. **NeTEx Export**: Generate NeTEx files from database
7. **Post-validation**: Validate exported NeTEx files
8. **Publication**: Publish validated datasets to Open Data Portal
9. **Graph Update**: Trigger OpenTripPlanner graph rebuild

### 3. Message Flow Pattern

```
File Upload -> InboundQueue -> ProcessFileQueue -> Classification ->
Validation -> Import -> Export -> Publication -> OTP Graph Update
```

Each step communicates via PubSub queues and updates job status.

### 4. Provider System
- Each transport operator is a `Provider` with unique configuration
- Stored in external service (Baba) and cached locally
- Provider-specific settings: referential, auto-import flag, validation config

### 5. Idempotency
- Files tracked by name and MD5 checksum in PostgreSQL
- Duplicate files rejected automatically
- `upload/DuplicateFileFilter` is the guard on the upload path; `IdempotentRepository` and
  `IdempotentRepositoryService` are the store behind it
- The atomic claim is the schema, not the code: `V1__Base_version.sql` declares a primary key on
  `(processorname, filename)` and a unique index on `(processorname, digest)`, so two pods racing on the same
  file both insert and the loser's insert fails. Do not "fix" that by widening the transaction

### 6. Work That Must Happen Once Per Cluster

Scheduled jobs and batch ticks run in every pod, so anything that must happen once asks
`LeaderElection.isLeader()` first. Leadership comes from a Kubernetes Lease, which needs the `patch` verb in
the RBAC role: without it the lease silently stops renewing.

### 7. Batching

Requests that used to accumulate in a Camel aggregator now accumulate as rows: `BatchedRequests.record()`
writes one, and a leader-gated `BatchRunner` tick claims everything waiting and runs the job once. This is
what keeps a burst of export requests from starting an export per request.

`BatchRunner` deletes the claimed rows when the job returns and releases the claim when it throws, so *a job
that throws* is retried on the next tick. That is not the same as "a failure is retried", and the difference
matters per job:

- The merged GTFS export lets failures propagate, so it is retried.
- **Both OTP2 graph builds catch their own build failure**, report the job `FAILED` and return normally. The
  rows are therefore deleted and nothing is retried - deliberately, because the Camel routes did the same
  with `doCatch(Exception)` followed by `stop()`. `Otp2NetexGraphBuild` also returns early, and so consumes
  the batch, when there is no stop place export to build from. A failure in the *later* steps - the merged
  NeTEx export, publishing the finished graph - does propagate and is retried.

## Important Constants

From `Constants.java`, these headers are used throughout:
- `PROVIDER_ID`: Transport operator ID
- `CORRELATION_ID`: Trace ID for tracking workflow
- `FILE_HANDLE`: Blob storage path to file
- `FILE_TYPE`: the `routes/file/FileType` name, as a string
- `CHOUETTE_REFERENTIAL`: Database schema/namespace
- `FILE_NAME`: Original filename

## Configuration

### Application Properties
- Located in `src/test/resources/application.properties` (example)
- Uses Spring profiles: `gcs-blobstore` (deployed), `in-memory-blobstore` and `local-disk-blobstore`,
  `test` (switches off `MardukWebSecurityConfiguration`), `google-pubsub-autocreate` (creates the topics and
  subscriptions against an emulator; never active in a deployed environment)
- Key properties:
  - `marduk.pubsub.project.id`, and `antu.`/`nabu.`/`ashur.`/`servicelinker.pubsub.project.id`: all five are
    required - `MardukQueues` resolves a destination to a project from them, and a missing one fails the
    context at startup
  - `blobstore.gcs.container.name`: Storage bucket
  - `providers.api.url`: Provider repository service URL
  - `marduk.camel.redelivery.*`: read by `RetryPolicy`, which kept the old property names so the deployed
    ConfigMap did not have to change in the same release

### Environment-Specific Config
- Kubernetes ConfigMaps in `helm/marduk/templates/configmap.yaml`
- Helm values for different environments
- Cron values are written quartz-style with `+` for spaces and are transformed for Spring; `CronScheduleTest`
  pins that every one of them still parses, because an unparseable cron crash-loops every pod at startup

## Testing

- Unit tests use H2 in-memory database
- PubSub emulator via Testcontainers
- In-memory blob store for file operations
- `MardukSpringBootBaseTest` is the base for Spring tests, and clears the blob store and the duplicate-file
  keys between methods. Tests that leave state behind are the usual cause of an order-dependent failure.
- `RecordingPubSubPublisher` captures what a consumer published, which is how most handlers are asserted

## Common Development Tasks

### Adding a Pipeline Step

1. Add the queue name to `MardukQueues`, under the project that owns it
2. Create a `@Component` extending `MardukPubSubConsumer`
3. Return the queue name from `destination()` and do the work in `handle(MardukMessage)`
4. Publish onward with `MardukPubSubPublisher`, using constants from `Constants.java` for headers

Example:

```java
@Component
public class MyStepConsumer extends MardukPubSubConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(MyStepConsumer.class);

    private final MardukPubSubPublisher publisher;

    public MyStepConsumer(MardukPubSubPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    protected String destination() {
        return MardukQueues.MY_STEP_QUEUE;
    }

    @Override
    protected void handle(MardukMessage message) {
        LOGGER.info("Processing {}", message.getHeader(FILE_HANDLE));
        publisher.publish(MardukQueues.NEXT_STEP_QUEUE, message);
    }
}
```

### Adding a Scheduled Job

Annotate a method with `@Scheduled(cron = "${some.cron.property}")`. The pool is configured in
`SchedulingConfig`; size it there if the job is long-running, because a job that overruns its slot blocks the
others. Gate the body on `LeaderElection.isLeader()` unless it is genuinely safe to run in every pod.

### Adding a REST Endpoint

Add the mapping to `AdminRestController` (Ninkasi) or `AdminExternalRestController` (machine-to-machine).

Do not set `produces` on the mapping. Ninkasi sends `Accept: application/json` on every request, including
the ones that answer plain text, and `produces` turns that into a 406. Set the content type on the response
instead.

Nothing needs adding by hand to cover this. `AdminRestControllerIntegrationTest` walks
`RequestMappingHandlerMapping.getHandlerMethods()` and fails on any marduk mapping whose produces condition is
incompatible with `application/json`, so a new endpoint is guarded the moment it is mapped.

The one exemption: handler methods declared by the generated interfaces under
`no.rutebanken.marduk.rest.openapi.api` are skipped, and the guard asserts that the skipped set is **exactly
three** - so the exemption cannot quietly widen. Those three are genuine, not a workaround: the generated
partner API really does declare `application/x-octet-stream, text/plain` on its `download` operation, and it
did so on master too, so the spec is the contract and the 406 risk was accepted there long before this work.

### Working with Blob Storage

- Use injected `*BlobStoreService` classes
- Internal blobs: `MardukInternalBlobStoreService`
- Public blobs: `MardukPublicBlobStoreService`
- Common operations: `uploadBlob()`, `getBlob()`, `listBlobs()`

### Job Status Updates

```java
jobEvents.reportProviderJob(message, builder -> builder
        .timetableAction(JobEvent.TimetableAction.IMPORT)
        .state(JobEvent.State.STARTED));
```

### Error Handling

- Let the exception escape `handle` when redelivery is the right answer; PubSub will redeliver
- Use `RetryPolicy` for a single call that is worth retrying in process - the Chouette HTTP calls and the
  blob store calls are what it is for. `retryPolicy.call("what is being attempted", () -> ...)` returns a
  value, `run(...)` is the void form; the delays come from `marduk.camel.redelivery.*` and the sleep is
  interruptible so shutdown is not held up. Wrap the one call, never the whole `handle` - that is what Camel's
  error handler did, and it repeated side effects that had already succeeded
- Log with the correlation id on the MDC, which `MardukPubSubConsumer` sets up
- Messages that can never succeed go to `MardukDeadLetterQueue`

## External System Integration

### Antu
- **Purpose**: NeTEx validation service
- **Communication**: PubSub, request on `AntuNetexValidationQueue`, verdict on `AntuNetexValidationStatusQueue`
- **Classes**: `validation/AntuValidation`, `antu/AntuValidationStatusConsumer`

### Chouette
- **Purpose**: Timetable data import/export/validation engine
- **Communication**: REST API (JSON payloads), with job status polled
- **Classes**: `chouette/`, where `ChouetteJobPoller` polls and a `ChouetteJobResultHandler` per job type
  handles the outcome

### Damu
- **Purpose**: GTFS export generator
- **Communication**: PubSub
- **Classes**: `gtfs/`

### OpenTripPlanner (OTP)
- **Purpose**: Journey planning graph builder
- **Communication**: File-based (GCS), Kubernetes API
- **Classes**: `otp/`

### Uttu/Enki
- **Purpose**: Flexible transport timetable editors
- **Integration**: Merges their NeTEx exports with Chouette exports

## Database Schema

- Managed by Flyway migrations in `src/main/resources/db/migration/`
- Two tables:
  - `camel_unique_filename_and_digest` - the imported file names and digests the duplicate filter reads. The
    name is a leftover from Camel's JDBC idempotent repository; renaming it needs a migration and a
    coordinated deploy, so it stays
  - `batched_request` - the durable batches described above

Migrations run against H2 in tests and PostgreSQL in production, so the DDL has to parse as both.

## Security

- OAuth2 authentication via `MardukWebSecurityConfiguration`
- Authorization service: `MardukAuthorizationService`
- Role-based access control for REST endpoints
- Multi-issuer JWT support

## Logging

- Structured logging with Logback
- JSON format via logstash-logback-encoder
- MDC logging enabled for correlation tracking
- Correlation ID propagated through entire workflow

## Deployment

- Docker image built from `Dockerfile`
- Helm chart in `helm/marduk/`
- Kubernetes-native: leader election on a Lease (`leader/KubernetesLeaderElection`) and the OTP graph builds
  run as Kubernetes Jobs (`kubernetes/KubernetesJobRunner`), both through the fabric8 client. That client is
  declared explicitly in `pom.xml`, not inherited - it used to arrive transitively through
  `camel-kubernetes-starter`, and `KubernetesClientOnTheClasspathTest` is why a missing one fails the build
  rather than the first pod
- Terraform infrastructure in `terraform/`

## Scheduled Jobs

Spring `@Scheduled`, leader-gated where it matters:
- Nightly revalidation of datasets, and the Chouette level 2 validation (`ChouetteValidationSchedules`)
- OSM data refresh (`OsmMapFetcher`) - hourly at 11 minutes past, despite the name
- Old job cleanup in Chouette (`ChouetteJobCleanup`)
- The candidate base graph monitor (`CandidateBaseGraphBuilderMonitor`)
- The batch ticks that serve accumulated export and graph-build requests
- The provider cache refresh (`CacheProviderRepository.populate`), which is deliberately **not** leader-gated:
  every pod needs its own cache

`autoStartup`-style flags gate the schedule and nothing else, so the matching admin endpoint stays reachable
with the flag off.

## Code Style Guidelines

1. **Minimal Comments**: Code is self-documenting with clear method names. Comment the reason, not the
   mechanism, and only when the reason is not visible in the code.
2. **Logging**: Rely on the MDC correlation id rather than formatting it into every message
3. **Constants**: Never hardcode strings, use `Constants.java` and `MardukQueues`
4. **Headers**: Use typed header access: `message.getHeader(PROVIDER_ID, Long.class)`
5. **Status Updates**: Keep them where the old routes had them; nabu and the operator UI depend on the exact
   action and state values

## Troubleshooting

### Common Issues

1. **Provider Repository Not Available**: App waits on startup until provider service responds
2. **PubSub Emulator**: point `spring.cloud.gcp.pubsub.emulator-host` and `PUBSUB_EMULATOR_HOST` at it; the
   emulator's own default is port 8085
3. **Database**: Auto-created via Flyway if `spring.flyway.enabled=true`
4. **File Encoding**: Strict UTF-8 validation for NeTEx files
5. **406 from an admin endpoint**: a `produces` was added to the mapping
6. **A leader-gated job never runs**: the Lease is not being renewed, usually a missing `patch` verb

### Debug Tips

- Check PubSub dead letter queue for failed messages
- Review job status in status tracking system
- Check blob storage for intermediate files
- `batched_request` rows that never disappear mean the batch tick is failing or no pod is leader
- **A stranded claim.** If a pod dies between `claim()` and `complete()`/`release()`, its rows keep the
  `claim_id` the dead pod wrote. `BatchedRequests.waiting()` counts only `claim_id IS NULL`, so those rows
  are invisible to the size trigger and no tick will ever pick them up again - the batch is stuck with no
  log line saying so. Spot it with
  `select kind, claim_id, claimed_at, count(*) from batched_request group by 1,2,3;`: a non-null `claim_id`
  with a `claimed_at` older than the tick interval is stranded. Clearing it by hand
  (`update batched_request set claim_id = null, claimed_at = null where claim_id = '...'`) puts the requests
  back in the queue

## Building and Running

The build needs a JDK 25; the enforcer rejects anything older, so set `JAVA_HOME` rather than relying on the
shell default.

```bash
export JAVA_HOME=/path/to/jdk-25

./mvnw clean package     # build
./mvnw test              # tests
```

Running it needs a Postgres, a PubSub emulator and a reachable provider service. `README.md` has the
properties; the pipeline superproject's `docker compose up` or `local-k8s` is the shorter route.

## API Documentation

- OpenAPI specs in `src/main/resources/openapi/`
- REST endpoints accessible at `/services/*`
- Generated by springdoc from the controller annotations, at `springdoc.api-docs.path=/services/openapi`
  (so also `/services/openapi.yaml`), and forwarded from `/services/timetable_admin/openapi.yaml` by
  `OpenApiDocController` because the old REST DSL published it there too
- Admin endpoints in `AdminRestController` and `AdminExternalRestController`

## Related Projects (Entur Ecosystem)

- **Bel**: Operator portal for file uploads
- **Ninkasi**: Administration console
- **Baba**: Provider repository service
- **Antu**: NeTEx validation service
- **Chouette**: Timetable database and transformation engine
- **Damu**: GTFS export generator
- **Uttu/Enki**: Flexible transport timetable editors
- **OTP**: OpenTripPlanner journey planner

## License

Licensed under EUPL 1.2 (European Union Public License)

## Additional Notes for AI Assistants

- This is a production system handling critical public transport data
- Changes must maintain backward compatibility with external systems
- All file operations should preserve idempotency
- PubSub message ordering is not guaranteed - design for eventual consistency
- Provider configuration is external - never hardcode provider-specific logic
- Status updates are an external contract; keep them when refactoring
- `docs/camel-removal.md` records what the old routes did and which differences were deliberate. Read it
  before concluding that something looks wrong.
