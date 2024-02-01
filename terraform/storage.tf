# Create bucket

resource "google_storage_bucket" "storage_bucket" {
  name                   = "${var.bucket_instance_prefix}-${var.bucket_instance_suffix}"
  force_destroy      = var.force_destroy
  location               = var.location
  project                 = var.gcp_resources_project
  storage_class      = var.storage_class
  labels                   = var.labels
  uniform_bucket_level_access = true
  versioning {
    enabled = true
  }
  lifecycle_rule {
      condition {
        matches_prefix = ["graphs/work", "chouette/netex-before-validation", "chouette/netex-with-blocks-before-validation"]
        age = 1
        with_state = "ANY"
      }
      action {
        type = "Delete"
      }
    }
  logging {
    log_bucket           = var.log_bucket
    log_object_prefix = "${var.bucket_instance_prefix}-${var.bucket_instance_suffix}"
  }
}