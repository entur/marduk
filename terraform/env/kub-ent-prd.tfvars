gcp_resources_project = "ent-marduk-prd"
db_availability = "REGIONAL"
antu_netex_validation_status_queue_topic = "projects/ent-antu-prd/topics/AntuNetexValidationStatusQueue"
bucket_instance_suffix="production"
ashur_service_account="serviceAccount:application@ent-ashur-prd.iam.gserviceaccount.com"
marduk_exchange_storage_bucket="marduk-exchange-production"
servicelinker_service_account="serviceAccount:application@ent-servicelnk-prd.iam.gserviceaccount.com"

labels = {
  manager     = "terraform"
  team        = "ror"
  slack       = "talk-ror"
  app         = "marduk"
  environment = "prd"
}