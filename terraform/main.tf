# Contains main description of bulk of terraform?
terraform {
  required_version = ">= 0.12"
}

provider "google" {
  version = "~> 2.19"
}
provider "kubernetes" {
  load_config_file = var.load_config_file
}

# create service account
resource "google_service_account" "marduk_service_account" {
  account_id   = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_project
}

# add service account as member to the cloudsql client
resource "google_project_iam_member" "cloudsql_iam_member" {
  project = var.gcp_project
  role    = var.service_account_cloudsql_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the main bucket
resource "google_storage_bucket_iam_member" "storage_main_bucket_iam_member" {
  bucket = var.bucket_marduk_instance_name
  role   = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the exchange bucket
resource "google_storage_bucket_iam_member" "storage_exchange_bucket_iam_member" {
  bucket = var.bucket_exchange_instance_name
  role   = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to the otpreport bucket
resource "google_storage_bucket_iam_member" "storage_otpreport_bucket_iam_member" {
  bucket = var.bucket_otpreport_instance_name
  role   = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to pubsub service in the resources project
resource "google_project_iam_member" "pubsub_project_iam_member" {
  project = var.gcp_pubsub_project
  role    = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to pubsub service in the workload project
# TODO to be removed after cluster migration
resource "google_project_iam_member" "pubsub_iam_member" {
  project = var.gcp_project
  role    = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# create key for service account
resource "google_service_account_key" "marduk_service_account_key" {
  service_account_id = google_service_account.marduk_service_account.name
}

  # Add SA key to to k8s
resource "kubernetes_secret" "marduk_service_account_credentials" {
  metadata {
    name      = "${var.labels.team}-${var.labels.app}-sa-key"
    namespace = var.kube_namespace
  }
  data = {
    "credentials.json" = "${base64decode(google_service_account_key.marduk_service_account_key.private_key)}"
  }
}

resource "kubernetes_secret" "ror-marduk-secret" {
  metadata {
    name      = "${var.labels.team}-${var.labels.app}-secret"
    namespace = var.kube_namespace
  }

  data = {
    "marduk-db-username"     = var.ror-marduk-db-username
    "marduk-db-password"     = var.ror-marduk-db-password
    "marduk-google-sftp-username"     = var.ror-marduk-google-sftp-username
    "marduk-google-sftp-password"     = var.ror-marduk-google-sftp-password
    "marduk-google-qa-sftp-username"     = var.ror-marduk-google-qa-sftp-username
    "marduk-google-qa-sftp-password"     = var.ror-marduk-google-qa-sftp-password
    "marduk-keycloak-secret"     = var.ror-marduk-keycloak-secret
  }
}
