resource "kubernetes_secret" "ror-marduk-secret" {
  metadata {
    name = "${var.labels.team}-${var.labels.app}-secret"
    namespace = var.kube_namespace
  }

  data = {
    "SPRING_DATASOURCE_USERNAME" = var.ror-marduk-db-username
    "SPRING_DATASOURCE_PASSWORD" = var.ror-marduk-db-password
    "GOOGLE_PUBLISH_SFTP_USERNAME" = var.ror-marduk-google-sftp-username
    "GOOGLE_PUBLISH_SFTP_PASSWORD" = var.ror-marduk-google-sftp-password
    "GOOGLE_PUBLISH_QA_SFTP_USERNAME" = var.ror-marduk-google-qa-sftp-username
    "GOOGLE_PUBLISH_QA_SFTP_PASSWORD" = var.ror-marduk-google-qa-sftp-password
    "SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_MARDUK_CLIENT_SECRET" = var.ror-marduk-auth0-secret
  }
}