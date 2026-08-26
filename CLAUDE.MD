# Marduk - AI Assistant Guide

## Project Overview

Marduk is a Spring Boot application that orchestrates the timetable data import pipeline for Entur (Norwegian public transport). It manages the entire workflow from receiving timetable data files through validation, import, transformation, and publication to updating journey planner graphs.

### Core Purpose
- **Primary Role**: Backend integration orchestrator for public transport timetable data
- **Data Formats**: NeTEx (primary), GTFS (legacy, being migrated)
- **Architecture Pattern**: Event-driven, message-based orchestration using Apache Camel

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

- Enable route tracing: `camel.main.tracing=true`
- Check PubSub dead letter queue for failed messages
- Review job status in status tracking system
- Check blob storage for intermediate files

## Building and Running

```bash
# Build
./mvnw clean package

# Run locally
java -Xmx500m \
  -Dspring.config.location=/path/to/application.properties \
  -Dfile.encoding=UTF-8 \
  -jar target/marduk-0.0.1-SNAPSHOT.jar

# Run tests
./mvnw test
```

## Related Projects (Entur Ecosystem)

- **Bel**: Operator portal for file uploads
- **Ninkasi**: Administration console
- **Baba**: Provider repository service
- **Antu**: NeTEx validation service
- **Chouette**: Timetable database and transformation engine
- **Damu**: GTFS export generator
- **Uttu/Enki**: Flexible transport timetable editors
- **OTP**: OpenTripPlanner journey planner

## Additional Notes for AI Assistants

- This is a production system handling critical public transport data
- Changes must maintain backward compatibility with external systems
- All file operations should preserve idempotency
- PubSub message ordering is not guaranteed - design for eventual consistency
- Provider configuration is external - never hardcode provider-specific logic
- The workflow is complex but well-structured - follow existing patterns
- When modifying routes, ensure error handling and status updates are maintained
