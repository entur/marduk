# Create pubsub topics and subscriptions
resource "google_pubsub_topic" "ChouetteExportGtfsQueue" {
  name = "ChouetteExportGtfsQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteExportGtfsQueue" {
  name = "ChouetteExportGtfsQueue"
  topic = google_pubsub_topic.ChouetteExportGtfsQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "DamuExportGtfsDeadLetterQueue" {
  name = "DamuExportGtfsDeadLetterQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "DamuExportGtfsDeadLetterQueue" {
  name = "DamuExportGtfsDeadLetterQueue"
  topic = google_pubsub_topic.DamuExportGtfsDeadLetterQueue.name
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_topic" "DamuExportGtfsQueue" {
  name = "DamuExportGtfsQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "DamuExportGtfsQueue" {
  name = "DamuExportGtfsQueue"
  topic = google_pubsub_topic.DamuExportGtfsQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  dead_letter_policy {
    max_delivery_attempts = 5
    dead_letter_topic = google_pubsub_topic.DamuExportGtfsDeadLetterQueue.id
  }
  ack_deadline_seconds = 600
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "DamuExportGtfsStatusQueue" {
  name = "DamuExportGtfsStatusQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "DamuExportGtfsStatusQueue" {
  name = "DamuExportGtfsStatusQueue"
  topic = google_pubsub_topic.DamuExportGtfsStatusQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 600
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ChouetteExportNetexQueue" {
  name = "ChouetteExportNetexQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteExportNetexQueue" {
  name = "ChouetteExportNetexQueue"
  topic = google_pubsub_topic.ChouetteExportNetexQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ChouetteExportNetexBlocksQueue" {
  name = "ChouetteExportNetexBlocksQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteExportNetexBlocksQueue" {
  name = "ChouetteExportNetexBlocksQueue"
  topic = google_pubsub_topic.ChouetteExportNetexBlocksQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ChouetteImportQueue" {
  name = "ChouetteImportQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteImportQueue" {
  name = "ChouetteImportQueue"
  topic = google_pubsub_topic.ChouetteImportQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "FlexibleLinesExportQueue" {
  name = "FlexibleLinesExportQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "FlexibleLinesExportQueue" {
  name = "FlexibleLinesExportQueue"
  topic = google_pubsub_topic.FlexibleLinesExportQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "PublishMergedNetexQueue" {
  name = "PublishMergedNetexQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "PublishMergedNetexQueue" {
  name = "PublishMergedNetexQueue"
  topic = google_pubsub_topic.PublishMergedNetexQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  retry_policy {
    minimum_backoff = "10s"
  }
}


resource "google_pubsub_topic" "ChouetteMergeWithFlexibleLinesQueue" {
  name = "ChouetteMergeWithFlexibleLinesQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteMergeWithFlexibleLinesQueue" {
  name = "ChouetteMergeWithFlexibleLinesQueue"
  topic = google_pubsub_topic.ChouetteMergeWithFlexibleLinesQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ChouettePollStatusQueue" {
  name = "ChouettePollStatusQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouettePollStatusQueue" {
  name = "ChouettePollStatusQueue"
  topic = google_pubsub_topic.ChouettePollStatusQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ChouetteTransferExportQueue" {
  name = "ChouetteTransferExportQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteTransferExportQueue" {
  name = "ChouetteTransferExportQueue"
  topic = google_pubsub_topic.ChouetteTransferExportQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ChouetteValidationQueue" {
  name = "ChouetteValidationQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ChouetteValidationQueue" {
  name = "ChouetteValidationQueue"
  topic = google_pubsub_topic.ChouetteValidationQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "GtfsBasicExportMergedQueue" {
  name = "GtfsBasicExportMergedQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsBasicExportMergedQueue" {
  name = "GtfsBasicExportMergedQueue"
  topic = google_pubsub_topic.GtfsBasicExportMergedQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "GtfsExportMergedQueue" {
  name = "GtfsExportMergedQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsExportMergedQueue" {
  name = "GtfsExportMergedQueue"
  topic = google_pubsub_topic.GtfsExportMergedQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "GtfsGoogleExportQueue" {
  name = "GtfsGoogleExportQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGoogleExportQueue" {
  name = "GtfsGoogleExportQueue"
  topic = google_pubsub_topic.GtfsGoogleExportQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "GtfsGooglePublishQaQueue" {
  name = "GtfsGooglePublishQaQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGooglePublishQaQueue" {
  name = "GtfsGooglePublishQaQueue"
  topic = google_pubsub_topic.GtfsGooglePublishQaQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "GtfsGooglePublishQueue" {
  name = "GtfsGooglePublishQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGooglePublishQueue" {
  name = "GtfsGooglePublishQueue"
  topic = google_pubsub_topic.GtfsGooglePublishQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "GtfsGoogleQaExportQueue" {
  name = "GtfsGoogleQaExportQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsGoogleQaExportQueue" {
  name = "GtfsGoogleQaExportQueue"
  topic = google_pubsub_topic.GtfsGoogleQaExportQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "MardukInboundQueue" {
  name = "MardukInboundQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "MardukInboundQueue" {
  name = "MardukInboundQueue"
  topic = google_pubsub_topic.MardukInboundQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "NetexExportMergedQueue" {
  name = "NetexExportMergedQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "NetexExportMergedQueue" {
  name = "NetexExportMergedQueue"
  topic = google_pubsub_topic.NetexExportMergedQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "ProcessFileQueue" {
  name = "ProcessFileQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "ProcessFileQueue" {
  name = "ProcessFileQueue"
  topic = google_pubsub_topic.ProcessFileQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "NetexExportNotificationQueue" {
  name = "NetexExportNotificationQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "NetexExportNotificationQueue" {
  name = "NetexExportNotificationQueue"
  topic = google_pubsub_topic.NetexExportNotificationQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}


resource "google_pubsub_subscription" "AntuNetexValidationStatusQueue" {
  name = "AntuNetexValidationStatusQueue"
  topic = var.antu_netex_validation_status_queue_topic
  filter = "attributes.EnturValidationClient = \"Marduk\""
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}


resource "google_pubsub_topic" "OtpBaseGraphBuildQueue" {
  name = "OtpBaseGraphBuildQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "OtpBaseGraphBuildQueue" {
  name = "OtpBaseGraphBuildQueue"
  topic = google_pubsub_topic.OtpBaseGraphBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_subscription" "Otp2BaseGraphBuildQueue" {
  name = "Otp2BaseGraphBuildQueue"
  topic = google_pubsub_topic.OtpBaseGraphBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "OtpGraphBuildQueue" {
  name = "OtpGraphBuildQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "OtpGraphBuildQueue" {
  name = "OtpGraphBuildQueue"
  topic = google_pubsub_topic.OtpGraphBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_subscription" "Otp2GraphBuildQueue" {
  name = "Otp2GraphBuildQueue"
  topic = google_pubsub_topic.OtpGraphBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "Otp2GraphCandidateBuildQueue" {
  name = "Otp2GraphCandidateBuildQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "Otp2GraphCandidateBuildQueue" {
  name = "Otp2GraphCandidateBuildQueue"
  topic = google_pubsub_topic.Otp2GraphCandidateBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "Otp2BaseGraphCandidateBuildQueue" {
  name = "Otp2BaseGraphCandidateBuildQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "Otp2BaseGraphCandidateBuildQueue" {
  name = "Otp2BaseGraphCandidateBuildQueue"
  topic = google_pubsub_topic.Otp2BaseGraphCandidateBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}
