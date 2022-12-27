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