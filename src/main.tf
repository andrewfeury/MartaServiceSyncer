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

## Locals
locals {
  apisync_name = "MartaTweetSync"
  parameter_path = "/MartaServiceSyncer/TwitterAPI"
}

## Data
data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

## Parameters for Twitter API
resource "aws_ssm_parameter" "twitter_token" {
  name           = "${local.parameter_path}/BearerToken"
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

## MartaTweetSync
resource "aws_lambda_function" "apisync" {
  function_name    = local.apisync_name
  role             = aws_iam_role.for_apisync.arn
  description      = "Monitors the @MartaService Twitter account for service alerts"
  runtime          = "java11"
  architectures    = ["x86_64"]
  filename         = "${path.module}/functions/syncbusalerts/target/syncbusalerts.jar"
  source_code_hash = filebase64sha256("${path.module}/functions/syncbusalerts/target/syncbusalerts.jar")
  handler          = "us.feury.martasync.MartaSyncFunction"
  timeout          = 15
  memory_size      = 512

  depends_on = [
    null_resource.build_apisync,
    aws_cloudwatch_log_group.for_apisync,
    aws_iam_role_policy_attachment.for_apisync
  ]
}

resource "null_resource" "build_apisync" {
  provisioner "local-exec" {
    command = "mvn package -f ${path.module}/functions/syncbusalerts/pom.xml"
  }
}

resource "aws_cloudwatch_log_group" "for_apisync" {
  name              = "/aws/lambda/${local.apisync_name}"
  retention_in_days = 14
}

resource "aws_iam_role" "for_apisync" {
  name = "lambda_role_${local.apisync_name}"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action    = "sts:AssumeRole"
        Effect    = "Allow"
        Principal = {
          Service: "lambda.amazonaws.com"
        }
      }
    ]
  })
}

resource "aws_iam_policy" "for_apisync" {
  name = "lambda_policy_${local.apisync_name}"
  path = "/"
  description = "IAM policy for the ${local.apisync_name} lambda"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "WriteCloudwatchLogs"
        Action   = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Effect   = "Allow"
        Resource = [
          "${aws_cloudwatch_log_group.for_apisync.arn}",
          "${aws_cloudwatch_log_group.for_apisync.arn}:log-stream:*"
        ]
      },
      {
        Sid      = "ReadWriteDynamoDB"
        Action   = [
            "dynamodb:DescribeTable",
            "dynamodb:PutItem",
            "dynamodb:UpdateItem",
            "dynamodb:DeleteItem",
            "dynamodb:BatchWriteItem",
            "dynamodb:GetItem",
            "dynamodb:BatchGetItem",
            "dynamodb:Scan",
            "dynamodb:Query",
            "dynamodb:ConditionCheckItem"
        ]
        Effect   = "Allow"
        Resource = [
          "${aws_dynamodb_table.alert_db.arn}",
          "${aws_dynamodb_table.alert_db.arn}/index/*"
        ]
      },
      {
        Sid      = "ReadWriteParameterStore"
        Action   = [
          "ssm:GetParameter*",
          "ssm:PutParameter"
        ]
        Effect   = "Allow"
        Resource = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${local.parameter_path}/*"
      }
    ]
  })
}
resource "aws_iam_role_policy_attachment" "for_apisync" {
  role       = aws_iam_role.for_apisync.name
  policy_arn = aws_iam_policy.for_apisync.arn
}

resource "aws_cloudwatch_event_rule" "trigger_api_sync" {
  name                = "trigger_${local.apisync_name}"
  description         = "Recurring timer to trigger a Marta tweet sync"
  schedule_expression = "rate(${var.api_sync_period_in_minutes} minutes)"
}

resource "aws_cloudwatch_event_target" "trigger_api_sync" {
  rule = aws_cloudwatch_event_rule.trigger_api_sync.name
  arn = aws_lambda_function.apisync.arn
}

resource "aws_lambda_permission" "allow_scheduled_event" {
  statement_id  = "Execute${local.apisync_name}OnSchedule"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.apisync.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.trigger_api_sync.arn
}