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