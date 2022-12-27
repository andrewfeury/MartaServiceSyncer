terraform {
  backend "s3" {
    # configure backend locally using file or command line
    # see: https://developer.hashicorp.com/terraform/language/settings/backends/configuration#partial-configuration
  }
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 4.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
  default_tags {
    tags = {
      ManagedBy = "https://github.com/andrewfeury/MartaServiceSyncer"
    }
  }
}

## Parameters for Twitter API
resource "aws_ssm_parameter" "twitter_key" {
  name           = "/MartaServiceSyncer/TwitterAPI/KeyID"
  type           = "String"
  overwrite      = false
  tier           = "Standard"
  insecure_value = "UPDATE_ME"
  lifecycle {
    ignore_changes = [
      value, insecure_value
    ]
  }
}

resource "aws_ssm_parameter" "twitter_secret" {
  name           = "/MartaServiceSyncer/TwitterAPI/KeySecret"
  type           = "String"
  overwrite      = false
  tier           = "Standard"
  insecure_value = "UPDATE_ME"
  lifecycle {
    ignore_changes = [
      value, insecure_value
    ]
  }
}

resource "aws_ssm_parameter" "twitter_token" {
  name           = "/MartaServiceSyncer/TwitterAPI/BearerToken"
  type           = "String"
  overwrite      = false
  tier           = "Standard"
  insecure_value = "UPDATE_ME"
  lifecycle {
    ignore_changes = [
      value, insecure_value
    ]
  }
}

## DynamoDB table for tracking alerts
resource "aws_dynamodb_table" "alert_db" {
  name = "ActiveAlerts"
  billing_mode = "PROVISIONED"
  table_class = "STANDARD"
  read_capacity = 1
  write_capacity = 1
  hash_key = "Route"
  range_key = "Created"

  attribute {
    name = "Route"
    type = "S"
  }

  attribute {
    name = "Created"
    type = "N"
  }

  ttl {
    attribute_name = "Expires"
    enabled = true
  }
}