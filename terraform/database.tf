resource "google_sql_database_instance" "db_instance" {
  name             = "marduk-db-pg13"
  database_version = "POSTGRES_17"
  project          = var.gcp_resources_project
  region           = var.db_region

  settings {
    location_preference {
      zone = var.db_zone
    }
    tier              = var.db_tier
    user_labels       = var.labels
    availability_type = var.db_availability
    backup_configuration {
      enabled = true
      // 01:00 UTC
      start_time = "01:00"
    }
    maintenance_window {
      // Sunday
      day = 7
      // 02:00 UTC
      hour = 2
    }
    ip_configuration {
      ssl_mode = "TRUSTED_CLIENT_CERTIFICATE_REQUIRED"
    }
  }
}

resource "google_sql_database" "db" {
  name     = "marduk"
  project  = var.gcp_resources_project
  instance = google_sql_database_instance.db_instance.name
}

data "google_secret_manager_secret_version" "db_password" {
  secret  = "SPRING_DATASOURCE_PASSWORD"
  project = var.gcp_resources_project
}

data "google_secret_manager_secret_version" "db_username" {
  secret  = "SPRING_DATASOURCE_USERNAME"
  project = var.gcp_resources_project
}

resource "google_sql_user" "db-user" {
  project  = var.gcp_resources_project
  instance = google_sql_database_instance.db_instance.name
  name     = data.google_secret_manager_secret_version.db_username.secret_data
  password = data.google_secret_manager_secret_version.db_password.secret_data
}
