# Contains main description of bulk of terraform
terraform {
  required_version = ">= 0.13"
}

provider "google" {
  version = "~> 3.43"
}
provider "kubernetes" {
  load_config_file = var.load_config_file
}

# create service account
resource "google_service_account" "marduk_service_account" {
  account_id = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_resources_project
}

# add service account as member to the cloudsql client
resource "google_project_iam_member" "cloudsql_iam_member" {
  project = var.gcp_cloudsql_project
  role = var.service_account_cloudsql_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the main bucket
resource "google_storage_bucket_iam_member" "storage_main_bucket_iam_member" {
  bucket = var.bucket_marduk_instance_name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the exchange bucket
resource "google_storage_bucket_iam_member" "storage_exchange_bucket_iam_member" {
  bucket = var.bucket_exchange_instance_name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the otp graphs bucket
resource "google_storage_bucket_iam_member" "storage_graphs_bucket_iam_member" {
  bucket = var.bucket_graphs_instance_name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the otpreport bucket
resource "google_storage_bucket_iam_member" "storage_otpreport_bucket_iam_member" {
  bucket = var.bucket_otpreport_instance_name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# create key for service account
resource "google_service_account_key" "marduk_service_account_key" {
  service_account_id = google_service_account.marduk_service_account.name
}

# Add SA key to to k8s
resource "kubernetes_secret" "marduk_service_account_credentials" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
  }
  data = {
    "credentials.json" = base64decode(google_service_account_key.marduk_service_account_key.private_key)
  }
}

resource "kubernetes_secret" "ror-marduk-secret" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secret"
    namespace = var.kube_namespace
  }

  data = {
    "marduk-db-username" = var.ror-marduk-db-username
    "marduk-db-password" = var.ror-marduk-db-password
    "marduk-google-sftp-username" = var.ror-marduk-google-sftp-username
    "marduk-google-sftp-password" = var.ror-marduk-google-sftp-password
    "marduk-google-qa-sftp-username" = var.ror-marduk-google-qa-sftp-username
    "marduk-google-qa-sftp-password" = var.ror-marduk-google-qa-sftp-password
    "marduk-keycloak-secret" = var.ror-marduk-keycloak-secret
  }
}

# Create pubsub topics
resource "google_pubsub_topic" "topics" {
  count = length(var.queues)
  name  = var.queues[count.index]
  project = var.gcp_pubsub_project
  labels = var.labels
}

# Create pubsub subscriptions
resource "google_pubsub_subscription" "queues" {
  count = length(var.queues)
  name  = var.queues[count.index]
  topic = var.queues[count.index]
  project = var.gcp_pubsub_project
  retry_policy {
    minimum_backoff = "10s"
  }
  labels = var.labels
}

# Configure IAM for pubsub topics
resource "google_pubsub_topic_iam_member" "topics_iam" {
  count = length(var.queues)
  project = var.gcp_pubsub_project
  topic = var.queues[count.index]
  role = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# Configure IAM for pubsub subscriptions
resource "google_pubsub_subscription_iam_member" "subscriptions_iam" {
  count = length(var.queues)
  project = var.gcp_pubsub_project
  subscription = var.queues[count.index]
  role = "roles/pubsub.subscriber"
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# Configure subscriptions for OTP2 queues that are attached to OTP1 topics
resource "google_pubsub_subscription" "Otp2BaseGraphBuildQueue" {
  name = "Otp2BaseGraphBuildQueue"
  topic = "OtpBaseGraphBuildQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription_iam_member" "Otp2BaseGraphBuildQueue_iam" {
  project = var.gcp_pubsub_project
  subscription = "Otp2BaseGraphBuildQueue"
  role = "roles/pubsub.subscriber"
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

resource "google_pubsub_subscription" "Otp2GraphBuildQueue" {
  name = "Otp2GraphBuildQueue"
  topic = "OtpGraphBuildQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription_iam_member" "Otp2GraphBuildQueue_iam" {
  project = var.gcp_pubsub_project
  subscription = "Otp2GraphBuildQueue"
  role = "roles/pubsub.subscriber"
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# Configure IAM for the event notification topic (Nabu)
resource "google_pubsub_topic_iam_member" "pubsub_subscription_iam_member_subscriber_job_event_queue" {
  project = var.gcp_pubsub_project
  topic = "JobEventQueue"
  role = "roles/pubsub.publisher"
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}