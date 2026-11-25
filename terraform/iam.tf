resource "google_storage_bucket_iam_member" "marduk_exchange_storage_iam_member" {
  bucket = var.marduk_exchange_storage_bucket
  role   = var.service_account_bucket_role
  member = var.ashur_service_account
}

resource "google_pubsub_topic_iam_member" "FilterNetexFileStatusQueuePublisherRole" {
  project = var.gcp_resources_project
  topic   = google_pubsub_topic.FilterNetexFileStatusQueue.name
  role    = "roles/pubsub.publisher"
  member  = var.ashur_service_account
}

resource "google_storage_bucket_iam_member" "servicelinker_exchange_storage_reader" {
  bucket = var.marduk_exchange_storage_bucket
  role   = var.service_account_bucket_role
  member = var.servicelinker_service_account
}

resource "google_pubsub_topic_iam_member" "ServicelinkerStatusQueuePublisherRole" {
  project = var.gcp_resources_project
  topic   = google_pubsub_topic.ServicelinkerStatusQueue.name
  role    = "roles/pubsub.publisher"
  member  = var.servicelinker_service_account
}

# Servicelinker's GitHub Actions Terraform SA (via Workload Identity Federation)
# needs roles/pubsub.subscriber (which includes pubsub.topics.attachSubscription)
# on these topics to create cross-project subscriptions from ent-servicelnk-* to ent-marduk-*.
resource "google_pubsub_topic_iam_member" "ServicelinkerInboundQueueSubscriberRole" {
  project = var.gcp_resources_project
  topic   = google_pubsub_topic.ServicelinkerInboundQueue.name
  role    = "roles/pubsub.subscriber"
  member  = var.servicelinker_terraform_service_account
}

resource "google_pubsub_topic_iam_member" "ServicelinkerStatusQueueSubscriberRole" {
  project = var.gcp_resources_project
  topic   = google_pubsub_topic.ServicelinkerStatusQueue.name
  role    = "roles/pubsub.subscriber"
  member  = var.servicelinker_terraform_service_account
}

resource "google_pubsub_subscription_iam_member" "NetexExportNotificationQueueSubscriberRole" {
  project      = var.gcp_resources_project
  subscription = google_pubsub_subscription.NetexExportNotificationQueue.name
  role         = "roles/pubsub.subscriber"
  member       = var.nisaba_service_account
}

# Damu receives the GTFS export and aggregation requests on GtfsRouteDispatcherTopic, and reports
# their outcome on DamuExportGtfsStatusQueue and MardukAggregateGtfsStatusQueue. The subscription
# and the topics are created here and damu never creates them, so it needs no Pub/Sub admin role.
resource "google_pubsub_subscription_iam_member" "GtfsRouteDispatcherTopicSubscriberRole" {
  project      = var.gcp_resources_project
  subscription = google_pubsub_subscription.GtfsRouteDispatcherTopic.name
  role         = "roles/pubsub.subscriber"
  member       = var.damu_service_account
}

resource "google_pubsub_topic_iam_member" "DamuExportGtfsStatusQueuePublisherRole" {
  project = var.gcp_resources_project
  topic   = google_pubsub_topic.DamuExportGtfsStatusQueue.name
  role    = "roles/pubsub.publisher"
  member  = var.damu_service_account
}

resource "google_pubsub_topic_iam_member" "MardukAggregateGtfsStatusQueuePublisherRole" {
  project = var.gcp_resources_project
  topic   = google_pubsub_topic.MardukAggregateGtfsStatusQueue.name
  role    = "roles/pubsub.publisher"
  member  = var.damu_service_account
}

# Damu reads the NeTEx exports, the per-provider GTFS exports and the stop places from the marduk
# bucket, and writes the GTFS exports, the aggregated GTFS datasets and the GTFS stop export to it.
# An upload replaces the existing object, which needs storage.objects.delete, so
# roles/storage.objectCreator is not enough. The bucket is not managed here, only this binding.
resource "google_storage_bucket_iam_member" "damu_storage_object_user" {
  bucket = var.marduk_storage_bucket
  role   = "roles/storage.objectUser"
  member = var.damu_service_account
}
