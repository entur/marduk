#Enviroment variables
variable "gcp_resources_project" {
  description = "The GCP project hosting the project resources"
}

variable "kube_namespace" {
  description = "The Kubernetes namespace"
  default = "marduk"
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

variable "db_zone" {
  description = "GCP zone"
  default = "europe-west1-b"
}

variable "db_tier" {
  description = "Database instance tier"
  default = "db-custom-1-3840"
}

variable "db_availability" {
  description = "Database availability"
  default = "ZONAL"
}

variable "antu_netex_validation_status_queue_topic" {
  description = "PubSub topic receiving NeTEx validation request status"
}



