# Create pubsub topics and subscriptions
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
  expiration_policy {
        ttl = ""
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


resource "google_pubsub_topic" "Otp2BaseGraphBuildQueue" {
  name = "Otp2BaseGraphBuildQueue"
  project = var.gcp_resources_project
  labels = var.labels
}


resource "google_pubsub_subscription" "Otp2BaseGraphBuildQueue" {
  name = "Otp2BaseGraphBuildQueue"
  topic = google_pubsub_topic.Otp2BaseGraphBuildQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  ack_deadline_seconds = 60
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "Otp2GraphBuildQueue" {
  name = "Otp2GraphBuildQueue"
  project = var.gcp_resources_project
  labels = var.labels
}


resource "google_pubsub_subscription" "Otp2GraphBuildQueue" {
  name = "Otp2GraphBuildQueue"
  topic = google_pubsub_topic.Otp2GraphBuildQueue.name
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

resource "google_pubsub_topic" "MardukDeadLetterQueue" {
  name = "MardukDeadLetterQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "MardukDeadLetterQueue" {
  name = "MardukDeadLetterQueue"
  topic = google_pubsub_topic.MardukDeadLetterQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  expiration_policy {
        ttl = ""
      }
}

resource "google_pubsub_topic" "GtfsRouteDispatcherDeadLetterQueue" {
  name = "GtfsRouteDispatcherDeadLetterQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsRouteDispatcherDeadLetterQueue" {
  name = "GtfsRouteDispatcherDeadLetterQueue"
  topic = google_pubsub_topic.GtfsRouteDispatcherDeadLetterQueue.name
  project = var.gcp_resources_project
  labels = var.labels
  expiration_policy {
        ttl = ""
      }
}

resource "google_pubsub_topic" "GtfsRouteDispatcherTopic" {
  name = "GtfsRouteDispatcherTopic"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "GtfsRouteDispatcherTopic" {
  name = "GtfsRouteDispatcherTopic"
  topic = google_pubsub_topic.GtfsRouteDispatcherTopic.name
  project = var.gcp_resources_project
  labels = var.labels
  dead_letter_policy {
    max_delivery_attempts = 5
    dead_letter_topic = google_pubsub_topic.GtfsRouteDispatcherDeadLetterQueue.id
  }
  ack_deadline_seconds = 600
  retry_policy {
    minimum_backoff = "10s"
  }
}

resource "google_pubsub_topic" "MardukAggregateGtfsStatusQueue" {
  name = "MardukAggregateGtfsStatusQueue"
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_subscription" "MardukAggregateGtfsStatusQueue" {
  name = "MardukAggregateGtfsStatusQueue"
  topic = google_pubsub_topic.MardukAggregateGtfsStatusQueue.name
  project = var.gcp_resources_project
  labels = var.labels
}

resource "google_pubsub_topic" "LineStatisticsCalculationQueue" {
  name = "LineStatisticsCalculationQueue"
  project = var.gcp_resources_project
  labels = var.labels
}
