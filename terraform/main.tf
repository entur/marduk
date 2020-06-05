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
  account_id = "${var.labels.team}-${var.labels.app}-sa"
  display_name = "${var.labels.team}-${var.labels.app} service account"
  project = var.gcp_project
}

# add service account as member to the cloudsql client
resource "google_project_iam_member" "cloudsql_iam_member" {
  project = var.gcp_project
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

# add service account as member to the otpreport bucket
resource "google_storage_bucket_iam_member" "storage_otpreport_bucket_iam_member" {
  bucket = var.bucket_otpreport_instance_name
  role = var.service_account_bucket_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to pubsub service in the resources project
resource "google_project_iam_member" "pubsub_project_iam_member" {
  project = var.gcp_pubsub_project
  role = var.service_account_pubsub_role
  member = "serviceAccount:${google_service_account.marduk_service_account.email}"
}

# add service account as member to pubsub service in the workload project
# TODO to be removed after cluster migration
resource "google_project_iam_member" "pubsub_iam_member" {
  project = var.gcp_project
  role = var.service_account_pubsub_role
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
    "credentials.json" = "${base64decode(google_service_account_key.marduk_service_account_key.private_key)}"
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

# Create pubsub topics and subscriptions
resource "google_pubsub_topic" "ChouetteExportGtfsQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteExportGtfsQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteExportGtfsQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteExportGtfsQueue"
  topic = google_pubsub_topic.ChouetteExportGtfsQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ChouetteExportNetexQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteExportNetexQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteExportNetexQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteExportNetexQueue"
  topic = google_pubsub_topic.ChouetteExportNetexQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ChouetteImportQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteImportQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteImportQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteImportQueue"
  topic = google_pubsub_topic.ChouetteImportQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ChouetteMergeWithFlexibleLinesQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteMergeWithFlexibleLinesQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteMergeWithFlexibleLinesQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteMergeWithFlexibleLinesQueue"
  topic = google_pubsub_topic.ChouetteMergeWithFlexibleLinesQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ChouettePollStatusQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouettePollStatusQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouettePollStatusQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouettePollStatusQueue"
  topic = google_pubsub_topic.ChouettePollStatusQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ChouetteTransferExportQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteTransferExportQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteTransferExportQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteTransferExportQueue"
  topic = google_pubsub_topic.ChouetteTransferExportQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ChouetteValidationQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteValidationQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteValidationQueue" {
  name = "${var.labels.team}.${var.labels.app}.ChouetteValidationQueue"
  topic = google_pubsub_topic.ChouetteValidationQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "GtfsBasicExportMergedQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsBasicExportMergedQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsBasicExportMergedQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsBasicExportMergedQueue"
  topic = google_pubsub_topic.GtfsBasicExportMergedQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "GtfsExportMergedQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsExportMergedQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsExportMergedQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsExportMergedQueue"
  topic = google_pubsub_topic.GtfsExportMergedQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "GtfsGoogleExportQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGoogleExportQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGoogleExportQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGoogleExportQueue"
  topic = google_pubsub_topic.GtfsGoogleExportQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "GtfsGooglePublishQaQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGooglePublishQaQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGooglePublishQaQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGooglePublishQaQueue"
  topic = google_pubsub_topic.GtfsGooglePublishQaQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "GtfsGooglePublishQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGooglePublishQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGooglePublishQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGooglePublishQueue"
  topic = google_pubsub_topic.GtfsGooglePublishQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "GtfsGoogleQaExportQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGoogleQaExportQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGoogleQaExportQueue" {
  name = "${var.labels.team}.${var.labels.app}.GtfsGoogleQaExportQueue"
  topic = google_pubsub_topic.GtfsGoogleQaExportQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "MardukInboundQueue" {
  name = "${var.labels.team}.${var.labels.app}.MardukInboundQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "MardukInboundQueue" {
  name = "${var.labels.team}.${var.labels.app}.MardukInboundQueue"
  topic = google_pubsub_topic.MardukInboundQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "NetexExportMergedQueue" {
  name = "${var.labels.team}.${var.labels.app}.NetexExportMergedQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "NetexExportMergedQueue" {
  name = "${var.labels.team}.${var.labels.app}.NetexExportMergedQueue"
  topic = google_pubsub_topic.NetexExportMergedQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "ProcessFileQueue" {
  name = "${var.labels.team}.${var.labels.app}.ProcessFileQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ProcessFileQueue" {
  name = "${var.labels.team}.${var.labels.app}.ProcessFileQueue"
  topic = google_pubsub_topic.ProcessFileQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "OtpBaseGraphBuildQueue" {
  name = "${var.labels.team}.${var.labels.app}.OtpBaseGraphBuildQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "OtpBaseGraphBuildQueue" {
  name = "${var.labels.team}.${var.labels.app}.OtpBaseGraphBuildQueue"
  topic = google_pubsub_topic.OtpBaseGraphBuildQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "Otp2BaseGraphBuildQueue" {
  name = "${var.labels.team}.${var.labels.app}.Otp2BaseGraphBuildQueue"
  topic = google_pubsub_topic.OtpBaseGraphBuildQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_topic" "OtpGraphBuildQueue" {
  name = "${var.labels.team}.${var.labels.app}.OtpGraphBuildQueue"
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "OtpGraphBuildQueue" {
  name = "${var.labels.team}.${var.labels.app}.OtpGraphBuildQueue"
  topic = google_pubsub_topic.OtpGraphBuildQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}

resource "google_pubsub_subscription" "Otp2GraphBuildQueue" {
  name = "${var.labels.team}.${var.labels.app}.Otp2GraphBuildQueue"
  topic = google_pubsub_topic.OtpGraphBuildQueue.name
  project = var.gcp_pubsub_project
  labels = var.labels
}
