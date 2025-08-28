resource "google_storage_bucket_iam_member" "marduk_exchange_storage_iam_member" {
  bucket = var.marduk_exchange_storage_bucket
  role = var.service_account_bucket_role
  member = var.ashur_service_account
}
