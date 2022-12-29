output "lambda_api_sync" {
  description = "Lambda function for MartaTweetSync"
  value       = aws_lambda_function.apisync.arn
}