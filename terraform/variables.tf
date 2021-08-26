#Enviroment variables
variable "gcp_project" {
  description = "The GCP project hosting the workloads"
}

variable "gcp_resources_project" {
  description = "The GCP project hosting the project resources"
}

variable "kube_namespace" {
  description = "The Kubernetes namespace"
}

variable "labels" {
  description = "Labels used in all resources"
  type = map(string)
  default = {
    manager = "terraform"
    team = "ror"
    slack = "talk-ror"
    app = "marduk"
  }
}

variable "load_config_file" {
  description = "Do not load kube config file"
  default = false
}

variable "service_account_cloudsql_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/pubsub/docs/access-control"
  default = "roles/cloudsql.client"
}

variable "service_account_bucket_role" {
  description = "Role of the Service Account - more about roles https://cloud.google.com/storage/docs/access-control/iam-roles"
  default = "roles/storage.objectViewer"
}

variable "bucket_marduk_instance_name" {
  description = "Main storage bucket name"
}

variable "bucket_exchange_instance_name" {
  description = "Exchange storage bucket name"
}

variable "bucket_graphs_instance_name" {
  description = "OTP Graphs storage bucket name"
}

variable "bucket_otpreport_instance_name" {
  description = "OTP report storage bucket name"
}

variable "bucket_nisaba_exchange_instance_name" {
  description = "Nisaba Exchange storage bucket name"
}

variable "ror-marduk-db-username" {
  description = "marduk database username"
}

variable "ror-marduk-db-password" {
  description = "marduk database password"
}

variable "ror-marduk-google-sftp-username" {
  description = "marduk Google SFTP username"
}

variable "ror-marduk-google-sftp-password" {
  description = "marduk Google SFTP password"
}

variable "ror-marduk-google-qa-sftp-username" {
  description = "marduk Google QA SFTP username"
}

variable "ror-marduk-google-qa-sftp-password" {
  description = "marduk Google QA SFTP password"
}

variable "ror-marduk-auth0-secret" {
  description = "marduk auth0 secret"
}

variable "db_region" {
  description = "GCP  region"
  default = "europe-west1"
}

variable "db_zone_letter" {
  description = "GCP zone letter"
  default = "b"
}

variable "db_tier" {
  description = "Database instance tier"
  default = "db-custom-1-3840"
}

variable "db_availability" {
  description = "Database availablity"
  default = "ZONAL"
}



