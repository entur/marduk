# Marduk - AI Assistant Guide

## Project Overview

Marduk is a Spring Boot application that orchestrates the timetable data import pipeline for Entur (Norwegian public transport). It manages the entire workflow from receiving timetable data files through validation, import, transformation, and publication to updating journey planner graphs.

### Core Purpose
- **Primary Role**: Backend integration orchestrator for public transport timetable data
- **Data Formats**: NeTEx (primary), GTFS (legacy, being migrated)
- **Architecture Pattern**: Event-driven, message-based orchestration using Apache Camel

## Technology Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.x with Apache Camel 4.4.5
- **Build Tool**: Maven
- **Database**: PostgreSQL (with Flyway migrations)
- **Messaging**: Google Cloud PubSub
- **Storage**: Google Cloud Storage (GCS)
- **REST**: Jersey (JAX-RS)
- **Deployment**: Kubernetes (Helm charts in `helm/`)

## Project Structure

```
src/main/java/no/rutebanken/marduk/
├── App.java                    # Main Spring Boot application, extends RouteBuilder
├── Constants.java              # Application-wide constants (headers, paths, file names)
├── Utils.java                  # Common utility functions
├── config/                     # Spring configuration classes
│   ├── CamelConfig.java
│   ├── OAuth2Config.java
│   ├── IdempotentRepositoryConfig.java
│   └── *BlobStoreRepositoryConfig.java
├── domain/                     # Domain model entities
│   ├── Provider.java           # Transport data provider entity
│   ├── ChouetteInfo.java       # Chouette integration config per provider
│   ├── BlobStoreFiles.java     # File metadata
│   └── FileNameAndDigest.java  # For idempotency checking
├── exceptions/                 # Custom exception classes
├── routes/                     # Apache Camel route builders (main business logic)
│   ├── BaseRouteBuilder.java           # Abstract base with common routing utilities
│   ├── TransactionalBaseRouteBuilder.java
│   ├── inbound/                        # Receiving files from queues
│   ├── file/                           # File upload, classification, validation
│   │   ├── FileUploadRouteBuilder.java
│   │   ├── FileClassificationRouteBuilder.java
│   │   └── FileType.java               # Enum: GTFS, NETEXPROFILE, etc.
│   ├── chouette/                       # Chouette database integration
│   │   ├── ChouetteImportRouteBuilder.java
│   │   ├── ChouetteValidationRouteBuilder.java
│   │   ├── ChouetteExportNetexRouteBuilder.java
│   │   └── ChouetteTransferToDataspaceRouteBuilder.java
│   ├── netex/                          # NeTEx format handling
│   │   ├── NetexMergeChouetteWithFlexibleLineExportRouteBuilder.java
│   │   └── PublishMergedNetexRouteBuilder.java
│   ├── flexlines/                      # Flexible transport integration
│   ├── otp/                            # OpenTripPlanner graph building
│   ├── aggregation/                    # Data aggregation across providers
│   ├── fetchosm/                       # OpenStreetMap data fetching
│   ├── gtfs/                           # GTFS export handling
│   ├── admin/                          # Admin operations
│   └── status/                         # Job status tracking
├── rest/                       # REST API endpoints
│   ├── AdminRestRouteBuilder.java
│   └── AdminExternalRestRouteBuilder.java
├── services/                   # Business logic services
│   ├── *BlobStoreService.java  # Storage abstractions
│   └── IdempotentRepositoryService.java
├── repository/                 # Data access layer
├── security/                   # OAuth2 and authorization
├── graph/                      # OTP graph related logic
├── json/                       # JSON serialization helpers
└── kubernetes/                 # Kubernetes integration

src/main/resources/
├── db/migration/              # Flyway database migrations
├── logback.xml                # Logging configuration
└── openapi/                   # API specifications
```

## Key Concepts

### 1. Apache Camel Routes
The application is built around Apache Camel routes. Each `*RouteBuilder` class defines one or more integration routes:

