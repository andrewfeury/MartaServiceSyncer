variable "api_sync_period_in_minutes" {
  description = "Number of minutes between Twitter sync calls"
  default = 15
  type = number
  validation {
    condition = var.api_sync_period_in_minutes >= 1
    error_message = "The shortest possible polling period is 1 minute"
  }
  validation {
    condition = var.api_sync_period_in_minutes <= 60 * 24
    error_message = "The longest possible polling period is 1 day (1440 minutes)"
  }
}