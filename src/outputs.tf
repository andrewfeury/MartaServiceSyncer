output "lambda_api_sync" {
  description = "Lambda function for MartaTweetSync"
  value       = aws_lambda_function.apisync.arn
}

output "lambda_query" {
  description = "Lambda function for MartaTweetQuery"
  value       = aws_lambda_function.query.arn
}

output "trigger_sync_rule" {
  description = "EventBridge trigger to run MartaTweetSync"
  value       = aws_cloudwatch_event_rule.trigger_api_sync.arn
}