# Removing Apache Camel from marduk

The record of the removal: what the old routes did, what replaced them, and which behaviour differences were
deliberate. Read it before concluding that something in the current code looks wrong.

Claims marked **[measured]** were read off this repo's code, the rendered ConfigMap, the helm values or a
dependency jar. **[assumed]** means reasoning that was never checked against the artifact or against
production. Written alongside
[antu's camel-migration-learnings.md](https://github.com/entur/antu/blob/master/docs/camel-migration-learnings.md)
and `damu/docs/camel-removal.md`, which cover the same job in smaller services; where they and this file
disagree about marduk, this one wins.

The removal is complete. `pom.xml` has no `org.apache.camel` artifact, no route builder is left, and
`grep -rn "org.apache.camel" src --include=*.java` matches one javadoc line in `IdempotentRepository` naming
the interface it replaced. What is left below is the reasoning, not a plan.

## What Camel was doing that no line of marduk mentioned

Route builder by route builder, and the requirement each one put on the replacement. The middle column
describes `origin/master`, in the present tense it was written in; the right-hand column is what the
replacement therefore had to do.

| Camel feature | What marduk relied on | Requirement it put on the replacement |
| --- | --- | --- |
| `errorHandler` / `redeliveryPolicy` | **[measured]** `defaultErrorHandler` with 3 redeliveries, 5000 ms base delay, exponential backoff multiplier 3. The ConfigMap does **not** set `marduk.camel.redelivery.max`, so production really runs 3 in-process retries - the opposite of antu, where the deployed value was 0. `src/test/resources/application.properties` sets it to 0, so no test exercises the retry path production depends on. | Reproduce 3 retries with exponential backoff before nacking, or drop it deliberately and accept that every transient failure becomes a PubSub redelivery. Not a silent change either way. |
| `onCompletion` | **[measured]** Three uses: MDC cleanup in `BaseRouteBuilder`; `IdempotentFileFilterRoute`'s `onFailureOnly` block that removes the name+digest key when a non-duplicate exchange failed; the PubSub `AcknowledgeCompletion` handover around aggregators. | Explicit `finally` for MDC, explicit compensation for the idempotent key, explicit ack deferral for batches. The middle one is a claim-released-on-failure of exactly the shape the guide warns about. |
| aggregator: OTP2 | **[measured]** `Otp2NetexGraphRouteBuilder` and `Otp2BaseGraphRouteBuilder` aggregate with `completionSize(100)` and **no `completionTimeout`**. Completion comes from `IdleRouteAggregationMonitor`, keyed on the id of the *downstream* route (`otp2-remote-netex-graph-build`, `otp2-base-graph-build`), polled every 5 s by a quartz route on every node. So the real rule is "when no graph build is running, batch everything queued and build once" - a debounce, not a size or time window. | Reproduce the debounce. Below 100 queued requests the idle monitor is the **only** completion trigger, so dropping it means graph builds never start. This is antu's "the `completionTimeout` was the only thing guaranteeing a terminal status" in another shape. |
| aggregator: GTFS | **[measured]** `GtfsMergedExportRouteBuilder` aggregates on a constant key with `completionSize` from `aggregation.completionSize` (default 100, unset in the ConfigMap, set to 1 in tests) and `completionTimeout` from `gtfs.export.aggregation.timeout` (default 300000, unset in the ConfigMap). No aggregate controller, so the idle monitor does *not* apply to it. Runs on `gtfsExportExecutorService`, pool size 1. | Batch on size-or-timeout, and keep the concurrency of one. Note the test default of 1 means tests never aggregate more than a single message. |
| aggregator: header handling | **[measured]** `HeaderPreservingGroupedMessageAggregationStrategy` drops every header except an explicit list, and the aggregated body is a `List<Message>`. `addSynchronizationForAggregatedExchange` then walks that list to re-attach each message's deferred ack. | Whatever carries a batch has to carry each member's ack token, and must fail loudly if one is missing (the current code throws `IllegalStateException("Synchronization holder not found")`). |
| `master:` | **[measured]** `singletonFrom()` wraps 10 consumers. Lock name is derived by stripping the scheme and the query string, so `google-pubsub:proj:Otp2GraphBuildQueue?maxAckExtensionPeriod=14400` locks on `Otp2GraphBuildQueue`. Backed by `camel.cluster.kubernetes` in k8s (ConfigMap `marduk-leaders`) and by a file lock in tests. | Leader election plus a leader-gated consumer. **[measured]** camel-master stops its child consumer when leadership is *lost*; an `isLeader()` check does not, and antu still has that gap. With `forceReplicas: 2` there is exactly one other pod to race with. |
| quartz | **[measured]** 6 schedules. 5 are `singletonFrom` + `shouldQuartzRouteTrigger`, which re-checks `isStarted && isLeader && fireTime within quartz.lenient.fire.time.ms` (default 180000, unset in the ConfigMap) - a guard against the re-fire that happens when a route resumes on a leadership change. The 6th, the aggregation checker, is deliberately not singleton and uses `stateful=true` with `repeatInterval` rather than a cron. | `@Scheduled` plus an explicit leader check. **[measured]** the guide's warning applies here: spring-cloud-gcp registers its own `TaskScheduler` beans, so `TaskSchedulingAutoConfiguration` backs off, nothing is named `taskScheduler`, and `@Scheduled` silently builds a single-threaded executor. marduk needs an explicitly named `taskScheduler` bean and a test that reads the pool size back off it with a non-default value set. |
| type converters | **[measured]** `convertBodyTo(byte[].class)`, `convertBodyTo(String.class)`, `convertBodyTo(InputStream.class)`, and implicit conversion in every `getHeader(x, Long.class)` / `getBody(InputStream.class)`. `JobEvent.ExchangeStatusBuilder` reads `PROVIDER_ID` as `String.class` and then `Long.valueOf`s it, which only works because Camel converts. | Typed accessors that do the same coercions. A header arriving from a PubSub attribute is always a `String`; the same header set in-process is often a `Long`. Both paths currently work by conversion, so the replacement's accessor has to accept both. |
| data formats | **[measured]** `ListJacksonDataFormat(JobResponse.class)`, `unmarshal().json(Jackson, ...)`, `marshal().json()`, and `camel.dataformat.jackson.module-refs=jacksonJavaTimeModule` in the ConfigMap wiring the `JavaTimeModule` into those. `JobEvent` separately uses `ObjectMapperFactory`, whose mapper registers `JavaTimeModule` without disabling `WRITE_DATES_AS_TIMESTAMPS`, so `eventTime` goes out as an epoch-seconds decimal. Pinned by `WireContractTest`. | One mapper, explicitly configured, used by both paths. Dropping `module-refs` without replacing it silently changes date shape for the Chouette JSON. |
| `platform-http` | **[measured]** `restConfiguration().component("platform-http").contextPath("/services").inlineRoutes(false).bindingMode(json).apiContextPath("/openapi.yaml")`, plus `camel.component.platform-http.serverRequestValidation=false` to keep pre-4.18 behaviour of not rejecting on Accept/Content-Type. Routes run on Spring's `applicationTaskExecutor`, sized up to `spring.task.execution.pool.core-size=32` because uploads were starving the probe off it. `spring.mvc.servlet.load-on-startup=1` exists because a lazily initialised `DispatcherServlet` left `/services/**` 404ing per pod. | Spring MVC at the identical paths. **The liveness, readiness and startup probes all target `/services/health`**, chosen precisely because it proves Camel's mappings registered while `/actuator/health` stays UP without them. That path must keep answering `OK` as `text/plain`. `values.yaml`'s probe rationale has been rewritten for Spring MVC, except for one comment on the `startupProbe` that still explains the `httpGet` choice in terms of "the connector binds before Camel starts"; the reason survives the rename - the connector binds before the `DispatcherServlet` serves `/services/**` - so only the wording is stale. |
| ack deadline | **[measured, off the 4.21.0 jar]** Camel's `maxAckExtensionPeriod` default is 3600 s. `maxAckExtensionPeriod=14400` (4 h) is set on `Otp2GraphBuildQueue`, `Otp2BaseGraphBuildQueue` and `Otp2BaseGraphCandidateBuildQueue` - but **not** on `Otp2GraphCandidateBuildQueue`, which therefore gets 1 h. Looks like an oversight rather than intent. `CamelConfig` forces `maxDeliveryAttempts=0` on every endpoint via an endpoint callback, because 4.18 otherwise fetches it through the Subscriber Admin API and marduk's service account lacks the permission. **[measured]** All 27 terraformed subscriptions have `ack_deadline_seconds` of 60 or 600; the 4 h ceiling is purely client-side. | See *Ack deadline* below - the defaults happen to line up, so only three subscriptions need configuring. |
| consumer concurrency | **[measured, off the 4.21.0 jar]** `concurrentConsumers` defaults to 1, and `GooglePubsubConsumer` creates that many subscribers. No marduk endpoint overrides it, so today every one of the 22 subscriptions processes strictly one message at a time per pod. | **[measured]** `PubSubConfiguration.DEFAULT_EXECUTOR_THREADS` is 4, and flow control defaults to 1000 outstanding messages. Left alone the replacement runs 4x hotter on 22 subscriptions inside a 2-CPU pod. Pin `executor-threads` and `flow-control.max-outstanding-element-count`, as damu did. |
| autocreate notifier | **[measured]** `AutoCreatePubSubSubscriptionEventNotifier` walks every endpoint in the context on startup under the `google-pubsub-autocreate` profile, so it creates destinations marduk only *publishes* to as well as ones it consumes. Used in tests and locally only. | Create publish-only destinations explicitly. This is the one that bit antu: a consumer-driven replacement creates only what it subscribes to, and the first publish against a fresh emulator fails `NOT_FOUND`. marduk publishes to 8 destinations it never consumes. |
| MDC | **[measured]** `interceptFrom(".*")` copies correlation id and codespace into MDC at every route entry, `onCompletion` clears them, and `getContext().setUseMDCLogging(true)` adds Camel's own keys. | Explicit set/clear around each unit of work, in a `finally`. Logs are the main tool for the rollout itself, so this is worth getting right before the pipeline moves. |
| case-insensitive headers | **[measured]** Camel's message headers are case-insensitive while preserving the original spelling. `removeHttpHeaders` calls `removeHeader(AUTHORIZATION)` with the literal `"Authorization"`, and it is only that case-insensitivity that strips a lowercase `authorization` sent by an HTTP/2 client. The method runs immediately before the outbound calls to Chouette. **[measured]** It is a *started-context* property: on an unstarted `DefaultCamelContext` the map is a plain `HashMap` and the strip silently fails. `MardukSpringBootBaseTest.exchange()` builds exactly such an exchange, so any existing test using it has case-sensitive headers while production does not. | The carrier's header map must match: case-insensitive lookup and removal, original key spelling preserved, because PubSub attribute names are case-sensitive on the wire. A plain `HashMap` would silently forward marduk's inbound bearer token to Chouette. `MardukMessage` does it unconditionally, so it does not depend on a lifecycle the way Camel's does. |
| PubSub attribute mapping | **[measured]** Inbound, every attribute not prefixed `CamelGooglePubsub` becomes a header. Outbound, `interceptSendToEndpoint` builds the attribute map from every header not prefixed `Camel`, excluding `breadcrumbId` and `Authorization` and any value over 1024 characters, and `OutboundFilteringHeaderFilterStrategy` suppresses the producer's separate copy-everything pass. | Same mapping, and the two exclusions are load-bearing security controls rather than tidiness - `Authorization` is otherwise copied onto published messages from the incoming HTTP request. Worth a test that asserts a request carrying an `Authorization` header publishes without one. |

## Why the replacement is shaped the way it is

### The Chouette routing slip is a wire format

**[measured]** `CHOUETTE_JOB_STATUS_ROUTING_DESTINATION` carries the literal strings
`direct:processImportResult`, `direct:processValidationResult`, `direct:processNetexExportResult`,
`direct:processNetexBlocksExportResult` and `direct:processTransferExportResult`. Marduk sets it on the
message it puts on `ChouettePollStatusQueue`, and `direct:rescheduleJob` re-publishes the whole header map
every 30 s for up to `chouette.max.retries=3000` polls. A job started shortly before a deploy is still
being polled long afterwards, carrying the value the previous version wrote.

So whatever replaces the `toD("${header...}")` dispatch must keep accepting those five literals for at
least as long as a poll can outlive a rollout. `WireContractTest.everyPinnedRoutingDestinationIsStillAccepted`
fails if one stops being named in the sources. This is the same class of problem as antu's Kryo incident:
state that turns out to be a wire format.

### The shutdown budget does not add up for the obvious replacement

**[measured]** No `terminationGracePeriodSeconds` was set anywhere in helm or the env values, so it was the
Kubernetes default of 30 s, and `marduk.shutdown.timeout=25` was sized against that.

**[measured, off the 7.2.0 sources jar]** `AbstractEnturGooglePubSubConsumer.handleContextClosedEvent` is an
`@EventListener` at `HIGHEST_PRECEDENCE` that loops over its subscribers calling
`EnturGooglePubSubUtils.closeSubscriber`, which is `stopAsync().awaitTerminated(10, TimeUnit.SECONDS)`.
Sequential, per subscriber, and gax will not terminate while a callback is running, so for a consumer that is
mid-work the wait always expires in full. There is no `SmartLifecycle`, no in-flight tracking and no drain
anywhere in the library. With 22 consumers the worst case is 220 s of subscriber close.

**[measured]** The same class's failure path makes it worse:

```java
} catch (Exception e) {
  basicAcknowledgeablePubsubMessage.nack();
  LOGGER.error("Message processing failed, retrying in {} milliseconds", retryDelay, e);
  delay(retryDelay);          // Thread.sleep, default entur.pubsub.consumer.retry.delay=15000
}
```

The sleep is on the subscriber callback thread, so a repeatedly failing message both serialises its
subscription to one attempt per sleep and keeps the 10 s `awaitTerminated` expiring. Failures and slow
shutdown reinforce each other.

Hence three things in this branch: `terminationGracePeriodSeconds` raised to 120, the library's sleep cut
right down (see *The library's post-failure sleep* below), and `InFlightWork` plus `PubSubDrain` written as a
`SmartLifecycle` rather than inherited. `RetryPolicy`'s sleep is interruptible for the same reason.

### The publisher is shut down before late work can use it

**[measured, from damu's writeup, same library versions]** `CachingPublisherFactory.shutdown()` is
`@PreDestroy`, so every cached `Publisher` is closed with the bean. Work that outlives the 10 s
subscriber close therefore drains successfully and then cannot publish its result - the exact redelivery
a drain exists to prevent. `spring.cloud.gcp.pubsub.publisher.executor-accept-tasks-after-context-close=true`
narrows it but does not close it, because it only keeps the thread pool accepting work. Camel was not
exposed to this at all: its google-pubsub component built publishers outside the Spring lifecycle.

### `goog` attributes: not broken today, one terraform line away from it

**[measured]** Only `GtfsRouteDispatcherTopic` has a `dead_letter_policy`, and marduk publishes to that
one rather than consuming it. **None of marduk's own 26 subscriptions has one.** PubSub only attaches
`googclient_deliveryattempt` when the subscription has a dead-letter policy, which is why the current
copy-every-header-back-out behaviour does not already fail.

**[measured, from damu's reading of the 4.21.0 jar]** `goog` is reserved: publishing an attribute with
that prefix is rejected with `INVALID_ARGUMENT`, and Camel's producer skipped it via
`GooglePubsubConstants.RESERVED_GOOGLE_CLIENT_ATTRIBUTE_PREFIX` (whose value is `"goog"`, not
`"googclient_"`).

marduk re-publishes its entire header map on every `ChouettePollStatusQueue` reschedule, so the day
anyone adds a dead-letter policy to a marduk subscription, every Chouette poll would start failing to
publish. The replacement should filter the `goog` prefix on echo regardless, and a test should pin it.

### Autocreate defaults to on, and would try to create terraformed topics

**[measured]** `AbstractEnturGooglePubSubConsumer.handleContextRefreshed` calls
`enturGooglePubSubAdmin.createSubscriptionIfMissing(getDestinationName())`, and
**[measured, from damu's writeup]** `entur.pubsub.subscriber.autocreate` defaults to **true**. Under
Camel this behaviour was behind the `google-pubsub-autocreate` Spring profile, which no deployed
environment activates. With the consumer base class it is on by default, so the ConfigMap has to set
`entur.pubsub.subscriber.autocreate=false` or 22 consumers will each try to create a terraformed
subscription and fail on `pubsub.topics.create`.

### Ack deadline: the defaults line up, so only three subscriptions need configuring

**[measured]** Camel's `maxAckExtensionPeriod` default is 3600 s, and
`Subscriber.DEFAULT_MAX_ACK_EXTENSION_PERIOD` - which applies because spring-cloud-gcp holds
`maxAckExtensionPeriod` as a boxed `Long` and skips the setter when unset - is 60 minutes. The same hour,
by coincidence rather than design. So 19 of the 22 subscriptions need nothing.

**[measured]** `PubSubConfiguration.setSubscription(Map<String, Subscriber>)` and
`computeMaxAckExtensionPeriod(subscription, project)` exist in 8.1.0, so per-subscription overrides work:
`spring.cloud.gcp.pubsub.subscription.<name>.max-ack-extension-period`. Raising it globally instead would
let *any* stuck message hold its lease for 4 hours, so per-subscription is the right shape.

Needed: 14400 on `Otp2GraphBuildQueue`, `Otp2BaseGraphBuildQueue` and `Otp2BaseGraphCandidateBuildQueue`.
Whether `Otp2GraphCandidateBuildQueue` should join them is a question for whoever owns graph builds; the
migration should preserve today's 1 h rather than silently fixing it, and raise it separately if it is a bug.

### There are ten leaders, not one, and the old RBAC comment was wrong about why

**[measured, off the 4.21.0 jar]** With `leaseResourceType` at its default of `Lease`,
`NativeLeaseResourceManager` names the Lease after the *lock group* - the `master:` lock name - passed through
`toValidKubernetesID`, which is `toLowerCase()` then `replaceAll("[^a-z0-9-.]", "-")`. So marduk held ten
separate Leases (`gtfsexportmergedqueue`, `otp2graphbuildqueue`, `nightlyvalidation`, and so on), and
leadership for different routes could sit on different pods. The claim in `helm/marduk/templates/rbac.yaml`
that `camel.cluster.kubernetes.config-map-name` "names the Lease" was **false**: that property sets
`kubernetesResourceName`, which the Lease-type manager never reads. So was the doc-level claim that "one pod
does all the batching" - the GTFS aggregator's leader and an OTP2 aggregator's leader were independent.

**The RBAC did need changing, and an earlier version of this document said it did not.** The existing Role
granted `create, get, update, list` on `coordination.k8s.io/leases`, which is enough for camel-master's
`NativeLeaseResourceManager` (an update) and **not** enough for fabric8's `LeaderElector`, which renews with a
**PATCH**. Observed on the local cluster: the Lease was created, leadership was acquired, and `renewTime`
never advanced while every renewal logged a 403. In production that acquires leadership once and then flaps as
soon as the 30 s lease expires, with the scheduled jobs firing erratically - the leader-flapping failure
antu's writeup blames for a reverted release. `patch` is in the rule now, which is an addition and so
permitted in this release, and the comment in `rbac.yaml` explains which implementation needs it.

Keep `camel.cluster.kubernetes.enabled` in the ConfigMap: that is the key whose absence makes camel-master
4.21.0 throw `IllegalStateException("No cluster service found")` and fail the *previous* version's context
startup, which is what an image-only rollback would run.

### There is no HPA, and no consumer is leader-gated

**[measured]** `replicas: 2` with `forceReplicas: 2`, so pod count is fixed and the guide's "autoscaling
changes character" section does not apply. All 22 consumers run in both pods; leadership gates only the
`@Scheduled` methods, so of the two pods one serves every batch and losing it moves that work rather than
parallelising it.

### The retry behaviour is untested in the direction that matters

**[measured]** Production runs 3 in-process redeliveries; `src/test/resources/application.properties` sets 0.
So the suite proves the pipeline works *without* the retry production leans on, and would not notice the
replacement dropping it. `RetryPolicyTest` therefore drives the mechanics with non-default values, for the
same reason antu's `spring.task.scheduling.pool.size` test had to set a non-default value to stop being
`1 == 1`.

## The mapping

Complete. Each route builder was deleted in the same change that replaced it, so there was never a period
where a Camel route and a consumer both read the same subscription.

| Was | Is |
| --- | --- |
| `Exchange` | `MardukMessage` |
| `interceptFrom` MDC interceptor + `onCompletion` cleanup | `MardukMdc`, set by the consumer base class |
| `errorHandler(defaultErrorHandler()...)` | `RetryPolicy`, around the Chouette HTTP calls and the blob-store calls, and nothing else - see below |
| `interceptSendToEndpoint` attribute map + `OutboundFilteringHeaderFilterStrategy` | `PubSubAttributes.toAttributes` |
| the project in each `google-pubsub:` URI | `MardukQueues` |
| `google-pubsub:` consumers | `MardukPubSubConsumer` on `AbstractEnturGooglePubSubConsumer` |
| `to("google-pubsub:...")` | `MardukPubSubPublisher` |
| `AutoCreatePubSubSubscriptionEventNotifier` | `PubSubPublishTargets` |
| Camel's shutdown timeout | `InFlightWork` + `PubSubDrain` (`SmartLifecycle`) |
| `master:` via `singletonFrom()` | `LeaderElection` on a Kubernetes Lease |
| `quartz:` | `@Scheduled` on the `taskScheduler` from `SchedulingConfig` |
| `org.apache.camel.spi.IdempotentRepository` | `no.rutebanken.marduk.repository.IdempotentRepository` |
| `direct:updateStatus` + `JobEvent.providerJobBuilder(Exchange)` | `JobEventPublisher.reportProviderJob` |
| `DamuExportGtfsStatusRouteBuilder` | `DamuGtfsExportStatusConsumer` |
| `http:` producers against Chouette | `ChouetteClient` |
| `quartz://marduk/chouetteRemoveOldJobsQuartz` | `ChouetteJobCleanup.removeOldJobsOnSchedule`, `@Scheduled` |
| `FetchOsmRouteBuilder` + `quartz://marduk/fetchOsmMap` | `OsmMapFetcher`, `@Scheduled` |
| `MonitorCandidateBaseGraphBuilderVersionRouteBuilder` | `CandidateBaseGraphBuilderMonitor`, `@Scheduled` |
| `InboundQueueRouteBuilder` | `MardukInboundQueueConsumer` |
| `FileClassificationRouteBuilder`'s consumer | `FileClassificationConsumer` |
| `direct:antuNetexPreValidation`, `direct:antuNetexNightlyValidation` | `NetexPreValidation` |
| `direct:setNetexValidationProfile` | `NetexValidationProfiles.profileFor` |
| `AdminRestRouteBuilder` (REST DSL over `platform-http`) | `AdminRestController` (Spring MVC) |
| `HealthRouteBuilder` | `HealthController` |
| `camel-openapi-java` generating `/services/openapi.yaml` from the REST DSL | springdoc, same path |
| `direct:uploadFilesAndStartImport` (split over servlet parts) | `TimetableFileUploader`, called per file by the controller |
| `MardukAuthorizationService`'s `Exchange` overloads | the plain overloads; the request thread has the security context |
| `direct:chouetteGetJobs*`, `direct:chouetteCancelJob*`, `direct:chouetteClean*` | `ChouetteJobs` |
| `ChouettePollJobStatusRoute` | `ChouetteJobPoller`, `ChouetteValidationReport` |
| `ChouetteImportRouteBuilder` | `ChouetteImportConsumer`, `ChouetteImportResultHandler` |
| `ChouetteValidationRouteBuilder` | `ChouetteValidationConsumer`, `ChouetteValidationResultHandler`, `ChouetteValidationTriggers` |
| `quartz://marduk/nightlyValidation`, `quartz://marduk/chouetteValidateLevel2` | `ChouetteValidationSchedules`, `@Scheduled` |
| `NightlyValidationFileProcessor` as a Camel `Processor` | the same class, taking a `MardukMessage` |
| `ChouetteExportNetexRouteBuilder` | `ChouetteNetexExportConsumer`, `ChouetteNetexExportResultHandler` |
| `ChouetteExportNetexBlocksRouteBuilder` | `ChouetteNetexBlocksExportConsumer`, `ChouetteNetexBlocksExportResultHandler` |
| `ChouetteTransferToDataspaceRouteBuilder` | `ChouetteTransferConsumer`, `ChouetteTransferResultHandler` |
| `direct:antuNetexPostValidation`, `direct:antuNetexBlocksPostValidation` | `AntuValidation.requestPostValidation` (was `NetexPreValidation`) |
| the Camel aggregators + `IdleRouteAggregationMonitor` + `quartz://marduk/checkAggregation` | `BatchedRequests` (a Postgres table) + `BatchRunner` |
| `FileUploadRouteBuilder` | `TimetableFileUploader` |
| `IdempotentFileFilterRoute` + Camel's `idempotentConsumer` | `DuplicateFileFilter` |
| `MardukFileUtils.drainToStreamCache` + `camel.main.streamCachingSpoolEnabled` | a temporary file the uploader owns |
| `GtfsMergedExportRouteBuilder` | `MergedGtfsExportConsumer`, `MergedGtfsExport`, `DamuGtfsMergeStatusConsumer` |
| `AntuNetexValidationStatusRouteBuilder` | `antu/AntuValidationStatusConsumer`, `antu/PrevalidatedDataset` |
| `AshurFilteringStatusRouteBuilder`, `ServicelinkerEnrichmentStatusRouteBuilder`, `ExportNetexBlocksQueueRouteBuilder` | `experimental/*Consumer`, `experimental/ExperimentalImportPath` |
| `NetexMergeChouetteWithFlexibleLineExportRouteBuilder`, `PublishMergedNetexRouteBuilder`, `UploadDatedExportRouteBuilder` | `netex/NetexFlexibleLinesMergeConsumer`, `netex/PublishMergedNetexConsumer`, `netex/MergedNetexPublication`, `netex/DatedExportUpload` |
| `NetexFlexibleLinesExportRouteBuilder`, `NetexFlexibleLinesImportRouteBuilder` | `flexlines/FlexibleLinesExportConsumer`, `flexlines/FlexibleLinesImport` |
| `AbstractChouetteRouteBuilder`, `ExperimentalImportHelpersExchangeAdapter` | deleted; nothing left to serve |
| `Otp2*RouteBuilder` (4) | `otp/Otp2*Build*`, `otp/Otp2MergedNetexExport`, `otp/OtpGraphs` |
| `IdempotentFileFilterRoute`, `FileUploadRouteBuilder` | `upload/DuplicateFileFilter`, `upload/TimetableFileUploader` |
| `routes/blobstore/*` (5) | the blob store services, called directly |
| `StatusRouteBuilder` (`direct:updateStatus`) | `JobEventPublisher` |
| `CommonFileRoutesBuilder` (`direct:cleanUpLocalDirectory`) | a `finally` in each caller |
| `CamelConfig`, `MardukMetricsConfig`, `AutoCreatePubSubSubscriptionEventNotifier` | deleted with the Camel context |
| `ExchangeBridge`, `CamelRouteInvoker` | deleted; the transitional doors are closed |
| `JobEvent.ExchangeStatusBuilder` | `JobEvent.MessageStatusBuilder` |
| `BaseRouteBuilder`, `TransactionalBaseRouteBuilder`, `App extends RouteBuilder` | nothing; `App` is a plain `@SpringBootApplication` |
| the `{@code Location}` handling every submit route repeated | `ChouetteJobSubmission` |
| the routing slip's `toD("${header...RoutingDestination}")` | `ChouetteJobResultHandler`, keyed by the same header value |

`RetryPolicy` is deliberately **not** a like-for-like replacement for the error handler. Camel's
`defaultErrorHandler` re-ran the whole route, which meant re-running every side effect that had already
succeeded - a second job event, a second submission to Chouette. `RetryPolicy` wraps the individual call that
is worth retrying, and never a whole handler: it is applied around the outbound Chouette HTTP calls and the
blob-store calls, both of which are safe to repeat because they are either idempotent or start over from bytes
already in the store. Anything not covered by one of those is a PubSub redelivery instead, which re-reads the
dataset from the blob store and so starts from a known state.

The shape is one call wrapped in place, with a description for the log line:

```java
JobResponse response = retryPolicy.call("submit the import to Chouette", () -> chouetteClient...);
retryPolicy.run("upload the merged NeTEx", () -> blobStore...);
```

`call` returns a value, `run` is the void form; both take the description first. The delays are the ones Camel
computed from `marduk.camel.redelivery.*` - 5 s, 15 s, 45 s by default - taken synchronously on the calling
thread, and the sleep is interruptible so a pod being shut down does not spend a further minute per in-flight
failure. Wrapping something wider than a single call is the mistake this class exists to avoid.

## Deliberate behaviour changes

- **A `wireTap` became a synchronous call.** The NeTEx export notification ran on another thread with its
  errors swallowed. It now runs in-process, on a copy of the message so its `removeAllHeaders` still cannot
  reach the main flow, and a failure fails the publication. It publishes before the graph build and the damu
  export, so a failure retries before anything expensive has happened, and the only realistic cause is PubSub
  being unavailable - in which case the later publishes would fail too.
- **`AntuValidation.requestPostValidation` is not used by the antu status or merge paths**, deliberately. It
  overwrites `DATASET_REFERENTIAL` from the provider, which would un-prefix `rb_tst` in the ashur flow, and it
  reports `PREVALIDATION` rather than the post-validation action the caller needs. Each request is built where
  it is made. Consolidating them needs a stage-plus-profile-plus-bucket-plus-action parameterisation, which is
  a change of its own.

- **A generated correlation id now reaches the log lines that follow it.** Camel's `interceptFrom` set the
  MDC at route entry, before a route could fill in a missing correlation id, so those lines went out
  unlabelled. The converted consumer sets the MDC again after generating one.
- **An unrecognised damu export status is logged.** It was silently acked and dropped before, because the
  `choice()` had no `otherwise()`. Still dropped - nacking a status no version can handle would only
  redeliver it forever - but no longer silent.
- **The quartz lenient-fire-time guard is gone rather than reimplemented.**
  `BaseRouteBuilder.shouldQuartzRouteTrigger` re-checked that the fire time was within
  `quartz.lenient.fire.time.ms` of a scheduled time, because a `master:` route resumed on a leadership
  change and quartz re-fired a trigger it considered missed. A Spring scheduled task has no such resume, so
  there is nothing to guard against.
- **`autoStartup` gates the schedule, not the operation.** For the Chouette job cleanup the flag used to
  decide whether the quartz route started, which also made the admin endpoint unreachable. It now only
  skips the scheduled firing, so the manual cleanup stays available. Pinned by a test.

- **Every admin action carries a correlation id and a username.** The routes set a fresh correlation id on
  about half of the admin endpoints and left the rest with none, so their nabu job events had a null id and
  their log lines no MDC. `USERNAME` came from `direct:setUsername` inside each authorization route, which
  every endpoint went through; the controller does the same for all of them, uniformly.
- **Authorization is checked before the provider is looked up, everywhere.** `direct:adminChouetteClean` and
  `direct:adminChouetteTransfer` validated the provider first, so an unauthorized caller learned whether a
  provider id exists. The other endpoints authorized first; the controller does that for all of them.
- **No hand-written endpoint declares `produces`.** The REST DSL declared it and
  `camel.component.platform-http.serverRequestValidation=false` stopped Camel enforcing it. Spring MVC
  enforces `produces` at the mapping, and Ninkasi sends `Accept: application/json` to every endpoint
  including the ones that answer with text/plain - which would have been a 406 on every command. The content
  type is set on the response instead, which also stops Spring negotiating it away. Guarded reflectively:
  `AdminRestControllerIntegrationTest` walks `RequestMappingHandlerMapping.getHandlerMethods()` and fails on
  any marduk mapping whose produces condition is JSON-incompatible, so a new endpoint is covered without
  anyone remembering to list it. Handler methods declared by the generated interfaces under
  `no.rutebanken.marduk.rest.openapi.api` are skipped and the skipped set is asserted to be exactly three, so
  the exemption cannot widen unnoticed. It exists because the generated partner API genuinely declares
  `application/x-octet-stream, text/plain` on `download`, inherited from master - the published spec is the
  contract there, and that 406 exposure predates this work.
- **`POST /services/timetable_admin/upload/{codespace}` answers `application/json`.** The route marshalled
  its body to JSON while the REST DSL declared `produces(PLAIN)`, so the response said text/plain and
  contained JSON. The body is unchanged.
- **An empty NeTEx export always gets its own error code now.** The routes read Chouette's failure off the
  exchange body, which by the time `direct:processFailedExport` ran held the *validation* report whenever the
  job produced one - so `ERROR_NETEX_EXPORT_EMPTY` was set only for exports without a validation report. The
  poller passes the failure code to the handler, so it is set whenever Chouette reports it. The code only
  appears on the job event; nothing routes on it.
- **The post-validation profile is chosen once, not twice.** The two post-validation routes set
  `EnturValidationProfile` to the plain timetable profile and then called
  `direct:setNetexValidationProfile`, which overwrote it with the codespace's profile. Only the second ever
  took effect.
- **`errorHandler(noErrorHandler())` on the import submission is gone.** It was there so a failed Chouette
  call retried the *whole* route, because a multipart body cannot be re-sent once its stream is consumed. A
  consumer that throws nacks the message, and the redelivery re-reads the dataset from the blob store, which
  is the same guarantee without the special case.
- **The poll log lines no longer name the PubSub message id.** The route put it in a `PUBSUB_MESSAGE_ID`
  header purely to log it; the consumer library does not hand the id to the handler. The correlation id and
  the Chouette job id are on every one of those lines and identify the same work.
- **The three `line_statistics` endpoints are gone.** `direct:chouetteGetStats`,
  `direct:chouetteRefreshStatsCache` and `direct:chouetteGetStatsSingleProvider` lost their consumers in
  "Remove support for chouette statistics" (674aeaec); the REST routes and their `.to()` calls stayed. Camel
  resolves `direct:` at send time, so this never failed at startup - every request to those three has
  returned a 500 since. Now a 404.
- **Timetable file listings keep epoch-millis timestamps.** Camel's REST binding wrote dates as timestamps;
  Spring's `ObjectMapper` writes ISO strings. `BlobStoreFiles.File` and `OtpGraphsInfo.OtpGraphFile` now say
  `@JsonFormat(shape = NUMBER)` explicitly rather than relying on the writer's default. Pinned by
  `fileTimestampsStayEpochMillis`.
- **JSON field order inside an object changed.** Camel's Jackson and Spring's order the properties of
  `BlobStoreFiles.File` differently. Verified against the local cluster: same files, same fields, same
  values, byte-identical length, only the order within each object differs.
- **`/services/**` is served by Tomcat's request threads, not a Camel worker pool.**
  `spring.task.execution.pool.core-size=32` and `spring.mvc.async.request-timeout=300s` existed for
  platform-http's async handling and are gone; the limit is `server.tomcat.threads.max` (200) now.

## Deliberate divergences from master

Everything above is a difference the conversion produced. The two below are differences that were *chosen*
after comparing the branch against master. A future maintainer needs to be able to tell these from an
accident of the migration, which is the only reason they get their own section.

**An invalid `clean` filter is a 400 rather than a 500.** `POST /services/timetable_admin/clean/{filter}`
accepts `all`, `level1` and `level2`. Master validated the filter with `.validate(header("filter").in(...))`
*inside* the parallel split (`ChouetteImportRouteBuilder.java:86`), so a typo raised a
`PredicateValidationException` per sub-exchange, which reached the parent exchange after redeliveries and came
back as a **500**. Master's own REST DSL corroborates it: the declared error message for that response is
literally "Internal error - check filter".

**[measured, off the Camel 4.21.0 sources]** The reason it propagated is worth writing down, because the
opposite is easy to assume: `Splitter.process()` installs `new UseOriginalAggregationStrategy(exchange, true)`
whenever no aggregation strategy is configured - Camel's own comment on that line is "keep the original and
propagate exceptions" - and that strategy calls `original.setException(exception)` on the parent.
`stopOnException` is a *different* control: it decides only whether the remaining sub-exchanges still run, not
whether the failure reaches the caller. So an un-`stopOnException`'d parallel split still fails its parent.

`ChouetteJobs.cleanAll` selects on the filter on the calling thread, before the fan-out, and throws
`IllegalArgumentException` on anything else, which the controller maps to 400 - pinned by
`AdminRestControllerIntegrationTest` (`clean/level3` answers 400). Kept deliberately: a malformed path
variable is a client mistake, and 500 tells the caller to look in marduk's logs for a bug that is not there.
No real client can reach it either, since Ninkasi sends one of the three fixed values from a UI control.

**`IllegalArgumentException` is mapped asymmetrically, on purpose.** On the internal admin API
(`AdminRestController`) an `@ExceptionHandler` turns it into a 400. On the external partner API
(`AdminExternalRestController`) there is deliberately no such handler, so it falls through to 500 as it did on
master, because that endpoint's published OpenAPI spec documents only 200, 401, 403, 404 and 500. This is not
an inconsistency waiting to be tidied up - the published partner contract is the reason, and there is a test
on each side saying so.

## Notes on individual conversions

### One bug fixed rather than reproduced

**[measured]** `direct:fetchOsmMapOverNorway` stored the OSM checksum *before* downloading and verifying the
archive. A truncated download threw `Md5ChecksumValidationException`, which
`onException(MardukException.class).handled(true)` swallowed - leaving the stored checksum matching the
source, so the next hourly check saw nothing to do and the stale map survived until the source published a
new file. Up to a day of silently stale OSM data, and the retry the schedule exists for was useless.

The replacement writes the checksum only after the archive verifies and is stored. This is the one place the
migration changes behaviour to fix something rather than to preserve it; nothing outside marduk reads either
object, so the change is contained. `OsmMapFetcherTest` has the regression test, verified by reinstating the
old ordering.

Also noticed while converting it: the code default for the schedule was `0+*+*/23+?+*+MON-FRI`, which has
`*` in the *minute* field and would fire sixty times an hour during two hours of the day. It was never in
effect because the ConfigMap always sets the value. The new default matches the deployed shape instead.

### Two job events that are built and never reported

Both predate the migration and both are preserved, because reporting them now would put events in nabu that
have never been there:

- **`direct:startDamuGtfsExport`** built an `EXPORT`/`PENDING` event with no `updateStatus` after it. The only
  surviving effect is that `build()` stamps the event onto the message as `RutebankenSystemStatus`, and that
  header travels to damu - so the build is load-bearing even though the report never happened.
- **`direct:ashurNetexFilterAfterPreValidation`'s `otherwise`** built a `FILTERING`/`CANCELLED` event, and
  every caller then `.stop()`ed without reporting. It fires whenever `FileCreatedTimestamp` is absent, so a
  cancelled filtering has never reached nabu.

### One path that reported an empty message body

In `direct:antuNetexValidationComplete`, the `PREVALIDATION OK` event for the experimental import was built
*inside* a `filter(!chouette.enablePreValidation)`, but the route's trailing `to("direct:updateStatus")` ran
either way. With the property `true`, that published whatever the previous step had left as the body - an
empty string - to `JobEventQueue`.

`JobEventPublisher` builds and publishes atomically, so "publish a body that is not a job event" cannot be
expressed any more; that path now reports nothing. `enablePreValidation` is `false` in dev, tst and prd, so
no deployed environment takes it.

### A regression guard that moved rather than disappeared

`AntuNetexValidationStatusRouteBuilderTest` had one test spanning four route builders: a FLEX post-validation
on an experimental codespace reaching the merged post-validation through the latest-Ashur-output fallback. It
could only be one test because it was all one Camel context. It is now two, meeting at the queue between the
two consumers: `AntuValidationStatusConsumerTest` asserts that a completed FLEX post-validation publishes to
`ChouetteMergeWithFlexibleLinesQueue`, and `NetexFlexibleLinesMergeConsumerTest` asserts that consuming it
for an experimental codespace takes the Ashur fallback and requests a merged post-validation.

### Preserved even though it looks wrong

The merged GTFS status handling reports `STATUS_MERGE_STARTED` under a **new** correlation id while `OK` and
`FAILED` reuse the incoming one, so nabu gets a STARTED event under an id no later event shares and the merge
job never reaches a terminal state under its own id. Preserved, and pinned by a test that says so.

On `OK` and `FAILED` the incoming `CORRELATION_ID` also overwrites whatever `initSystemJob` read from
`RutebankenSystemStatus`, and a message without one makes `build()` throw and the message nack. Preserved.

`direct:checkScheduledJobsBeforeTriggeringNextAction` asked Chouette for
`/jobs?timetableAction=importer&status=SCHEDULED&status=STARTED`. Everywhere else the parameter is `action`,
which is what Chouette documents, so `timetableAction` most likely filters nothing and the answer covers
every unfinished job in the dataspace - meaning a queued export can postpone validation as well as a queued
import. `ChouetteJobs.hasQueuedImports` sends the same query. Narrowing it would start triggering validation
in cases that are skipped today, which is a change to when datasets are validated, not a cleanup.

### The aggregators become rows, as decided

The four OTP graph builds and the merged GTFS export each collected many requests and served them with one
job. Camel did that in heap: the messages were held unacknowledged until the job finished, which is why those
subscriptions carried `maxAckExtensionPeriod=14400` - four hours - and why a pod dying mid-build lost the
whole batch unless PubSub happened to redeliver in time. Completion was triggered at a hundred requests, on a
timeout, or when `IdleRouteAggregationMonitor` saw the route idle, poked every five seconds by
`quartz://marduk/checkAggregation`.

`BatchedRequests` writes each request to `batched_request` and the consumer acknowledges its message at once.
A leader-gated `@Scheduled` tick claims everything waiting, runs the job for the newest request, and deletes
the rows - or, if the job *throws*, clears the claim so the next tick retries.

Note what that does and does not mean per job. The merged GTFS export lets a failure propagate, so it is
retried. Both OTP2 graph builds catch their own build failure, report the job FAILED and return normally, so
their rows are deleted and nothing is retried - deliberately, matching the routes' `doCatch(Exception)`
followed by `stop()`. `Otp2NetexGraphBuild` also returns early, and so consumes the batch, when there is no
stop place export to build from; a failure in the later steps does propagate. And a pod that dies between the
claim and the delete strands its rows with a non-null `claim_id` that no tick will look at again - see the
debug note in `CLAUDE.MD`.

What changes:

- **No ack extension anywhere.** The 4h setting is gone, along with the deferred-acknowledgement helpers in
  `BaseRouteBuilder`.
- **A batch survives a restart.** This is the point of the change: the rows are still there after a redeploy.
- **The claim is decided by an `UPDATE`, not by leadership.** Leader election makes two runners unlikely; a
  handover can still overlap, so the statement is what picks the winner.
- **The completion triggers collapse into the tick interval.** The graph builds tick every 5s, matching the
  old idle check. The merged GTFS export keeps both of its triggers: the tick reuses
  `gtfs.export.aggregation.timeout` and the size check reuses `aggregation.completionSize`.
- The migration is portable DDL - no `bigserial`, no `uuid` column, no partial index - because the tests run
  it on H2 and the deployments on PostgreSQL.

### Only the headers the aggregator kept reach damu

`HeaderPreservingGroupedMessageAggregationStrategy` kept exactly five headers from the newest request and
dropped the rest, so those five are the attribute set damu sees today. A durable batch keeps whole messages,
and passing one straight on would newly publish the admin caller's `USERNAME`, or a triggering codespace's
file handles, onto `GtfsRouteDispatcherTopic` - a topic other services read and the one with a dead-letter
policy. `MergedGtfsExport` copies the five onto a fresh message instead.

Two behaviour notes on that conversion:

- `gtfs.export.autoStartup` gates the schedule, not the operation, as it does for the Chouette job cleanup.
  Not quite equivalent: the admin endpoint publishes to the queue rather than calling the export, so with the
  flag off an admin-triggered export is recorded and then served only once `aggregation.completionSize`
  requests accumulate. Under Camel the queue was not consumed at all with the flag off, so the endpoint was
  entirely dead - this is better, not identical.
- Serving the batch is `synchronized`. `gtfsExportExecutorService` had a pool size of one so that only one
  export ran at a time, and with two triggers - the tick on the scheduler's thread, the size check on a
  consumer's - `fixedDelay` alone no longer guarantees it.
- `direct:exportMergedGtfs` logged `Start export of merged GTFS file: ${header.RutebankenFileName}`, but the
  aggregation strategy had already dropped `FILE_NAME`, so that line always logged an empty name. Dropped
  rather than reproduced.

### The upload path no longer needs Camel's stream caching

`direct:uploadFileAndStartImport` read the multipart body from a Camel `StreamCache`, which kept small files
in heap and spooled larger ones to disk under `camel.main.streamCachingSpoolEnabled`. The uploader now drains
the request to a temporary file it deletes in a `finally`: the duplicate digest and the blob upload read the
same bytes twice without a spool strategy, and there is one code path at any size on an endpoint that accepts
150MB. `MardukFileUtils.drainToStreamCache` is gone.

Camel's `idempotentConsumer` went with it. The interesting part was never the consumer but what happens when
the work *after* it fails: the route removed the key again through `onCompletion().onFailureOnly()`, but only
if the exchange was not itself a duplicate - otherwise it would have released the earlier upload's key.
`DuplicateFileFilter.Claim` carries that distinction and `release` honours it.

### A known trap in the test suite, left alone deliberately

`TestApp` spells out `@ComponentScan`, which *replaces* Boot's default exclude filters rather than adding to
them. One of those defaults is the `TypeExcludeFilter` that keeps a test's nested `@TestConfiguration` out of
every other test's context. Without it, `AdminRestControllerIntegrationTest`'s beans - including the suite's
only `AuthorizationService` - reach every Spring test. Putting the filter back means giving every Spring test
its own authorization bean, which is a test-infrastructure change of its own; it is noted here rather than
done as part of the Camel removal.

### A test that was switched off, and why

`ChouettePollJobStatusMardukRouteIntegrationTest.testValidationReportResultNOK` was commented out with
`//@Test`. The verdict is `$.validation_report.check_points[?(@.severity == 'ERROR' && @.result == 'NOK')]`,
and `getValidationReportResponseNOK.json` was still in an older Chouette shape - a `tests` array instead of
`check_points` - so the predicate matched nothing and the report came back OK. The code was right and the
fixture was stale. `ChouetteValidationReportTest` has the case back, against a fixture in the shape
`getValidationReportResponseOK.json` uses, with one checkpoint that is both `ERROR` and `NOK`.

### Cron schedules

The quartz trigger URIs used `+` as the field separator. The ConfigMap now renders **both** spellings from
the same helm value - `chouette.remove.old.jobs.cron.schedule` for the Camel version and
`chouette.remove.old.jobs.cron` with spaces for Spring - via `replace "+" " "`, so the two cannot drift and
the old key survives a rollback.

**[measured]** All nine cron values across the three environments transform safely, and Spring's
`CronExpression` accepts `?` in both the day-of-month and day-of-week positions, behaving as `*`. That is
what lets one helm value render both spellings.

**[measured]** Not every quartz spelling survives, though. The candidate graph monitor's code default was
`0+/5+*+*+*+?`, and Spring rejects the bare `/5` step that quartz accepts - it needs `*/5` or `0/5`. That
value has **no ConfigMap override**, so the default is what runs, and a naive transform would have failed
the context at startup and crash-looped every pod.

`CronScheduleTest` therefore checks two things: every cron in the helm files, and every default embedded in
a `@Scheduled` annotation in the source. The second exists because of the `/5` case - a code default is
invisible to a test that only reads helm.

Worth noting while checking them: `osm.fetchCronSchedule` is `0+11+*+*+*+?`, which is **hourly at 11 minutes
past**, not daily.

## What the local cluster showed

Run on `local-k8s` at two replicas with Kubernetes Lease election, which is closer to production than a single
replica plus Camel's file lock. Captured the old responses first, redeployed, compared.

**Two replicas on a freshly built image, `Started App in 3.7s`** (about 5 s with Camel), zero ERROR lines on
either pod once leftover messages from earlier testing were purged.

- `/services/health` byte-identical. `routing_graph/graphs` and `{providerId}/files` byte-identical.
  `export/files` identical as objects with the same byte length - only the field order inside each object
  differs.
- The API document is served at `/services/openapi.yaml` and `/services/timetable_admin/openapi.yaml`, with
  the old title and version. Nothing lost from the documented operation set except the three dead
  `line_statistics` endpoints, and four gained (`/services/health` and the three timetable-management
  endpoints, which the REST DSL could not see).
- `scripts/smoke-upload.sh` passes through both multipart endpoints: stored blob size equals uploaded size.
- Every command endpoint answers 200 with `Accept: application/json`; an unknown graph type is 400, an unknown
  provider 404, the removed `line_statistics` paths 404.
- An upload traced end to end across both pods produced the same seven nabu job events as the pre-migration
  baseline, under one correlation id: stored and deduped on one pod, classified on the other.
- Leader election acquired the lease cleanly, `renewTime` advanced, the five-minute monitor fired on the
  leader only, and only the holder served batches. 17 job events per import landed in nabu's `event` table
  with the right action, state, referential and provider - the same 17, in the same order, at one replica and
  at two.
- The full batch chain: two `gtfs-merged-export` requests recorded as rows, the leader claimed both, **one**
  merge request went to damu, damu merged and notified, `DamuGtfsMergeStatusConsumer` logged STARTED then OK,
  and the rows were deleted. One job for two requests, which is what the aggregator was for.
- `taskScheduler` resolved unambiguously: no `TaskSchedulerRouter` line and no WARN at startup, so the pool
  size property is not being silently ignored.
- `POST /services/map_admin/download` times out locally because it genuinely starts downloading the Norway OSM
  extract from geofabrik. Working as intended.

**One production bug found**, the missing `patch` verb on `leases` - see *There are ten leaders* above.

**A second bug, caught by reading the rig's config rather than by running it.** `OsmMapFetcher.fetch()` read
the whole OSM extract into a `byte[]`. The Norway file is well over a gigabyte and the pod limit is 1280Mi;
Camel had spooled it to disk through the route's `.streamCaching()`. It streams to a temp file and hashes from
there now. `ChouetteClient` grew a `downloadTo` for the same reason, and `getBytes` is documented as
small-payloads-only, because the export paths fetch hundreds of megabytes.

The OTP2 graph builds cannot run in this rig - a real build needs an OTP jar and a Kubernetes job - so
`otp2.graph.build.autoStartup` stays false there and those requests simply accumulate as rows. That is the
same outcome the flag had under Camel, where the route did not start and the messages stayed on the
subscription; the difference is that they are now acknowledged and durable rather than an unacked backlog.

**Pre-existing, not caused by this work:** an empty message is stuck on `MardukInboundQueue` in the local
emulator and fails with a `NullPointerException` from `BlobId.of` on every redelivery. It starts before any
import and has no attributes at all. Left alone; it is local emulator state, though it does show that a
message with no file handle poisons that path indefinitely.

## Decisions taken

### The aggregators become durable request rows, not an in-heap batch

Both aggregators are replaced by the same shape: the consumer inserts a request row and acks immediately,
and a leader-gated scheduled task claims all pending rows and runs the work once for the batch.

What that deletes, rather than reimplements: `aggregate()` with its `completionSize` and
`completionTimeout`, the aggregate controllers, `IdleRouteAggregationMonitor`,
`AggregationCheckerRouteBuilder`, `HeaderPreservingGroupedMessageAggregationStrategy`,
`removeSynchronizationForAggregatedExchange`/`addSynchronizationForAggregatedExchange`, and the
`maxAckExtensionPeriod=14400` on the three OTP2 subscriptions - no message is held while a build runs, so
the 60 minute default that both Camel and spring-cloud-gcp happen to share is enough for everything.

It also means **no consumer needs leader gating**, so all 22 can use
`AbstractEnturGooglePubSubConsumer` unchanged, and leadership only gates `@Scheduled` methods, as in antu
and damu.

The argument for it over a faithful port is in antu's own history: the Camel aggregator held pending
aggregations in the heap of whichever pod held the lease, and leader flapping lost them - which is what
sank the first jdk25/Spring Boot 4.1 attempt. Reproducing that shape would carry the same fragility into
marduk, on a service where a lost batch is a missed graph build.

The cost is one Flyway migration and a change in where an in-flight request is durable: a Postgres row
instead of an unacked PubSub message. Both versions of marduk can run side by side, because the new one
only ever adds rows to a table the old one does not know about.

### `terminationGracePeriodSeconds` is raised to 120

It was unset, so 30. Adding a value is permitted by the rule that forbids *removing* what the old version
needs. The budget it has to cover is 10 s per busy subscriber - spent, not skipped - plus the
drain, plus bean destruction. `marduk.shutdown.drain.timeout.seconds` defaults to 60 inside that.

### The Chouette poll delay is on the scheduler, and a hard kill inside it loses one poll

An accepted bound rather than an open defect, so it is written down here rather than left to be rediscovered.

`ChouetteJobPoller` polls a Chouette job by republishing the same message to `ChouettePollStatusQueue` after
`chouette.retry.delay` (30000 in the deployed ConfigMap), up to `chouette.max.retries=3000`. The delay is
taken on the `taskScheduler`, as the route's `delay().asyncDelayed()` was, so one slow Chouette job does not
occupy a consumer thread for its whole lifetime - a 3000-poll job would otherwise hold one for a day.

The consequence is that the inbound message is acknowledged before the delay elapses.
`AbstractEnturGooglePubSubConsumer` acks unconditionally as soon as `onMessage` returns and exposes no manual
or deferred ack, so there is no way to hold the poll message across the delay. `reschedule` registers the
pending republish with `InFlightWork` before scheduling it and closes the token in a `finally`, so
`PubSubDrain` waits for it: a **graceful** shutdown - a rolling deploy, a scale-down, anything that sends
SIGTERM - drains the republish and the poll chain survives. That is the common case and it is covered.

A **hard** kill inside the delay window is not: SIGKILL after the grace period, node loss, or the OOM killer
takes the scheduled task with the JVM, and the message is already acked, so that poll is simply gone. The
Chouette job is never reaped afterwards and the import stalls silently. What an operator sees: a nabu job that
stays in STARTED with no terminal event, a Chouette job that finished normally on Chouette's side, and nothing
further on `ChouettePollStatusQueue` for that correlation id - so no error anywhere, just a chain that stops.
The recovery is to re-trigger the action from Ninkasi.

The alternative - keeping the delay inside `handle` so the message stays unacked - was rejected: with
`concurrentConsumers` at 1 it would serialise every poll on that subscription behind a 30 s sleep, so one
in-flight job would starve every other job's polling. Losing at most one poll per hard kill is the cheaper
bound, and the window is 30 s out of a poll chain that can run for a day.

### The library's post-failure sleep is shortened, not removed

`AbstractEnturGooglePubSubConsumer` sleeps `entur.pubsub.consumer.retry.delay` on the callback thread after
a failure, which is also what keeps its subscriber from terminating during shutdown. It cannot go to zero:
**[measured]** 21 of marduk's 25 subscriptions have `retry_policy.minimum_backoff = "10s"`, but
`MardukAggregateGtfsStatusQueue` - which marduk consumes - has no `retry_policy` at all, so PubSub would
redeliver a persistent failure there with no backoff and a zero sleep would spin. Setting it to about a
second bounds the shutdown cost while leaving the subscription-level backoff to do the throttling. Adding a
`retry_policy` to that subscription would be the better fix and is a terraform addition, so it is allowed -
but it is a separate change.

### The duplicate-file guard is the unique constraint, not the code

Worth writing down because the code reads as though it were the other way round, and the rule about a check
followed by an act applies exactly.

**[measured]** `FileNameAndDigestIdempotentRepository.add` - inherited from Camel's
`AbstractJdbcMessageIdRepository`, now inlined - runs `queryForInt` and then `insert` in one
`PROPAGATION_REQUIRED` transaction at the database's default isolation. At read-committed that is not
atomic: two pods can both see no row and both insert.

What makes it safe is the schema. `V1__Base_version.sql` declares a primary key on
`(processorname, filename)` and a unique index on `(processorname, digest)`, so the loser's insert fails and
the exception propagates - it is *not* reported as "already present". The observable behaviour is that the
unit of work fails, the message is redelivered, and the retry finds the row and reports a duplicate. That is
preserved deliberately; the constraint is the atomic claim.

The transaction is therefore about grouping the two statements, not about mutual exclusion. Anyone tempted
to "fix" `add` by widening the transaction or adding a lock should know the constraint is already doing the
work.

## Done

Camel is gone. `grep -rn "org.apache.camel" src --include=*.java` matches one javadoc line in
`IdempotentRepository`, which names the interface it replaced. `pom.xml` has no `org.apache.camel` artifact
and no `camel.version` property; the seventeen starters, the BOM and `camel-test-spring-junit5` are all out.

What went with the last of it:

- **`App` is a plain `@SpringBootApplication`.** It extended `RouteBuilder` only to set the shutdown timeout,
  MDC logging, breadcrumbs and message history on the Camel context. Waiting for the provider repository -
  the one thing in `configure()` that still matters, because every consumer needs it to turn a provider id
  into a referential - is a `@PostConstruct` now.
- **`configureJsonPath()` is gone.** It configured Jayway JsonPath's default providers for the two
  `.jsonpath()` predicates in the routes. Both are Jackson tree navigation now, so nothing evaluates a
  JsonPath expression and the library left with `camel-jsonpath-starter`.
- **`MardukMetricsConfig` is gone.** Its only bean was a `MicrometerRoutePolicyFactory` - per-route metrics
  for routes that no longer exist. The actuator's own metrics are untouched.
- **`AutoCreatePubSubSubscriptionEventNotifier` is gone**, replaced earlier by `PubSubPublishTargets`.
- **The test bases lost Camel too.** `MardukRouteBuilderIntegrationTestBase` (`@CamelSpringBootTest`,
  `@UseAdviceWith`, the injected `CamelContext`) is deleted and its two remaining users extend
  `MardukSpringBootBaseTest` directly. `AdminExternalRestControllerIntegrationTest` drove itself through
  Camel's HTTP *producer*; it uses HttpClient5 now, like the admin one.
- **`CamelMainConfigurationTest`, `ClusterServiceConfigMapTest` and `MdcInterceptorRouteBuilderTest`** are
  deleted: they pinned Camel's stream-caching configuration, its cluster-service wiring and its MDC
  interceptor. `MardukMdcTest` already covers every behaviour the interceptor test asserted.

### What only the cluster caught

Two things passed the build and the whole test suite and still broke a pod:

- **`io.fabric8:kubernetes-client` was arriving transitively through `camel-kubernetes-starter`.** Removing
  that starter left `kubernetes-client-api` on its own. `KubernetesClientImpl` is loaded reflectively, so
  nothing failed to compile, and no test builds a real client - the test profile uses the single-node leader
  election - so the first pod to start crash-looped on `ClassNotFoundException`. Declared explicitly now, with
  `KubernetesClientOnTheClasspathTest` asserting the implementation and an HTTP transport are both present.
- **`HealthController` declared `produces = text/plain`**, the same 406 trap the admin endpoints were fixed
  for. A Kubernetes httpGet probe sends no `Accept` header and was unaffected, but anything asking for JSON
  got a 406 from the readiness endpoint.

### What deliberately still says "camel"

- **`marduk.camel.redelivery.*`** are the property names `RetryPolicy` reads. They are configuration keys on
  the wire; renaming them would silently change retry behaviour wherever they are set.
- **`PubSubAttributes` still excludes the `camel` attribute prefix.** Messages written by the previous
  version can be on the queues during a rolling deploy, and they carry those attributes.
- **`logback.xml` still emits `breadcrumb` from `%X{camel.breadcrumbId}`.** Nothing sets that key any more, so
  the field is always empty - but it was already empty for every converted component, and dropping it would
  change the log schema the collectors parse in the same release that changes everything else.
- **The ConfigMap keeps its `camel.*` block for one release.** A helm rollback restores the chart and the
  image together, but rolling only the image back is a plausible emergency action, and the old jar needs those
  keys. There is a comment saying to delete the block in the next release.

## The final sweep

What was still left once the last route builder went, and what happened to it. All of it verified against the
tree rather than assumed.

- **Dead Camel logger levels in `src/test/resources/logback-test.xml`**: `org.apache.camel`, `AdviceWith`,
  `JacksonDataFormat`, and a typo'd `org.apache.processor.DefaultErrorHandler` that never matched anything
  even before this work. Removed; they configured packages that are no longer on the classpath.
- **Three quartz trigger properties in `src/test/resources/application.properties`**:
  `chouette.stats.cache.refresh`, `chouette.stats.cache.initial.refresh` and `marduk.aggregation.checker`.
  Removed. Nothing in `src/main` or in the chart read them, and the cron values that are still live are
  pinned by `CronScheduleTest`, which reads the helm files rather than these.
- **`CLAUDE.MD` still described the architecture as Camel routes**, down to a worked "Adding a New Route"
  example and `App extends RouteBuilder`. Rewritten around consumers, `@Scheduled`, leader election and
  batching, with the traps that cost time here written down: the `produces` 406, the Lease `patch` verb, and
  batch rows that never disappear. (The file is tracked as `CLAUDE.MD`, uppercase extension, on a
  case-insensitive filesystem - `git commit -- CLAUDE.md` fails with "pathspec did not match".)
- **`pipeline/MardukHeaders.java`** held one constant, `RutebankenFileParent`, and nothing referenced it: the
  blob-store methods that took it as a Camel `@Header` take a parameter now. Deleted.
- **Roughly twenty constants in `Constants.java` were left with no reader**, most of them headers whose
  concept became a method parameter during the OTP2 and upload conversions (`FILE_PREFIX`,
  `FILE_PARENT_COLLECTION`, `TARGET_FILE_PARENT`, `OTP_REMOTE_WORK_DIR`, `OTP_GRAPH_VERSION`,
  `OTP_BUILD_CANDIDATE`, `GRAPH_COMPATIBILITY_VERSION`, `JSON_PART`, `FILE_CONTENT`, `FILE_UPLOAD_FAILED`,
  `FILE_TARGET_MD5`), plus `CAMEL_ALL_HEADERS` and `CAMEL_ALL_HTTP_HEADERS` (Camel wildcard patterns) and the
  three destination names that duplicated `MardukQueues` constants. Deleted, and `WireContractTest`'s
  exhaustive pin updated in the same commit. `FILTERING_PROFILE_AS_IS` and `FILTERING_NETEX_SOURCE_CHOUETTE`
  are unreferenced too and were **kept**: they are values in ashur's `FilterProfile` enum and its
  `NetexSource` attribute, so they are wire contract that marduk simply does not send today.
- **`MardukMessage`'s property bag lost the accessors nothing called** - the untyped and defaulting
  `getProperty`, `removeProperty`, `getProperties`. `setProperty` and `getProperty(name, Class)` stay: a
  property is the one thing that is *not* published as a PubSub attribute, and the flexible-lines merge uses
  that for the local working directory it unpacks into.
- **`routes/` still exists**, holding the DTOs, processors, `FileType` and `JobEvent` that outlived the
  routes. The package name is a misnomer now, but renaming it touches every importer for no behaviour change,
  so it belongs in its own commit.

Not Camel, so deliberately not touched: `spring-boot-starter-jersey` is declared and unused, and both REST
controllers throw `jakarta.ws.rs.NotFoundException`, each mapped to a 404 by an `@ExceptionHandler` in the
controller. Both predate this work - the pre-migration pom already declared Jersey, and the pre-migration
`AdminExternalRestController` already threw that exception - so dropping the JAX-RS dependency is a separate
cleanup with its own risk. `src/test/resources/application.properties` also still carries two `# Camel`
section comments over properties that are no longer Camel's; cosmetic, and left for whoever next edits the
file.

## What the wire contract test pins

`WireContractTest` is the guard that the removal did not change what other services see, so it is worth
knowing what it does and does not cover. Every assertion compares a hand-written expectation against values
read **reflectively at runtime** - reflection because a compile-time reference to a `static final String`
would be inlined, letting an incremental build compare two copies of the same stale literal, and because
`assertEquals(PROVIDER_ID, attributes.get(PROVIDER_ID))` moves with any rename and pins nothing.

The comparisons are exhaustive over the class or enum, not filtered by name, so **adding** a constant fails
the test as loudly as changing one. That is the point: a new header cannot reach the wire without being
pinned on purpose. Covered that way: every string constant in `Constants`, in `JobEvent`, in
`MardukQueues` and in the two damu status consumers; every constant of `JobEvent.State`, `JobEvent.JobDomain`,
`JobEvent.TimetableAction`, `FileType` and Chouette's `Status`; the JSON `JobEvent` serialises to, including
`eventTime` as an epoch-seconds decimal with full nanosecond precision; the five routing-slip literals; and
every queue-shaped string literal anywhere in `src/main/java`, which catches a destination introduced without
going through `MardukQueues`.

Two earlier gaps, closed: the destination scan used to read `google-pubsub:` endpoint URIs out of the source
and passed vacuously the moment the last route went, and three of the constant pins filtered members by name
prefix so a constant named anything else escaped. Verified against mutations: an unpinned queue literal, a
fourth damu status and a second `MardukQueues` constant holding an existing destination name all fail it.

## Rollback

Three routes back, in increasing order of what they restore.

| Route | Works? |
| --- | --- |
| `git revert` plus a full rebuild and redeploy | Yes. |
| `helm rollback` | Yes: the chart and the image move together, so the previous ConfigMap and the previous jar arrive as a pair. |
| Rolling only the image back | Yes, given `spring.flyway.ignore-migration-patterns` and the deploy ordering below. |

An image-only rollback is the plausible emergency action and the one that needs care, because the ConfigMap is
a separate unversioned object that does not move with the image. Three things make it survivable:

- **The ConfigMap keeps every old key.** The `camel.*` block is retained for exactly this reason, including
  `camel.cluster.kubernetes.enabled`, whose absence makes camel-master 4.21.0 throw
  `IllegalStateException("No cluster service found")` and fail the old context's startup. The cron values are
  rendered in both spellings from the same helm value, so the quartz-style keys the old jar reads are still
  there. There is a comment in the ConfigMap saying to delete the block in the next release.
- **`marduk.camel.redelivery.*` kept its names** for the same reason: they are configuration keys on the wire,
  and both versions read them.
- **`V2__Batched_requests.sql` would otherwise block it, and does not.** The old jar has no `V2` script, but
  the schema history table has a `V2` row, and Flyway's default `validateOnMigrate` fails startup on an
  applied migration it cannot resolve locally - a CrashLoopBackOff on Flyway validation rather than on
  anything to do with Camel. `helm/marduk/templates/configmap.yaml` therefore sets:

  ```properties
  spring.flyway.ignore-migration-patterns=*:missing
  ```

  **Deploy ordering is the whole point of that line, so do not reorder it.** The property has to be readable
  by the *old* jar, which means the chart change must ship in the same release as `V2__Batched_requests.sql`
  or in an earlier one - **never after**. Shipping them together is sufficient rather than merely lucky: helm
  applies the ConfigMap before it replaces any pod, so by the time a rolled-back image starts, the key is
  already there. Ship the migration first and the rollback window between the two releases has no way out.

  The table itself is harmless to leave behind: the old version does not know about it, and both versions can
  run side by side because the new one only ever adds rows to it.