- **Route Definition**: Uses Camel DSL in `configure()` method
- **Route IDs**: Each route has a unique `.routeId("route-name")`
- **Direct Endpoints**: Internal routes use `direct:routeName` for synchronous calls
- **PubSub Endpoints**: Async messaging via `google-pubsub:{{project.id}}:QueueName`
- **Message Headers**: Extensive use of headers defined in `Constants.java`

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
File Upload → InboundQueue → ProcessFileQueue → Classification → 
Validation → Import → Export → Publication → OTP Graph Update
```

Each step communicates via PubSub queues and updates job status.

### 4. Provider System
- Each transport operator is a `Provider` with unique configuration
- Stored in external service (Baba) and cached locally
- Provider-specific settings: referential, auto-import flag, validation config

### 5. Idempotency
- Files tracked by name and MD5 checksum in PostgreSQL
- Duplicate files rejected automatically
- Implemented via `IdempotentRepositoryService`

## Important Constants

From `Constants.java`, these headers are used throughout:
- `PROVIDER_ID`: Transport operator ID
- `CORRELATION_ID`: Trace ID for tracking workflow
- `FILE_HANDLE`: Blob storage path to file
- `FILE_TYPE`: Enum indicating GTFS, NETEXPROFILE, etc.
- `CHOUETTE_REFERENTIAL`: Database schema/namespace
- `FILE_NAME`: Original filename

## Configuration

### Application Properties
- Located in `src/test/resources/application.properties` (example)
- Uses Spring profiles: `in-memory-blobstore`, `test`, `gcs-blobstore`
- Key properties:
  - `marduk.pubsub.project.id`: GCP project
  - `blobstore.gcs.container.name`: Storage bucket
  - `providers.api.url`: Provider repository service URL
  - `camel.*`: Camel configuration

### Environment-Specific Config
- Kubernetes ConfigMaps in `helm/marduk/templates/configmap.yaml`
- Helm values for different environments

## Testing

- Unit tests use H2 in-memory database
- PubSub emulator via Testcontainers
- In-memory blob store for file operations
- Base test class setup in `src/test/`

## Common Development Tasks

### Adding a New Route

1. Create class extending `BaseRouteBuilder` or `TransactionalBaseRouteBuilder`
2. Annotate with `@Component`
3. Override `configure()` method
4. Define routes with `.from()` and `.to()`
5. Set meaningful `.routeId()`
6. Use constants from `Constants.java` for headers

Example:
```java
@Component
public class MyNewRouteBuilder extends BaseRouteBuilder {
    @Override
    public void configure() throws Exception {
        super.configure();
        
        from("direct:myRoute")
            .log(LoggingLevel.INFO, "Processing...")
            .to("direct:nextStep")
            .routeId("my-route");
    }
}
```

### Working with Blob Storage

- Use injected `*BlobStoreService` classes
- Internal blobs: `MardukInternalBlobStoreService`
- Public blobs: `MardukPublicBlobStoreService`
- Common operations: `uploadBlob()`, `getBlob()`, `listBlobs()`

### Job Status Updates

```java
.process(e -> JobEvent.providerJobBuilder(e)
    .timetableAction(JobEvent.TimetableAction.IMPORT)
    .state(JobEvent.State.STARTED)
    .build())
.to("direct:updateStatus")
```

### Error Handling

- Use `onException()` in routes for exception handling
- Log with correlation ID: `correlation() + "message"`
- Failed jobs go to `MardukDeadLetterQueue`

## External System Integration

### Antu
- **Purpose**: NeTEx validation service
- **Communication**: REST API calls
- **Route**: `AntuNetexValidationStatusRouteBuilder`

### Chouette
- **Purpose**: Timetable data import/export/validation engine
- **Communication**: REST API (JSON payloads)
- **Routes**: All classes in `routes/chouette/`

### Damu
- **Purpose**: GTFS export generator
- **Communication**: PubSub messages
- **Route**: `DamuExportGtfsStatusRouteBuilder`

### OpenTripPlanner (OTP)
- **Purpose**: Journey planning graph builder
- **Communication**: File-based (GCS), Kubernetes API
- **Routes**: Classes in `routes/otp/`

### Uttu/Enki
- **Purpose**: Flexible transport timetable editors
- **Integration**: Merges their NeTEx exports with Chouette exports

## Database Schema

- Managed by Flyway migrations in `src/main/resources/db/migration/`
- Primary tables:
  - File tracking (name, checksum, timestamp)
  - Idempotency records

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
- Kubernetes-native (leader election, service discovery)
- Terraform infrastructure in `terraform/`

## Scheduled Jobs

- Uses Quartz scheduler via Camel
- Nightly revalidation of datasets
- OSM data refresh
- Old job cleanup in Chouette

## Code Style Guidelines

1. **Minimal Comments**: Code is self-documenting with clear method names
2. **Route IDs**: Always use lowercase-with-hyphens
3. **Logging**: Include `correlation()` prefix for traceability
4. **Constants**: Never hardcode strings, use `Constants.java`
5. **Error Handling**: Always handle exceptions at route level
6. **Headers**: Use typed header access: `header(PROVIDER_ID, Long.class)`

## Troubleshooting

### Common Issues

1. **Provider Repository Not Available**: App waits on startup until provider service responds
2. **PubSub Emulator**: Must run on port 8085 for local development
3. **Database**: Auto-created via Flyway if `spring.flyway.enabled=true`
4. **File Encoding**: Strict UTF-8 validation for NeTEx files

### Debug Tips

- Enable route tracing: `camel.springboot.tracing=true`
- Check PubSub dead letter queue for failed messages
- Review job status in status tracking system
- Check blob storage for intermediate files

## Building and Running

```bash
# Build
mvn clean package

# Run locally
java -Xmx500m \
  -Dspring.config.location=/path/to/application.properties \
  -Dfile.encoding=UTF-8 \
  -jar target/marduk-0.0.1-SNAPSHOT.jar

# Run tests
mvn test
```

## API Documentation

- OpenAPI specs in `src/main/resources/openapi/`
- REST endpoints accessible at `/services/*`
- Admin endpoints in `AdminRestRouteBuilder` and `AdminExternalRestRouteBuilder`

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
- The workflow is complex but well-structured - follow existing patterns
- When modifying routes, ensure error handling and status updates are maintained
