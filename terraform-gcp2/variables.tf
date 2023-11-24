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

variable "location" {
  description = "GCP bucket location"
  default = "europe-west1"
}

variable "bucket_instance_suffix" {
  description = "A suffix for the bucket instance, may be changed if environment is destroyed and then needed again (name collision workaround) - also bucket names must be globally unique"
}

variable "bucket_instance_prefix" {
  description = "A prefix for the bucket instance, may be changed if environment is destroyed and then needed again (name collision workaround) - also bucket names must be globally unique"
  default = "ror-marduk-internal"
}

variable "force_destroy" {
  description = "(Optional, Default: false) When deleting a bucket, this boolean option will delete all contained objects. If you try to delete a bucket that contains objects, Terraform will fail that run"
  default     = false
}

variable "storage_class" {
  description = "GCP storage class"
  default     = "STANDARD"
}

variable "versioning" {
  description = "The bucket's Versioning configuration."
  default     = "false"
}

variable "log_bucket" {
  description = "The bucket's Access & Storage Logs configuration"
  default     = "false"
}



