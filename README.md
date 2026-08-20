
# Marduk

Marduk orchestrates the timetable data import pipeline.

# Data input channels
Marduk receives datasets from the following channels:
- File upload from the operator portal ([Bel](https://github.com/entur/bel))
- File upload from the administration console ([Ninkasi](https://github.com/entur/ninkasi))
- File transfer via a REST API
- Internal PubSub messaging for flight data ([Extime](https://github.com/entur/extime))

Input data are primarily NeTEx datasets. GTFS datasets are still in use but will ultimately be migrated to NeTEx.

# Data import workflow
Marduk performs basic validation checks on the input files (check that the file is a valid zip archive, simple data format check) and then initiates the import workflow:
1. Pre-validation of NeTEx data ([Antu](https://github.com/entur/antu))  
Antu runs a set of validation rules against the NeTEx dataset (XML Schema validation, XPath queries, ...).
2. Import of NeTEx data into the data provider timetable database ([Chouette](https://github.com/entur/chouette))  
Chouette imports the NeTEx data into an intermediate, work-in-progress database.
3. Validation of the imported data ([Chouette](https://github.com/entur/chouette))  
Chouette runs a second set of validation rules on the intermediate database.
4. Transfer from the data provider database to the central timetable database  ([Chouette](https://github.com/entur/chouette))  
Chouette copies the timetable data from the intermediate database to the central database. The central database contains only validated data. 
5. Validation of the transferred timetable data ([Chouette](https://github.com/entur/chouette))  
Chouette runs the same set of validation rules as in step 3. 
6. NeTEx export ([Chouette](https://github.com/entur/chouette))  
Chouette exports the timetable data into a NeTEx archive.
7. NeTEx export post-validation ([Antu](https://github.com/entur/antu))  
Antu runs the same set of validation rules as in step 1. against the exported NeTEx dataset.
8. Publication of validated NeTEx datasets  
Marduk publishes the validated datasets on [Entur Open Data Portal](https://developer.entur.org/stops-and-timetable-data)
9. Update of the journey planner graph  
Marduk triggers a rebuild of the journey planner graph so that it reflects the latest version of timetable data ([OTP](https://github.com/opentripplanner))

# Periodic timetable data revalidation
Some NeTEx validation rules are time-dependent, in particular those that rely on external reference data such as the [Norwegian Stop Place Register](https://stoppested.entur.org/).
It is necessary to revalidate periodically the imported datasets to guarantee that they still refer to valid stop places. Revalidation allows also for pruning expired data, such as trip  whose effective date is in the past.  
Marduk schedules a nightly revalidation of every dataset which triggers a regeneration of each NeTEx export file. Expired data are removed from the new exports.

# GTFS export
In addition to orchestrating NeTEx data export, Marduk triggers also an export of GTFS data ([Damu](https://github.com/entur/damu)) 

# Integration with flexible transport data
Marduk merges the NeTEx datasets containing flexible timetables generated in NPlan ([Uttu](https://github.com/entur/uttu) and [Enki](https://github.com/entur/enki)) with those generated in Chouette.

# Journey planner street graph update
[OpenTripPlanner](https://github.com/opentripplanner) relies on OpenStreetMap data to calculate the first/last leg of a journey (walk from start point or to destination point).
Marduk schedules a nightly download of OpenStreetMap data that in turn is used by OpenTripPlanner to build an updated street graph.

# Architecture

Marduk is a plain Spring Boot application: a `@Component` per pipeline step consuming a PubSub subscription,
`@Scheduled` methods for the periodic work, Spring MVC for the admin API, and Kubernetes Lease leader
election for the work that must happen once per cluster. Apache Camel was removed;
[docs/camel-removal.md](docs/camel-removal.md) maps the old routes onto what replaced them and records which
behaviour differences were deliberate. `CLAUDE.MD` is the working guide to the code.

# Deployment

EnTur deploys Marduk using [Harness](https://app.harness.io/ng/account/8VwWgE0WRK67_PWDpkooNA/all/cd/orgs/entur/projects/ror/services/marduk)

# Building

The build requires a **JDK 25**. The enforcer fails on anything older
(`Detected JDK ... is version 21.0.12 which is not in the allowed range [25,)`), so set `JAVA_HOME`
explicitly rather than relying on the shell default:

```sh
export JAVA_HOME=/path/to/jdk-25          # e.g. /Library/Java/JavaVirtualMachines/liberica-jdk-25.jdk/Contents/Home
./mvnw clean package                      # produces target/marduk-0.0.1-SNAPSHOT.jar
./mvnw test                               # unit tests; needs Docker for the PubSub emulator container
```

Add `-o` to work from the local repository once it is warm - useful offline, and faster:

```sh
./mvnw -o -DskipTests package
```

# Running against the local pipeline

This is the shortest path to a running marduk, and the one to prefer: the superproject
[marduk-pipeline](https://github.com/entur/marduk-pipeline) runs marduk together with every service it
talks to (antu, ashur, servicelinker, damu, nabu, nanna, baba, ninkasi) plus emulated PubSub, GCS and
Postgres. Marduk is a submodule of it, so from the superproject root:

```sh
./scripts/build-all.sh        # every service's jar, marduk included
docker compose up --build
```

Marduk answers on <http://localhost:21080>, the admin UI on <http://localhost:21000>. Upload a dataset
through marduk's REST API:

```sh
curl -X POST http://localhost:21080/services/timetable_admin/9999/files -F "file=@my-dataset.zip"
```

`local-k8s/` in the same superproject runs the identical images on a local Kubernetes cluster instead, which
is the only way to exercise the multi-replica paths - Lease leader election with a real contender, and one
pod picking up a batch another pod recorded. See `local-k8s/README.md`; in short:

```sh
./scripts/build-all.sh && docker compose build   # k8s consumes the images compose builds
kubectl apply -k . --context orbstack            # from the superproject root, not local-k8s/
./scripts/k8s-port-forward.sh                    # same host ports compose publishes
```

# Running standalone

A standalone run needs a Postgres, a PubSub emulator and a reachable provider service
([Nanna](https://github.com/entur/nanna), historically Baba).

## Marduk database
Marduk uses a database to store the history of imported file names and checksums.  
This is used by the duplicate-file filter to reject files that have been already imported.
A Docker PostgreSQL instance can be used for local testing (the pipeline stack uses PostGIS 18; production
is `POSTGRES_17`):

```sh
docker run -d -p 5432:5432 --name marduk-database \
  -e POSTGRES_PASSWORD=mypostgrespassword postgres:17
```

Create the database and role from `psql`:

```sql
create database marduk;
create user marduk with password 'mypassword';
alter role marduk superuser;
```

Flyway creates the schema at startup when `spring.flyway.enabled=true`.

## Google PubSub emulator
See https://cloud.google.com/pubsub/docs/emulator for installation. Start it with:

```sh
gcloud beta emulators pubsub start
```

It listens on port 8085 by default.

## Spring Boot configuration file
`src/test/resources/application.properties` is the closest template; `helm/marduk/templates/configmap.yaml`
is the deployed set. The minimum a standalone run needs:

```properties
# Datasource
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.url=jdbc:postgresql://localhost:5432/marduk
spring.datasource.username=marduk
spring.datasource.password=mypassword
spring.flyway.enabled=true

# PubSub emulator. All five project ids are required: MardukQueues resolves each destination to a project
# from them, and a missing one fails the context at startup. Locally they are all the same value.
spring.cloud.gcp.pubsub.emulator-host=localhost:8085
spring.cloud.gcp.project-id=local-project
marduk.pubsub.project.id=local-project
antu.pubsub.project.id=local-project
nabu.pubsub.project.id=local-project
ashur.pubsub.project.id=local-project
servicelinker.pubsub.project.id=local-project

# Blob store. These are bucket *names* rather than a choice of implementation, so they are read whichever
# blobstore profile is active and every one below has no default: leave one out and the context fails.
# Only blobstore.gcs.graphs.container.name has a default (otp-graphs).
blobstore.gcs.project.id=local-project
blobstore.gcs.container.name=marduk
blobstore.gcs.internal.container.name=marduk-internal
blobstore.gcs.exchange.container.name=marduk-exchange
blobstore.gcs.otpreport.container.name=otpreport
blobstore.gcs.nisaba.exchange.container.name=nisaba-exchange
blobstore.gcs.antu.exchange.container.name=antu-exchange
blobstore.gcs.ashur.exchange.container.name=ashur-exchange
blobstore.gcs.servicelinker.exchange.container.name=servicelinker-exchange

# Provider repository
providers.api.url=http://localhost:11101/services/providers/

# OAuth2 client. Required even for a local run that authenticates nothing: OAuth2Config injects
# OAuth2ClientProperties, and Boot only registers that bean once a client registration is declared.
spring.security.oauth2.client.registration.marduk.authorization-grant-type=client_credentials
spring.security.oauth2.client.registration.marduk.client-id=marduk
spring.security.oauth2.client.registration.marduk.client-secret=notInUse
spring.security.oauth2.client.provider.marduk.token-uri=https://notInUse
marduk.oauth2.client.audience=notInUse

# Chouette. Nothing needs to listen on it to start.
chouette.url=http://localhost:9999
```

Profiles: `gcs-blobstore` (deployed) or `in-memory-blobstore` / `local-disk-blobstore`; add `test` to switch
off `MardukWebSecurityConfiguration`, and `google-pubsub-autocreate` to have marduk create its topics and
subscriptions on an empty emulator.

## Starting the application

```sh
export JAVA_HOME=/path/to/jdk-25
./mvnw clean package
java -Xmx500m \
  -Dspring.profiles.active=in-memory-blobstore,test,google-pubsub-autocreate \
  -Dspring.config.location=/path/to/application.properties \
  -Dfile.encoding=UTF-8 \
  -jar target/marduk-0.0.1-SNAPSHOT.jar
```

Marduk waits at startup until the provider service answers, retrying every
`marduk.provider.service.retry.interval` ms (default 5000), so `Provider Repository not available` in a loop
means marduk started correctly and cannot reach the provider service - not that it failed. Note that the
provider fetch goes out through the OAuth2 client, so `spring.security.oauth2.client.provider.marduk.token-uri`
has to resolve as well as `providers.api.url`; with the placeholder above the loop reports
`Failed to resolve 'notInUse'`.

The Kubernetes probes target `/services/health`, which answers `OK` as `text/plain` and is the quickest check
that the admin API came up - the actuator health group stays UP even when `/services/**` is not being served,
which is why the probes do not use it.

The property block above was checked by running the jar with exactly those values: the context builds and
startup reaches the provider-repository wait, so nothing is missing from it. A run that also imports a
dataset needs the emulator, the database and the provider service actually up, which is what the pipeline
superproject gives you.
